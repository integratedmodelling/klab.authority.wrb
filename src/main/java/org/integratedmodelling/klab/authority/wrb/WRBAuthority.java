package org.integratedmodelling.klab.authority.wrb;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.integratedmodelling.klab.api.collections.Pair;
import org.integratedmodelling.klab.api.knowledge.Codelist;
import org.integratedmodelling.klab.api.knowledge.impl.CodelistImpl;
import org.integratedmodelling.klab.api.services.Authority;
import org.integratedmodelling.klab.api.services.ConfiguredAuthoritySnapshot;
import org.integratedmodelling.klab.api.services.SnapshottingAuthority;
import org.integratedmodelling.klab.api.services.reasoner.objects.AuthorityIdentitySyntax;
import org.integratedmodelling.klab.api.services.runtime.Notification;

/** Offline, version-pinned WRB naming authority. */
@org.integratedmodelling.klab.api.services.reasoner.Authority(
    urn = WRBAuthority.URN, version = "1.0.0-SNAPSHOT", embeddable = true,
    searchable = true, codelists = {"groups"})
public final class WRBAuthority implements SnapshottingAuthority {
  public static final String URN = "klab.authority.wrb.authority";
  public static final String EDITION = "2022-2024";
  public static final String SOURCE = "https://wrb.isric.org/files/WRB_fourth_edition_2022-12-18_errata_correction_2024-09-24.pdf";
  private static final String FORMAT = "wrb.2022-2024.v1";
  private final Map<String, ConfigurationRequest> bridges = new ConcurrentHashMap<>();

  @Override public String getUrn() { return URN; }
  @Override public String configure(ConfigurationRequest request) {
    Objects.requireNonNull(request);
    Object urn = request.parameters().get("urn");
    if (!URN.equals(urn) && !(URN + "@1.0.0-SNAPSHOT").equals(urn))
      throw new IllegalArgumentException("Incorrect WRB provider URN/version");
    if (!Set.of("urn", "edition", "codelists", "semanticBoundaries").containsAll(request.parameters().keySet()))
      throw new IllegalArgumentException("Unknown WRB configuration parameter");
    if (!EDITION.equals(request.parameters().getOrDefault("edition", EDITION)))
      throw new IllegalArgumentException("WRB supports edition " + EDITION);
    request.requireSupportedBoundaries(Set.of());
    var snapshot = new ConfiguredAuthoritySnapshot(FORMAT, URN, request, vocabularyDigest());
    String id = UUID.randomUUID().toString(); bridges.put(id, snapshot.request()); return id;
  }
  @Override public void releaseConfiguration(String id) { if (id != null) bridges.remove(id); }
  @Override public void close() { bridges.clear(); }
  private ConfigurationRequest bridge(String id) {
    var request = id == null ? null : bridges.get(id);
    if (request == null) throw new IllegalArgumentException("Unknown or released WRB configuration");
    return request;
  }

