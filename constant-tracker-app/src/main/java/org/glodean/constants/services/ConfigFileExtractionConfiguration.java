package org.glodean.constants.services;

import org.glodean.constants.extractor.configfile.PropertiesConstantsExtractor;
import org.glodean.constants.extractor.configfile.YamlConstantsExtractor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring {@link Configuration} that produces config-file extractor beans.
 */
@Configuration
public class ConfigFileExtractionConfiguration {

    @Bean
    YamlConstantsExtractor yamlConstantsExtractor() {
        return new YamlConstantsExtractor();
    }

    @Bean
    PropertiesConstantsExtractor propertiesConstantsExtractor() {
        return new PropertiesConstantsExtractor();
    }
}
