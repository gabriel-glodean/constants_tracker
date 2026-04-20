package org.glodean.constants.extractor.configfile;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.util.Set;
import org.glodean.constants.model.UnitConstant;
import org.glodean.constants.model.UnitConstants;
import org.junit.jupiter.api.Test;

class YamlConstantsExtractorTest {

    private final YamlConstantsExtractor extractor = new YamlConstantsExtractor();

    private Path samplePath(String name) {
        return Path.of("src/test/resources/samples/" + name);
    }

    @Test
    void supportsYmlExtension() {
        assertTrue(extractor.supports(samplePath("application.yml")));
        assertTrue(extractor.supports(samplePath("application.yaml")));
        assertFalse(extractor.supports(samplePath("application.properties")));
        assertFalse(extractor.supports(samplePath("README.md")));
    }

    @Test
    void extractsConstantsFromYaml() throws Exception {
        Set<UnitConstants> result = extractor.extract(samplePath("application.yml"));

        assertNotNull(result);
        assertEquals(1, result.size());

        UnitConstants unit = result.iterator().next();
        assertEquals(ConfigFileSourceKind.YAML, unit.source().sourceKind());
        assertFalse(unit.constants().isEmpty(), "Should extract at least one constant");

        // Verify a known value is present
        boolean foundPort = unit.constants().stream()
                .anyMatch(c -> Integer.valueOf(8080).equals(c.value()));
        assertTrue(foundPort, "Should find the server.port value 8080");

        // Verify URL classification
        boolean foundUrl = unit.constants().stream()
                .anyMatch(c -> "https://db.example.com/mydb".equals(c.value())
                        && c.usages().stream()
                                .anyMatch(u ->
                                        u.semanticType()
                                                == UnitConstant.CoreSemanticType.URL_RESOURCE));
        assertTrue(foundUrl, "Should classify datasource URL as URL_RESOURCE");
    }

    @Test
    void extractsListItems() throws Exception {
        Set<UnitConstants> result = extractor.extract(samplePath("application.yml"));
        UnitConstants unit = result.iterator().next();

        boolean foundFeature = unit.constants().stream()
                .anyMatch(c -> "feature-one".equals(c.value()));
        assertTrue(foundFeature, "Should extract list items");
    }

    @Test
    void returnsEmptyForUnsupportedFile() throws Exception {
        Set<UnitConstants> result = extractor.extract(samplePath("application.properties"));
        assertTrue(result.isEmpty());
    }

    @Test
    void nullPathThrows() {
        assertThrows(NullPointerException.class, () -> extractor.supports(null));
        assertThrows(NullPointerException.class, () -> extractor.extract(null));
    }
}

