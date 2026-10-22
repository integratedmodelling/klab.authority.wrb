package org.integratedmodelling.klab.authority.wrb;

import java.util.*;
import java.util.stream.Collectors;
import static org.integratedmodelling.klab.authority.wrb.WrbVocabulary.*;

/** Parser and canonical representation of soil names, not a pedon diagnostic engine. */
public final class WrbExpression {
  private static final WrbVocabulary VOCABULARY = new WrbVocabulary();
  private record Soil(Group group, List<List<Qualifier>> sections) {
    Soil { sections = sections.stream().map(List::copyOf).toList(); }
    List<Qualifier> qualifiers() { return sections.stream().flatMap(Collection::stream).toList(); }
    String code() {
      // Chapter 5 explicitly provides no compact codes for destroyed-horizon additions.
      if (qualifiers().stream().anyMatch(q -> !q.addition().isEmpty() && q.root().equals("Relocatic"))) return label();
      var fields = new ArrayList<String>(); fields.add(group.code());
      for (var section : sections) fields.add(section.stream().map(Qualifier::code).collect(Collectors.joining(".")));
      while (fields.getLast().isEmpty()) fields.removeLast();
      return String.join("-", fields);
    }
    String label() {
      var principal = new ArrayList<>(sections.getFirst()); Collections.reverse(principal);
      String prefix = principal.stream().map(Qualifier::name).collect(Collectors.joining(" "));
      String suffix = sections.subList(1, 4).stream().flatMap(Collection::stream).map(Qualifier::name).collect(Collectors.joining(", "));
      return (prefix.isEmpty() ? "" : prefix + " ") + group.name() + (suffix.isEmpty() ? "" : " (" + suffix + ")");
    }
  }
  private final List<Soil> soils;
  private WrbExpression(List<Soil> soils) { this.soils = List.copyOf(soils); }
  public String code() { return soils.stream().map(Soil::code).collect(Collectors.joining(" over ")); }
  public String label() { return soils.stream().map(Soil::label).collect(Collectors.joining(" over ")); }
  public String groupCode() { return soils.getFirst().group().code(); }
  public boolean isGeneric() { return soils.size() == 1 && soils.getFirst().qualifiers().isEmpty(); }
  public int qualifierCount() { return soils.stream().mapToInt(s -> s.qualifiers().size()).sum() + soils.size() - 1; }
  static Collection<Group> groups() { return VOCABULARY.groups.values(); }

  public static WrbExpression parse(String expression) {
    if (expression == null || expression.isBlank() || expression.length() > 8192)
      throw new IllegalArgumentException("A WRB soil expression of 1..8192 characters is required");
    String[] layers = expression.strip().split("(?i)\\s+over\\s+", -1);
    if (layers.length > 8) throw new IllegalArgumentException("At most eight buried soils are supported");
    var soils = Arrays.stream(layers).map(WrbExpression::parseSoil).toList();
    for (int i = 1; i < soils.size(); i++) {
      if (Set.of("RG", "LP").contains(soils.get(i).group().code()))
        throw new IllegalArgumentException("A buried soil named after 'over' must not be a Regosol or Leptosol (2.4)");
    }
    return new WrbExpression(soils);
  }

  private record Input(Qualifier qualifier, int section) {}
  private static Soil parseSoil(String expression) {
    String text = expression.strip();
    var entries = new ArrayList<Input>();
    Group group;
    // Codes keep the four section delimiters, including empty intervening sections.
    int dash = text.indexOf('-');
    if (dash >= 0 && VOCABULARY.group(text.substring(0, dash)) != null) {
      String[] fields = text.split("-", -1);
      if (fields.length > 5 || fields[fields.length - 1].isBlank())
        throw new IllegalArgumentException("Invalid WRB code sections");
      group = VOCABULARY.group(fields[0]);
      for (int i = 1; i < fields.length; i++) if (!fields[i].isEmpty())
        for (String token : fields[i].split("\\.", -1)) entries.add(new Input(VOCABULARY.qualifier(token), i - 1));
    } else {
      String principal = text;
      String supplementary = null;
      int opening = text.indexOf('(');
      if (opening >= 0) {
        if (!text.endsWith(")") || text.indexOf('(', opening + 1) >= 0 || text.indexOf(')') != text.length() - 1)
          throw new IllegalArgumentException("Malformed supplementary qualifier list");
        principal = text.substring(0, opening).strip();
        supplementary = text.substring(opening + 1, text.length() - 1);
        if (supplementary.isBlank()) throw new IllegalArgumentException("Empty supplementary qualifier list");
      } else if (text.contains(")")) throw new IllegalArgumentException("Unmatched parenthesis");
      String[] tokens = principal.split("\\s+");
      group = VOCABULARY.group(tokens[tokens.length - 1]);
      if (group == null) throw new IllegalArgumentException("Expected a WRB Reference Soil Group");
      for (int i = 0; i < tokens.length - 1; i++) entries.add(new Input(VOCABULARY.qualifier(tokens[i]), 0));
      if (supplementary != null) for (String token : supplementary.split(",", -1))
        entries.add(new Input(VOCABULARY.qualifier(token.strip()), -1));
    }
    if (entries.size() > 128) throw new IllegalArgumentException("Too many qualifiers");
    var sections = new ArrayList<List<Qualifier>>();
    for (int i = 0; i < 4; i++) sections.add(new ArrayList<>());
    var seen = new HashSet<String>();
    for (var input : entries) {
      Qualifier q = input.qualifier();
      if (!q.definition().groups().isEmpty() && !q.definition().groups().contains(group.code()))
        throw new IllegalArgumentException(q.name() + " is restricted to " + q.definition().groups());
      int section = section(group, q);
      if (input.section() >= 0 && input.section() != section || input.section() == -1 && section == 0)
        throw new IllegalArgumentException(q.name() + " belongs in " + List.of("principal qualifiers", "textural qualifiers", "supplementary qualifiers", "non-listed qualifiers").get(section));
      if (!seen.add(q.code())) throw new IllegalArgumentException("Duplicate qualifier " + q.name());
      sections.get(section).add(q);
    }
    validateCombinations(group, entries.stream().map(Input::qualifier).toList(), sections.getFirst());
    // Uppermost principal qualifier is closest to the RSG in a name, first in a code.
    sections.getFirst().sort(Comparator.comparingInt((Qualifier q) -> rank(group.principal(), q))
        .thenComparingInt(q -> q.definition().depthRule() == 3 && !q.prefixes().isEmpty() ? 1 : 0)
        .thenComparingInt(Qualifier::depthOrder).thenComparing(Qualifier::name));
    sections.get(1).sort(Comparator.comparingInt(Qualifier::depthOrder).thenComparing(Qualifier::name));
    Comparator<Qualifier> alphabet = Comparator.comparing(WrbExpression::sortName).thenComparingInt(Qualifier::depthOrder).thenComparing(Qualifier::name);
    sections.get(2).sort(alphabet); sections.get(3).sort(alphabet);
    return new Soil(group, sections);
  }

