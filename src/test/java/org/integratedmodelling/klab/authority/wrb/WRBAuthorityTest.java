package org.integratedmodelling.klab.authority.wrb;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import org.integratedmodelling.klab.api.services.Authority;
import org.integratedmodelling.klab.api.services.ConfiguredAuthoritySnapshot;
import org.junit.jupiter.api.Test;

class WRBAuthorityTest {
  static Authority.ConfigurationRequest request(String name, String root) {
    return new Authority.ConfigurationRequest("test.worldview", name, root,
        Map.of("urn", WRBAuthority.URN, "edition", WRBAuthority.EDITION));
  }
  @Test void resolvesCanonicalIdentityAndHierarchyWithoutCycles() {
    try (var authority = new WRBAuthority()) {
      var id = authority.configure(request("WRB", "soil:SoilType"));
      var full = authority.resolveIdentity(id, "Albic Stagnic Luvisol (Cutanic)");
      assertTrue(full.getNotifications().isEmpty(), full.getDescription());
      assertEquals("LV-st.ab--ct", full.getId());
      assertEquals(full.getConceptName(), authority.resolveIdentity(id, full.getId()).getConceptName());
      assertEquals("WRB:[LV-st.ab--ct]", full.getLocator());
      assertEquals(List.of("LV"), full.getParentIds());
      var root = authority.resolveIdentity(id, "LV");
      assertEquals("soil:SoilType", root.getBaseIdentity()); assertTrue(root.getParentIds().isEmpty());
      assertFalse(authority.resolveIdentity(id, "Madeup Luvisol").getNotifications().isEmpty());
    }
  }
  @Test void matchingIsDirectionalAndDoesNotTreatMissingInformationAsDisjointness() {
    try (var a = new WRBAuthority()) {
      var id = a.configure(request("WRB", "soil:SoilType"));
      assertEquals(Authority.Subsumption.SUBSUMES, a.subsumption(id, "LV", "LV-st.ab--ct"));
      assertEquals(Authority.Subsumption.SUBSUMES, a.subsumption(id, "LV-st", "LV-stn.ab--ct"));
      assertEquals(Authority.Subsumption.UNKNOWN, a.subsumption(id, "LV-st", "LV"));
      assertEquals(Authority.Subsumption.INCOMPATIBLE, a.subsumption(id, "LV", "CM"));
      assertEquals(Authority.DistanceStatus.COMPATIBLE, a.semanticDistance(id, "LV", "LV-ab").status());
      assertTrue(a.semanticDistance(id, "LV", "LV-ab").distance() > 0);
      assertEquals(0, a.semanticDistance(id, "Albic Luvisol", "LV-ab").distance());
      for (var pair : List.of(List.of("AR----sd", "AR-dy--sdd"), List.of("CM---sz", "CM---qz"),
          List.of("LV----hi", "LV----hib"), List.of("CM-sk", "CM-skn"))) {
        assertTrue(a.resolveIdentity(id, pair.get(0)).getNotifications().isEmpty(), pair.toString());
        assertTrue(a.resolveIdentity(id, pair.get(1)).getNotifications().isEmpty(), pair.toString());
        assertEquals(Authority.Subsumption.UNKNOWN, a.subsumption(id, pair.get(0), pair.get(1)));
      }
      assertEquals(Authority.DistanceStatus.UNKNOWN, a.semanticDistance(id, "garbage", "LV").status());
    }
  }
  @Test void bridgesSearchReconciliationAndClosedGroupList() {
    try (var a = new WRBAuthority()) {
      var first = a.configure(request("WRB", "soil:SoilType"));
      var second = a.configure(request("SOILS", "pedology:SoilIdentity"));
      assertEquals("SOILS", a.resolveIdentity(second, "LV").getAuthorityName());
      assertEquals("pedology:SoilIdentity", a.resolveIdentity(second, "LV").getBaseIdentity());
      assertEquals("LV", a.search("luvi", null, first).getFirst().getId());
      assertEquals("LV-ab", a.search("Albic Luvisol", null, first).getFirst().getId());
      assertEquals("LV-ab", a.reconcile(first, Map.of("name", "Albic Luvisol")).getId());
      assertFalse(a.reconcile(first, Map.of("name", "Luvisol", "code", "CM")).getNotifications().isEmpty());
      var definition = a.getCodelistDefinitions(first).get("groups");
      assertFalse(definition.acceptsProposals()); assertEquals(32, definition.codelist().size());
      assertEquals("LV", definition.codelist().value("WRB", "Luvisol"));
      a.releaseConfiguration(first);
      assertThrows(IllegalArgumentException.class, () -> a.resolveIdentity(first, "LV"));
      assertTrue(a.resolveIdentity(second, "LV").getNotifications().isEmpty());
    }
  }
  @Test void snapshotIsIndependentExactAndRejectsTampering() {
    var r = request("WRB", "soil:SoilType");
    ConfiguredAuthoritySnapshot snapshot;
    try (var a = new WRBAuthority()) { snapshot = a.captureConfiguration(a.configure(r)); }
    try (var a = new WRBAuthority()) {
      var id = a.restoreConfiguration(r, snapshot);
      assertEquals(snapshot, a.captureConfiguration(id));
      assertEquals("LV-ab", a.resolveIdentity(id, "Albic Luvisol").getId());
      var changed = snapshot.state(); changed[0] ^= 1;
      var corrupt = new ConfiguredAuthoritySnapshot(snapshot.format(), snapshot.providerUrn(), r, changed);
      assertThrows(IllegalArgumentException.class, () -> a.restoreConfiguration(r, corrupt));
      assertThrows(IllegalArgumentException.class, () -> a.restoreConfiguration(request("OTHER", "soil:SoilType"), snapshot));
    }
  }
  @Test void configurationRejectsOtherEditionsAndUnknownParameters() {
    var annotation = WRBAuthority.class.getAnnotation(org.integratedmodelling.klab.api.services.reasoner.Authority.class);
    assertEquals(WRBAuthority.URN, annotation.urn()); assertTrue(annotation.searchable());
    assertArrayEquals(new String[]{"groups"}, annotation.codelists());
    try (var a = new WRBAuthority()) {
      assertThrows(IllegalArgumentException.class, () -> a.configure(new Authority.ConfigurationRequest("test", "WRB", "soil:SoilType",
          Map.of("urn", WRBAuthority.URN, "edition", "1998"))));
      assertThrows(IllegalArgumentException.class, () -> a.configure(new Authority.ConfigurationRequest("test", "WRB", "soil:SoilType",
          Map.of("urn", WRBAuthority.URN, "edtion", "2022"))));
    }
  }
}
