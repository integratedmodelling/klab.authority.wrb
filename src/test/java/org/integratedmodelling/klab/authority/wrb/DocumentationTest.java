package org.integratedmodelling.klab.authority.wrb;

import static org.junit.jupiter.api.Assertions.*;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;
import org.eclipse.xtext.parser.IParser;
import org.integratedmodelling.languages.WorldviewStandaloneSetup;
import org.junit.jupiter.api.Test;

class DocumentationTest {
  @Test void documentedBindingParsesWithTheWorldviewGrammar() throws Exception {
    String readme = Files.readString(Path.of("README.md"));
    var matcher = Pattern.compile("(?s)```kwv\\R(.*?)```").matcher(readme);
    assertTrue(matcher.find());
    var parser = new WorldviewStandaloneSetup().createInjectorAndDoEMFRegistration().getInstance(IParser.class);
    var parsed = parser.parse(new StringReader("ontology soil in domain root version 1.0.0;\n" + matcher.group(1)));
    var messages = new java.util.ArrayList<String>();
    parsed.getSyntaxErrors().forEach(n -> messages.add(n.getSyntaxErrorMessage().getMessage()));
    assertFalse(parsed.hasSyntaxErrors(), messages.toString());
  }
  @Test void everyDocumentedNormalizationExampleResolvesAsWritten() throws Exception {
    var matcher = Pattern.compile("(?m)^\\| (.*?) \\| `([A-Z]{2}(?:-[a-z.\\-]+)?)` \\|$").matcher(Files.readString(Path.of("README.md")));
    int examples = 0;
    while (matcher.find()) {
      var inputs = Pattern.compile("`([^`]+)`").matcher(matcher.group(1));
      while (inputs.find()) {
        assertEquals(matcher.group(2), WrbExpression.parse(inputs.group(1)).code(), inputs.group(1)); examples++;
      }
    }
    assertEquals(9, examples);
  }
  @Test void documentsBothNormativeTableChoicesAndFerralsolNormalization() throws Exception {
    String readme = Files.readString(Path.of("README.md"));
    assertTrue(readme.contains("## WRB inconsistencies and implementation choices"));
    assertEquals("FR-ha-loe-dy.ed.hu.po-ppd", WrbExpression.parse(
        "Haplic Ferralsol (Pantoloamic, Dystric, Endic, Humic, Bathypetroplinthic, Posic)").code());
    var vocabulary = new WrbVocabulary();
    assertEquals("Pelocrustic", vocabulary.qualifier("pq").name());
    assertEquals("Hypergypsic", vocabulary.qualifier("jg").name());
    assertEquals("Hyperhydragric", vocabulary.qualifier("jy").name());
  }
}