  @Override public Identity resolveIdentity(String id, String expression) {
    var request = bridge(id);
    try {
      var soil = WrbExpression.parse(expression);
      String code = soil.code();
      return new WrbIdentity(code, "WRB_" + hexDigest(code.getBytes(StandardCharsets.UTF_8)), request.name(),
          soil.isGeneric() ? request.rootIdentity() : null, soil.isGeneric() ? List.of() : List.of(soil.groupCode()),
          soil.label(), "WRB 2022 (2024 corrections): " + soil.label() + ". Naming validation does not certify field diagnostics.",
          AuthorityIdentitySyntax.encode(request.name(), code), List.of());
    } catch (IllegalArgumentException e) { return failure(request, expression, e.getMessage()); }
  }
  private Identity failure(ConfigurationRequest request, String input, String message) {
    return new WrbIdentity(input == null ? "" : input, null, request.name(), null, List.of(), input,
        message, null, List.of(Notification.error("WRB: " + message)));
  }
  @Override public Identity reconcile(String id, Map<String, String> fields) {
    var request = bridge(id);
    if (fields == null || fields.size() != 1 || !Set.of("expression", "name", "code").containsAll(fields.keySet()))
      return failure(request, "", "Supply exactly one expression, name, or code field");
    return resolveIdentity(id, fields.values().iterator().next());
  }
  @Override public List<Identity> search(String query, String filter, String id) {
    bridge(id);
    if (filter != null && !filter.isBlank()) throw new IllegalArgumentException("WRB has no sub-authority filters");
    if (query == null || query.isBlank() || query.length() > 8192) throw new IllegalArgumentException("A bounded search query is required");
    var exact = resolveIdentity(id, query);
    if (exact.getNotifications().isEmpty()) return List.of(exact);
    String text = query.strip().toLowerCase(Locale.ROOT);
    return WrbExpression.groups().stream().filter(g -> (g.code() + " " + g.name()).toLowerCase(Locale.ROOT).contains(text))
        .map(g -> resolveIdentity(id, g.code())).toList();
  }
  @Override public Authority subAuthority(String catalog) {
    if (catalog == null || catalog.isBlank()) return this;
    throw new IllegalArgumentException("WRB has no sub-authorities");
  }
  @Override public Subsumption subsumption(String id, String targetId, String candidateId) {
    var distance = semanticDistance(id, targetId, candidateId);
    return switch (distance.status()) {
      case COMPATIBLE -> Subsumption.SUBSUMES;
      case INCOMPATIBLE -> Subsumption.INCOMPATIBLE;
      default -> Subsumption.UNKNOWN;
    };
  }
  @Override public SemanticDistance semanticDistance(String id, String targetId, String candidateId) {
    bridge(id);
    try {
      var target = WrbExpression.parse(targetId); var candidate = WrbExpression.parse(candidateId);
      if (!target.groupCode().equals(candidate.groupCode())) return new SemanticDistance(DistanceStatus.INCOMPATIBLE, -1,
          List.of("Distinct Reference Soil Groups in the same WRB edition"));
      if (target.includes(candidate)) return new SemanticDistance(DistanceStatus.COMPATIBLE,
          target.code().equals(candidate.code()) ? 0 : Math.min(100, Math.max(1, candidate.qualifierCount() - target.qualifierCount())),
          List.of("The candidate entails the target's soil group and every stated qualifier"));
      return SemanticDistance.unknown("The candidate does not prove every target qualifier; omitted qualifiers are unknown");
    } catch (IllegalArgumentException e) { return SemanticDistance.unknown(e.getMessage()); }
  }
  @Override public Map<String, Codelist> getCodelists(String id) {
    var request = bridge(id);
    var list = new CodelistImpl();
    list.setUrn(URN + ":groups:" + EDITION); list.setName("groups");
    list.setAuthorityId(request.name()); list.setWorldview(request.worldview()); list.setRootConceptId(request.rootIdentity());
    list.setDescription("The 32 unqualified WRB 2022 Reference Soil Groups (2024 corrections)");
    list.setEntries(WrbExpression.groups().stream().map(g -> new CodelistImpl.Entry(request.name(), g.name(), g.code(), g.name(), true)).toList());
    return Map.of("groups", list);
  }
  @Override public Map<String, CodelistDefinition> getCodelistDefinitions(String id) {
    return Map.of("groups", new CodelistDefinition(getCodelists(id).get("groups"), false));
  }
  @Override public CachePolicy getCachePolicy() { return new CachePolicy(FORMAT, Long.MAX_VALUE, Long.MAX_VALUE, Long.MAX_VALUE); }
  @Override public Capabilities getCapabilities() {
    return new Capabilities() {
      @Override public String getDescription() { return "WRB 2022/2024 soil names and expression validation"; }
      @Override public boolean isSearchable() { return true; }
      @Override public boolean isFuzzy() { return true; }
      @Override public boolean isReconciliationSupported() { return true; }
      @Override public boolean isSemanticDistanceSupported() { return true; }
      @Override public List<Pair<String, String>> getSubAuthorities() { return List.of(); }
      @Override public List<String> getDocumentationFormats() { return List.of("application/pdf"); }
      @Override public String getWorldview() { return null; }
    };
  }
  @Override public ConfiguredAuthoritySnapshot captureConfiguration(String id) {
    return new ConfiguredAuthoritySnapshot(FORMAT, URN, bridge(id), vocabularyDigest());
  }
  @Override public String restoreConfiguration(ConfigurationRequest request, ConfiguredAuthoritySnapshot snapshot) {
    if (snapshot == null || !FORMAT.equals(snapshot.format()) || !URN.equals(snapshot.providerUrn())
        || !request.equals(snapshot.request()) || !Arrays.equals(vocabularyDigest(), snapshot.state()))
      throw new IllegalArgumentException("WRB snapshot format, bridge, or bundled vocabulary differs");
    return configure(request);
  }
  private static byte[] vocabularyDigest() {
    try {
      var digest = MessageDigest.getInstance("SHA-256");
      digest.update(FORMAT.getBytes(StandardCharsets.UTF_8));
      for (String resource : List.of("groups.tsv", "qualifiers.tsv")) {
        try (var stream = WRBAuthority.class.getResourceAsStream(resource)) {
          if (stream == null) throw new IllegalStateException("Missing " + resource);
          digest.update(stream.readAllBytes());
        }
      }
      return digest.digest();
    } catch (NoSuchAlgorithmException | java.io.IOException e) { throw new IllegalStateException(e); }
  }
  private static String hexDigest(byte[] bytes) {
    try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
    catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
  }
}
