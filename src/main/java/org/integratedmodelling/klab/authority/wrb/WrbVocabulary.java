package org.integratedmodelling.klab.authority.wrb;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Factual vocabulary pinned to WRB 2022, corrections 2024-09-24. */
final class WrbVocabulary {
  record Definition(String code, String name, String parent, int depthRule, Set<String> depthPrefixes,
      Set<String> groups, boolean layerDiagnostic) {}
  record Group(String code, String name, List<List<String>> principal, List<List<String>> supplementary) {}
  static final Map<String, String> PREFIXES = Map.of("Epi", "p", "Endo", "n", "Amphi", "m",
      "Ano", "a", "Kato", "k", "Panto", "e", "Poly", "y", "Bathy", "d", "Supra", "s", "Thapto", "b");
  static final Set<String> TEXTURES = Set.of("Arenic", "Clayic", "Loamic", "Siltic");
  final Map<String, Definition> definitions = new LinkedHashMap<>();
  final Map<String, Group> groups = new LinkedHashMap<>();
  private final Map<String, Definition> qualifierAliases = new HashMap<>();
  private final Map<String, Group> groupAliases = new HashMap<>();

  WrbVocabulary() {
    for (String[] row : read("qualifiers.tsv")) {
      var prefixes = row[4].isEmpty() ? Set.<String>of() : Set.of(row[4].split(","));
      var q = new Definition(row[0], row[1], row[2], row[3].isEmpty() ? 0 : Integer.parseInt(row[3]), prefixes,
          row[5].isEmpty() ? Set.of() : Set.of(row[5].split(",")), Boolean.parseBoolean(row[6]));
      if (definitions.put(q.name(), q) != null) throw new IllegalStateException("Duplicate qualifier " + q.name());
      qualifierAliases.put(q.name().toLowerCase(Locale.ROOT), q);
      qualifierAliases.put(q.code(), q);
    }
    for (String[] row : read("groups.tsv")) {
      var g = new Group(row[0], row[1], slots(row[2]), slots(row[3]));
      groups.put(g.code(), g);
      groupAliases.put(g.code().toLowerCase(Locale.ROOT), g);
      groupAliases.put(g.name().toLowerCase(Locale.ROOT), g);
      groupAliases.put((g.name() + (g.name().endsWith("z") ? "" : "s")).toLowerCase(Locale.ROOT), g);
    }
    if (groups.size() != 32 || definitions.size() < 200) throw new IllegalStateException("Incomplete WRB vocabulary");
  }

  Group group(String token) { return groupAliases.get(token.toLowerCase(Locale.ROOT)); }

  record Qualifier(Definition definition, List<String> prefixes, String addition) {
    Qualifier { prefixes = List.copyOf(prefixes); }
    String name() {
      String stem = definition.name();
      for (int i = prefixes.size() - 1; i >= 0; i--)
        stem = prefixes.get(i) + stem.toLowerCase(Locale.ROOT);
      return addition.isEmpty() ? stem : addition + "-" + stem;
    }
    String code() {
      String code = definition.code();
      // Specifier suffixes follow the qualifier outwards: Thaptobathyvertic = vrdb.
      for (int i = prefixes.size() - 1; i >= 0; i--) code += PREFIXES.get(prefixes.get(i));
      if (!addition.isEmpty()) {
        String suffix = Map.of("Aeoli", "a", "Fluvi", "f", "Solimovi", "s", "Techni", "t",
            "Tephri", "v", "Transporti", "p").get(addition);
        return suffix == null ? name() : code + suffix;
      }
      return code;
    }
    String root() { return definition.parent().isEmpty() ? definition.name() : definition.parent(); }
    boolean displaced() {
      return prefixes.contains("Bathy") || prefixes.contains("Thapto")
          || definition.name().startsWith("Proto") && !definition.parent().isEmpty();
    }
    int depthOrder() {
      for (String prefix : prefixes) {
        int rank = List.of("Supra", "Epi", "Ano", "Amphi", "Kato", "Panto", "Endo", "Bathy", "Thapto").indexOf(prefix);
        if (rank >= 0) return rank;
      }
      return 5;
    }
    /** Only depth rules 1 and 2 preserve the qualifier's complete diagnostic criteria. */
    boolean proves(Qualifier target) {
      return equals(target) || addition.equals(target.addition) && definition.equals(target.definition)
          && target.prefixes.isEmpty() && !prefixes.isEmpty() && !displaced()
          && !prefixes.contains("Supra") && (definition.depthRule() == 1 || definition.depthRule() == 2);
    }
  }

