package org.glodean.constants.extractor.configfile;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import org.glodean.constants.model.UnitConstant;
import org.glodean.constants.model.UnitConstants;
import org.glodean.constants.model.UnitDescriptor;
import org.junit.jupiter.api.Test;

class YamlConstantsExtractorTest {

    private static final Path YAML_FILE =
            Path.of("src/test/resources/samples/application.yml");

    private YamlConstantsExtractor extractorFor(Path path) throws Exception {
        return new YamlConstantsExtractor(Files.readAllBytes(path));
    }

    private UnitDescriptor descriptorFor(Path path) {
        return new UnitDescriptor(ConfigFileSourceKind.YAML, path.getFileName().toString());
    }

    @Test
    void extractsConstantsFromYaml() throws Exception {
        Collection<UnitConstants> result =
                extractorFor(YAML_FILE).extract(descriptorFor(YAML_FILE));

        assertNotNull(result);
        assertEquals(1, result.size());

        UnitConstants unit = result.iterator().next();
        assertEquals(ConfigFileSourceKind.YAML, unit.source().sourceKind());
        assertFalse(unit.constants().isEmpty(), "Should extract at least one constant");

        boolean foundPort = unit.constants().stream()
                .anyMatch(c -> Integer.valueOf(8080).equals(c.value()));
        assertTrue(foundPort, "Should find the server.port value 8080");

        boolean foundUrl = unit.constants().stream()
                .anyMatch(c -> "https://db.example.com/mydb".equals(c.value())
                        && c.usages().stream()
                                .anyMatch(u -> u.semanticType() == UnitConstant.CoreSemanticType.URL_RESOURCE));
        assertTrue(foundUrl, "Should classify datasource URL as URL_RESOURCE");
    }

    @Test
    void extractsListItems() throws Exception {
        Collection<UnitConstants> result =
                extractorFor(YAML_FILE).extract(descriptorFor(YAML_FILE));
        UnitConstants unit = result.iterator().next();

        boolean foundFeature = unit.constants().stream()
                .anyMatch(c -> "feature-one".equals(c.value()));
        assertTrue(foundFeature, "Should extract list items");
    }

    @Test
    void descriptorIsReusedAsSource() throws Exception {
        UnitDescriptor descriptor = descriptorFor(YAML_FILE);
        Collection<UnitConstants> result = extractorFor(YAML_FILE).extract(descriptor);
        assertEquals(descriptor, result.iterator().next().source());
    }
}
