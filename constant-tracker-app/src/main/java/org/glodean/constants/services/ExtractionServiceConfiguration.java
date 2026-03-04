package org.glodean.constants.services;

import org.glodean.constants.extractor.bytecode.AnalysisMerger;
import org.glodean.constants.extractor.bytecode.InternalStringConcatPatternSplitter;
import org.glodean.constants.extractor.bytecode.StringConcatPatternSplitter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ExtractionServiceConfiguration {
  @Bean
  StringConcatPatternSplitter stringConcatPatternSplitter() {
    return new InternalStringConcatPatternSplitter();
  }

  @Bean
  AnalysisMerger analysisMerger(@Autowired StringConcatPatternSplitter splitter) {
    return new AnalysisMerger(splitter);
  }
}
