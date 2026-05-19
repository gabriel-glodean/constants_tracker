package org.glodean.constants.extractor.configfile;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import org.glodean.constants.model.UnitConstant;
import org.glodean.constants.model.UnitConstants;
import org.glodean.constants.model.UnitDescriptor;
import org.junit.jupiter.api.Test;

class PropertiesConstantsExtractorTest {

    private static final Path PROPERTIES_FILE =
            Path.of("src/test/resources/samples/application.properties");
    private static final Path YAML_FILE =
            Path.of("src/test/resources/samples/application.yml");

    private PropertiesConstantsExtractor extractorFor(Path path) throws Exception {
        return new PropertiesConstantsExtractor(Files.readAllBytes(path));
    }

    private UnitDescriptor descriptorFor(Path path) {
        return new UnitDescriptor(ConfigFileSourceKind.PROPERTIES, path.getFileName().toString());
    }

    @Test
    void extractsConstantsFromProperties() throws Exception {
        Collection<UnitConstants> result =
                extractorFor(PROPERTIES_FILE).extract(descriptorFor(PROPERTIES_FILE));

        assertNotNull(result);
        assertEquals(1, result.size());

        UnitConstants unit = result.iterator().next();
        assertEquals(ConfigFileSourceKind.PROPERTIES, unit.source().sourceKind());
        assertFalse(unit.constants().isEmpty());

        boolean foundPort = unit.constants().stream()
                .anyMatch(c -> "8080".equals(c.value()));
        assertTrue(foundPort, "Should find server.port value");

        boolean foundUrl = unit.constants().stream()
                .anyMatch(c -> "https://db.example.com/mydb".equals(c.value())
                        && c.usages().stream()
                                .anyMatch(u -> u.semanticType() == UnitConstant.CoreSemanticType.URL_RESOURCE));
        assertTrue(foundUrl, "Should classify datasource URL as URL_RESOURCE");
    }

    @Test
    void classifiesPathProperties() throws Exception {
        Collection<UnitConstants> result =
                extractorFor(PROPERTIES_FILE).extract(descriptorFor(PROPERTIES_FILE));
        UnitConstants unit = result.iterator().next();

        boolean foundPath = unit.constants().stream()
                .anyMatch(c -> c.usages().stream()
                        .anyMatch(u -> u.semanticType() == UnitConstant.CoreSemanticType.FILE_PATH));
        assertTrue(foundPath, "Should classify path properties as FILE_PATH");
    }

    @Test
    void descriptorIsReusedAsSource() throws Exception {
        UnitDescriptor descriptor = descriptorFor(PROPERTIES_FILE);
        Collection<UnitConstants> result =
                extractorFor(PROPERTIES_FILE).extract(descriptor);
        assertEquals(descriptor, result.iterator().next().source());
    }
}