  Qualifier qualifier(String token) {
    if (token == null || token.isBlank()) throw new IllegalArgumentException("Empty qualifier");
    String value = token.toLowerCase(Locale.ROOT);
    var direct = qualifierAliases.get(value);
    if (direct != null) return new Qualifier(direct, List.of(), "");
    // Named Novic combinations and destroyed subsurface diagnostic horizons (Chapter 5).
    if (value.contains("-")) {
      String[] parts = value.split("-", -1);
      if (parts.length != 2) throw new IllegalArgumentException("Invalid qualifier " + token);
      String addition = capitalize(parts[0]);
      var q = qualifier(parts[1]);
      boolean novic = q.root().equals("Novic") && Set.of("Aeoli", "Fluvi", "Solimovi", "Techni", "Tephri", "Transporti").contains(addition);
      boolean relocatic = q.root().equals("Relocatic") && Set.of("Argi", "Natri", "Spodi", "Cambi", "Calci", "Gypsi",
          "Duri", "Ferrali", "Ferri", "Fragi", "Niti", "Petrocalci", "Petroduri", "Petrogypsi",
          "Petroplinthi", "Plinthi", "Sali", "Verti").contains(addition);
      if (!novic && !relocatic) throw new IllegalArgumentException("Invalid qualifier addition " + token);
      return new Qualifier(q.definition(), q.prefixes(), addition);
    }
    // Chapter 6 codes: two-letter definition code followed by specifier letters.
    if (value.matches("[a-z]{3,6}") && qualifierAliases.containsKey(value.substring(0, 2))) {
      Definition q = qualifierAliases.get(value.substring(0, 2));
      String tail = value.substring(2);
      if (q.name().equals("Novic") || q.parent().equals("Novic")) {
        var additions = Map.of("a", "Aeoli", "f", "Fluvi", "s", "Solimovi", "t", "Techni", "v", "Tephri", "p", "Transporti");
        if (tail.length() == 1 && additions.containsKey(tail)) return qualifier(additions.get(tail) + "-" + q.name());
      }
      var prefixes = new ArrayList<String>();
      for (int i = tail.length() - 1; i >= 0; i--) {
        String suffix = tail.substring(i, i + 1);
        String prefix = PREFIXES.entrySet().stream().filter(e -> e.getValue().equals(suffix)).map(Map.Entry::getKey)
            .findFirst().orElseThrow(() -> new IllegalArgumentException("Unknown specifier in " + token));
        prefixes.add(prefix);
      }
      validatePrefixes(q, prefixes);
      return new Qualifier(q, prefixes, "");
    }
    var prefixes = new ArrayList<String>();
    while (!qualifierAliases.containsKey(value)) {
      String remaining = value;
      String prefix = PREFIXES.keySet().stream().filter(p -> remaining.startsWith(p.toLowerCase(Locale.ROOT)))
          .findFirst().orElseThrow(() -> new IllegalArgumentException("Unknown qualifier " + token));
      prefixes.add(prefix);
      value = value.substring(prefix.length());
    }
    Definition q = qualifierAliases.get(value);
    validatePrefixes(q, prefixes);
    return new Qualifier(q, prefixes, "");
  }

  private void validatePrefixes(Definition q, List<String> prefixes) {
    if (prefixes.size() > 2 || new HashSet<>(prefixes).size() != prefixes.size()
        || prefixes.size() == 2 && !(prefixes.get(0).equals("Thapto") && prefixes.get(1).equals("Bathy")))
      throw new IllegalArgumentException("Invalid specifier combination");
    for (String p : prefixes) {
      boolean allowed = switch (p) {
        case "Thapto", "Supra" -> q.layerDiagnostic();
        case "Bathy" -> (q.depthRule() == 1 || q.depthRule() == 2)
            && (q.depthPrefixes().isEmpty() || q.depthPrefixes().contains("Endo"));
        default -> !q.depthPrefixes().isEmpty() ? q.depthPrefixes().contains(p)
            : switch (q.depthRule()) {
              case 1 -> Set.of("Epi", "Endo", "Amphi").contains(p);
              case 2 -> Set.of("Epi", "Endo", "Amphi", "Ano", "Kato", "Poly", "Panto").contains(p);
              case 3, 5 -> Set.of("Epi", "Endo").contains(p);
              case 4 -> p.equals("Epi");
              default -> false;
            };
      };
      if (!allowed) throw new IllegalArgumentException(p + "- cannot be used with " + q.name());
    }
  }

  private static String capitalize(String text) { return Character.toUpperCase(text.charAt(0)) + text.substring(1); }
  private static List<List<String>> slots(String text) {
    return text.isEmpty() ? List.of() : Arrays.stream(text.split(";")).map(s -> List.of(s.split("/"))).toList();
  }
  private static List<String[]> read(String file) {
    try (var stream = WrbVocabulary.class.getResourceAsStream(file)) {
      if (stream == null) throw new IllegalStateException("Missing WRB vocabulary: " + file);
      return new String(stream.readAllBytes(), StandardCharsets.UTF_8).lines()
          .filter(l -> !l.isBlank() && !l.startsWith("#")).map(l -> l.split("\t", -1)).toList();
    } catch (IOException e) { throw new UncheckedIOException(e); }
  }
}
