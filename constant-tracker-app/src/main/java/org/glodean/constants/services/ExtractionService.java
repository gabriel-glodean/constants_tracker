package org.glodean.constants.services;

import org.glodean.constants.extractor.ModelExtractor;

/**
 * Service for creating {@link ModelExtractor} instances from various binary sources.
 *
 * <p>This service abstracts the creation of extractors for different input formats
 * (single class files, JAR files, etc.) allowing clients to uniformly extract constant
 * usage information regardless of the source format.
 */
public interface ExtractionService {
  /**
   * Creates an extractor for analyzing a single compiled class file.
   *
   * @param classFileBytes the raw bytes of a .class file
   * @return a configured {@link ModelExtractor} ready to analyze the class
   * @throws IllegalArgumentException if the bytes don't represent a valid class file
   */
  ModelExtractor extractorForClassFile(byte[] classFileBytes);

  /**
   * Creates an extractor for analyzing all classes within a JAR file.
   *
   * @param classFileBytes the raw bytes of a .jar file
   * @return a configured {@link ModelExtractor} that will analyze all contained classes
   * @throws IllegalArgumentException if the bytes don't represent a valid JAR file
   */
  ModelExtractor extractorForJarFile(byte[] classFileBytes);
}
