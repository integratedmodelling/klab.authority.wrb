package org.integratedmodelling.klab.authority.wrb;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class WrbExpressionTest {
  @ParameterizedTest
  @CsvSource(value = {
      "Albic Stagnic Luvisol (Episiltic, Katoclayic, Bathysiltic, Cutanic, Differentic, Epic, Ochric)|LV-st.ab-slp.cek.sld-ct.df.ep.oh",
      "Hemic Folic Endorockic Histosol (Dystric)|HS-rkn.fo.hm--dy",
      "Haplic Ferralsol (Pantoloamic, Dystric, Endic, Humic, Posic, Bathypetroplinthic)|FR-ha-loe-dy.ed.hu.po-ppd",
      "Calcaric Skeletic Pantofluvic Fluvisol (Pantoarenic, Ochric)|FL-fve.sk.ca-are-oh",
      "Dystric Umbric Aluandic Andosol (Pantosiltic, Thaptohistic, Hyperhumic)|AN-aa.um.dy-sle-hib.jh",
      "Isolatic Ekranic Technosol (Supraarenic, Supracalcaric)|TC-ek.il-ars-cas",
      "Dystric Arenosol (Bathyspodic)|AR-dy--sdd",
      "Albic Endostagnic Luvisol (Anoloamic, Endoclayic, Cutanic, Differentic, Endic, Ochric)|LV-stn.ab-loa.cen-ct.df.ed.oh"
  }, delimiter = '|')
  void officialExamplesRoundTrip(String name, String code) {
    var expression = WrbExpression.parse(name);
    assertEquals(code, expression.code());
    assertEquals(name, WrbExpression.parse(code).label());
    assertEquals(code, WrbExpression.parse(expression.code()).code());
  }

  @Test void vocabularyIsCompleteAndEveryListedElementRoundTrips() {
    var vocabulary = new WrbVocabulary();
    assertEquals(32, vocabulary.groups.size()); assertEquals(286, vocabulary.definitions.size());
    for (var group : vocabulary.groups.values()) {
      assertEquals(group.code(), WrbExpression.parse(group.name()).code());
      assertEquals(group.code(), WrbExpression.parse(group.name().equals("Solonetz") ? "Solonetz" : group.name() + "s").code());
      for (var slots : List.of(group.principal(), group.supplementary())) for (var slot : slots) for (var name : slot) {
        var q = vocabulary.qualifier(name);
        assertEquals(q.name(), vocabulary.qualifier(q.code()).name());
        String expression = slots == group.principal() ? name + " " + group.name() : group.name() + " (" + name + ")";
        // Epi-Eutric/Dystric rule 3 requires the predominant opposite qualifier.
        if (q.definition().depthRule() == 3 && !q.prefixes().isEmpty()) continue;
        var parsed = WrbExpression.parse(expression);
        assertEquals(parsed.code(), WrbExpression.parse(parsed.code()).code(), expression);
      }
    }
    for (var q : vocabulary.definitions.values()) assertEquals(q.name(), vocabulary.qualifier(q.code()).name());
  }

  @Test void normalizesOrderCaseNamesAndCodes() {
    String expected = "LV-st.ab-loa.cen-ct.df.ed.oh";
    assertEquals(expected, WrbExpression.parse("st ab luvisols (oh, df, cen, ct, loa, ed)").code());
    assertEquals(expected, WrbExpression.parse("lv-ab.st-cen.loa-oh.ed.df.ct").code());
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "Unknownsol", "Albic Luvisol (", "Luvisol ()", "Luvisol (Humic,)",
      "Luvisol (Albic)", "Humic Luvisol", "Haplic Albic Luvisol", "Albic Albic Luvisol",
      "Dystric Eutric Cambisol", "Calcaric Cambisol (Eutric)", "Luvisol (Epihumic)",
      "Luvisol (Hyperalbic)", "Luvisol (Epiendoclayic)", "Luvisol (Epieutric)",
      "LV-ab-ct", "LV---", "LV-ab..st", "LV-ab-----oh", "Luvisol (Acroxic)",
      "Luvisol (Endoalcalic)", "Luvisol over Regosol", "Luvisol over Leptosol"})
  void rejectsInvalidExpressions(String expression) { assertThrows(IllegalArgumentException.class, () -> WrbExpression.parse(expression)); }

  @Test void predominantQualifierPrecedesAdditionalRuleThreeVariantInCodes() {
    var expression = WrbExpression.parse("Epidystric Eutric Cambisol");
    assertEquals("CM-eu.dyp", expression.code());
    assertEquals("Epidystric Eutric Cambisol", WrbExpression.parse(expression.code()).label());
    assertThrows(IllegalArgumentException.class, () -> WrbExpression.parse("Luvisol (Dystric, Eutric)"));
  }

  @Test void nonListedAndBuriedSoilsKeepCanonicalIdentity() {
    assertEquals("AR-eu---lxd", WrbExpression.parse("Eutric Arenosol (Bathylixic)").code());
    String buried = "Skeletic Umbrisol (Siltic) over Albic Podzol (Arenic)";
    var parsed = WrbExpression.parse(buried);
    assertEquals(buried, WrbExpression.parse(parsed.code()).label());
    assertEquals("LV---sja", WrbExpression.parse("Luvisol (Aeoli-Siltinovic)").code());
    assertEquals("LV---sja", WrbExpression.parse("LV---sja").code());
    var destroyed = WrbExpression.parse("Spodi-Epirelocatic Regosol");
    assertEquals(destroyed.code(), WrbExpression.parse(destroyed.code()).code());
  }
}
