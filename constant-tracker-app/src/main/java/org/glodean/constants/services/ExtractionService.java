package org.glodean.constants.services;

import java.nio.file.Path;
import java.util.Collection;
import java.util.zip.ZipInputStream;
import org.glodean.constants.extractor.ModelExtractor;
import org.glodean.constants.model.UnitConstants;
import org.glodean.constants.model.UnitDescriptor;

/**
 * Service for extracting constants from various binary sources.
 */
public interface ExtractionService {
  /**
   * Parses a single compiled class file and returns its extracted constants.
   *
   * @param classFileBytes the raw bytes of a .class file
   * @param descriptor metadata describing this unit
   * @return the extracted {@link UnitConstants}
   * @throws ModelExtractor.ExtractionException if the bytes don't represent a valid class file
   */
  Collection<UnitConstants> extractClassFile(byte[] classFileBytes, UnitDescriptor descriptor)
      throws ModelExtractor.ExtractionException;

  /**
   * Opens a JAR file and extracts constants from all contained classes.
   *
   * @param jarPath path to the JAR file on disk
   * @param descriptor metadata describing this unit
   * @return the extracted {@link UnitConstants} for all classes in the JAR
   * @throws ModelExtractor.ExtractionException if the path does not point to a valid JAR file
   */
  Collection<UnitConstants> extractJarFile(Path jarPath, UnitDescriptor descriptor)
      throws ModelExtractor.ExtractionException;

  /**
   * Walks a {@link ZipInputStream} and extracts constants from all {@code .class} entries.
   * Reading is sequential; analysis is parallelised internally. The caller owns the stream.
   *
   * @param zis        open ZIP stream positioned at its beginning
   * @param descriptor metadata describing this unit (e.g. the nested JAR filename)
   * @return the extracted {@link UnitConstants} for all classes found in the stream
   * @throws ModelExtractor.ExtractionException if the stream cannot be walked
   */
  Collection<UnitConstants> extractZipStream(ZipInputStream zis, UnitDescriptor descriptor)
      throws ModelExtractor.ExtractionException;
}