  private static String sortName(Qualifier q) { return q.root(); }
  private static int rank(List<List<String>> slots, Qualifier q) {
    for (int i = 0; i < slots.size(); i++) if (slots.get(i).contains(q.name())) return i;
    for (int i = 0; i < slots.size(); i++) if (slots.get(i).contains(q.definition().name()) || slots.get(i).contains(q.root())) return i;
    return -1;
  }
  private static int section(Group group, Qualifier q) {
    int principal = rank(group.principal(), q), supplementary = rank(group.supplementary(), q);
    if (principal >= 0 && !q.displaced()) return 0;
    if (supplementary >= 0 || principal >= 0 && q.displaced()) return TEXTURES.contains(q.root()) ? 1 : 2;
    // Bathy and Thapto versions of listed qualifiers retain listed supplementary placement.
    return 3;
  }
  private static void validateCombinations(Group group, List<Qualifier> qualifiers, List<Qualifier> principal) {
    if (qualifiers.stream().anyMatch(q -> q.root().equals("Dystric") && q.prefixes().isEmpty())
        && qualifiers.stream().anyMatch(q -> q.root().equals("Eutric") && q.prefixes().isEmpty()))
      throw new IllegalArgumentException("Unmodified Dystric and Eutric are mutually exclusive");
    if (principal.stream().anyMatch(q -> q.definition().name().equals("Haplic")) && principal.size() > 1)
      throw new IllegalArgumentException("Haplic excludes every other principal qualifier");
    for (var slots : List.of(group.principal(), group.supplementary())) for (var alternatives : slots) {
      if (alternatives.size() < 2) continue;
      var selected = qualifiers.stream().filter(q -> alternatives.contains(q.name()) || alternatives.contains(q.definition().name()) || alternatives.contains(q.root())).toList();
      for (int i = 0; i < selected.size(); i++) for (int j = i + 1; j < selected.size(); j++) {
        var a = selected.get(i); var b = selected.get(j);
        if (a.root().equals(b.root())) continue;
        // Different texture/depth variants can occur in separate layers. The pedon must prove that.
        if (a.prefixes().isEmpty() && b.prefixes().isEmpty()
            || !a.prefixes().isEmpty() && a.prefixes().equals(b.prefixes()))
          throw new IllegalArgumentException("Conflicting or redundant alternatives: " + a.name() + ", " + b.name());
      }
    }
    for (var a : qualifiers) for (var b : qualifiers) if (a != b && a.definition().equals(b.definition())
        && a.addition().equals(b.addition()) && (a.prefixes().isEmpty() || a.prefixes().equals(b.prefixes())))
      throw new IllegalArgumentException("Redundant qualifier: " + a.name());
    boolean calcareous = qualifiers.stream().anyMatch(q -> Set.of("Calcaric", "Dolomitic").contains(q.root()) && q.prefixes().isEmpty());
    if (calcareous && qualifiers.stream().anyMatch(q -> q.root().equals("Eutric") && q.prefixes().isEmpty()))
      throw new IllegalArgumentException("Eutric is redundant with Calcaric or Dolomitic");
    // Rule 3 additional depth variants must be accompanied by the predominant opposite qualifier.
    for (var q : qualifiers) if (q.definition().depthRule() == 3 && !q.prefixes().isEmpty()) {
      String opposite = q.root().equals("Dystric") ? "Eutric" : "Dystric";
      if (qualifiers.stream().noneMatch(p -> p.root().equals(opposite) && p.prefixes().isEmpty()))
        throw new IllegalArgumentException(q.name() + " requires the predominant " + opposite + " qualifier");
    }
  }

  /** Sound, deliberately conservative inclusion; absence of a qualifier is not its negation. */
  public boolean includes(WrbExpression candidate) {
    if (soils.size() != candidate.soils.size()) return isGeneric() && groupCode().equals(candidate.groupCode());
    for (int i = 0; i < soils.size(); i++) {
      Soil target = soils.get(i), source = candidate.soils.get(i);
      if (!target.group().equals(source.group())) return false;
      if (!target.qualifiers().stream().allMatch(t -> source.qualifiers().stream().anyMatch(q -> q.proves(t)))) return false;
    }
    return true;
  }
}
