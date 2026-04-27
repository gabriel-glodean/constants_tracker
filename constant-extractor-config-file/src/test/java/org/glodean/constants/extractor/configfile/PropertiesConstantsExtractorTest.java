package org.glodean.constants.extractor.configfile;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.util.Set;
import org.glodean.constants.model.UnitConstant;
import org.glodean.constants.model.UnitConstants;
import org.junit.jupiter.api.Test;

class PropertiesConstantsExtractorTest {

    private final PropertiesConstantsExtractor extractor = new PropertiesConstantsExtractor();

    private Path samplePath(String name) {
        return Path.of("src/test/resources/samples/" + name);
    }

    @Test
    void supportsPropertiesExtension() {
        assertTrue(extractor.supports(samplePath("application.properties")));
        assertFalse(extractor.supports(samplePath("application.yml")));
    }

    @Test
    void extractsConstantsFromProperties() throws Exception {
        Set<UnitConstants> result = extractor.extract(samplePath("application.properties"));

        assertNotNull(result);
        assertEquals(1, result.size());

        UnitConstants unit = result.iterator().next();
        assertEquals(ConfigFileSourceKind.PROPERTIES, unit.source().sourceKind());
        assertFalse(unit.constants().isEmpty());

        // Verify known value
        boolean foundPort = unit.constants().stream()
                .anyMatch(c -> "8080".equals(c.value()));
        assertTrue(foundPort, "Should find server.port value");

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
    void classifiesPathProperties() throws Exception {
        Set<UnitConstants> result = extractor.extract(samplePath("application.properties"));
        UnitConstants unit = result.iterator().next();

        boolean foundPath = unit.constants().stream()
                .anyMatch(c -> c.usages().stream()
                        .anyMatch(u ->
                                u.semanticType() == UnitConstant.CoreSemanticType.FILE_PATH));
        assertTrue(foundPath, "Should classify path properties as FILE_PATH");
    }

    @Test
    void returnsEmptyForUnsupportedFile() throws Exception {
        Set<UnitConstants> result = extractor.extract(samplePath("application.yml"));
        assertTrue(result.isEmpty());
    }

    @Test
    void nullPathThrows() {
        assertThrows(NullPointerException.class, () -> extractor.supports(null));
        assertThrows(NullPointerException.class, () -> extractor.extract(null));
    }
}
