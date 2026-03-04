package org.glodean.constants.services;

import static org.junit.jupiter.api.Assertions.*;

import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.util.Collection;
import org.glodean.constants.extractor.ModelExtractor.ExtractionException;
import org.glodean.constants.extractor.bytecode.AnalysisMerger;
import org.glodean.constants.extractor.bytecode.FileSystemModelExtractor;
import org.glodean.constants.extractor.bytecode.InternalStringConcatPatternSplitter;
import org.glodean.constants.model.ClassConstants;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/**
 * Smoke-test that runs {@link FileSystemModelExtractor} against the current JDK runtime image
 * ({@code jrt:/} filesystem). This verifies the extractor can handle every {@code .class} file
 * shipped with the running Java version without throwing.
 */
class FileSystemModelExtractorJrtTest {

  @Test
  @EnabledIfEnvironmentVariable(named = "java.vm.analyze.rt", matches = "true")
  void extractAllJrtClassesWithoutError() throws ExtractionException {
    // The jrt:/ filesystem gives access to all modules in the running JDK (Java 9+)
    FileSystem jrtFs = FileSystems.getFileSystem(URI.create("jrt:/"));

    AnalysisMerger merger = new AnalysisMerger(new InternalStringConcatPatternSplitter());
    FileSystemModelExtractor extractor =
        new FileSystemModelExtractor(
            jrtFs, merger, "/modules/jdk.localedata/", new LoggingExtractionNotifier());

    Collection<ClassConstants> results = extractor.extract();

    // The JDK ships thousands of classes – we must get a non-trivial number of results
    assertNotNull(results);
    assertFalse(results.isEmpty(), "Expected at least some ClassConstants from the JDK runtime");

    // After filtering out jdk.localedata we still expect many class results
    assertTrue(
        results.size() > 500,
        "Expected > 500 class results from JDK runtime, got " + results.size());

    // Every entry should have a non-null, non-empty class name
    for (ClassConstants cc : results) {
      assertNotNull(cc.name(), "ClassConstants.name() must not be null");
      assertFalse(cc.name().isBlank(), "ClassConstants.name() must not be blank");
      assertNotNull(cc.constants(), "ClassConstants.constants() must not be null");
    }

    System.out.printf(
        "Successfully extracted constants from %d classes in the JDK runtime (%s)%n",
        results.size(), System.getProperty("java.version"));
  }
}
