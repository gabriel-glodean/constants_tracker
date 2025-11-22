package org.glodean.constants.services;

import org.glodean.constants.extractor.ModelExtractor;

public interface ExtractionService {
  ModelExtractor extractorForClassFile(byte[] classFileBytes);

  ModelExtractor extractorForJarFile(byte[] classFileBytes);
}
