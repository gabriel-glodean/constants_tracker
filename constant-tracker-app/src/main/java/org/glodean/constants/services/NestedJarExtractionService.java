package org.glodean.constants.services;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.zip.ZipInputStream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.glodean.constants.extractor.bytecode.BytecodeSourceKind;
import org.glodean.constants.model.UnitConstants;
import org.glodean.constants.model.UnitDescriptor;
import org.glodean.constants.store.postgres.repository.UnitDescriptorRepository;
import org.glodean.constants.util.DigestUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * Extracts constants from nested JARs found inside an uploaded fat JAR.
 *
 * <p>Supports one level of nesting only. The following entry locations are recognized:
 * <ul>
 *   <li>{@code /BOOT-INF/lib/*.jar} — Spring Boot executable JAR</li>
 *   <li>{@code /WEB-INF/lib/*.jar}  — WAR archive</li>
 *   <li>{@code /*.jar}              — root-level JARs (Maven Shade / Gradle Shadow)</li>
 * </ul>
 *
 * <p>Each nested JAR is extracted as a separate {@link UnitDescriptor} (keyed by filename)
 * and stored as its own unit under the same project/version as the outer JAR.
 *
 * <p>The pipeline is fully reactive:
 * <ol>
 *   <li>The outer ZipFileSystem is managed by {@link Flux#using} — it stays open for the
 *       duration of processing and is closed automatically on completion, error, or cancel.</li>
 *   <li>Reading bytes and computing the SHA-256 hash happen synchronously on
 *       {@link reactor.core.scheduler.Schedulers#boundedElastic()}.</li>
 *   <li>The dedup check against {@code unit_descriptors} is performed reactively via
 *       {@code filterWhen} — no {@code .block()} call required.</li>
 *   <li>Class extraction from each nested JAR also runs on
 *       {@link reactor.core.scheduler.Schedulers#boundedElastic()}.</li>
 * </ol>
 *
 * <p>Re-extraction is skipped when a row already exists in {@code unit_descriptors} with
 * the same {@code (project, version, content_hash)}. The existing index on
 * {@code content_hash} makes this lookup efficient.
 */
@Service
public class NestedJarExtractionService {

  private static final Logger logger = LogManager.getLogger(NestedJarExtractionService.class);

  /** Intermediate value object carrying a nested JAR's identity before extraction. */
  private record NestedJarCandidate(String jarName, byte[] bytes, String hash) {
    long size() { return bytes.length; }
  }

  private final ExtractionService extractionService;
  private final UnitDescriptorRepository descriptorRepository;
  private final ProjectVersionService projectVersionService;

  @Autowired
  public NestedJarExtractionService(
      ExtractionService extractionService,
      UnitDescriptorRepository descriptorRepository,
      ProjectVersionService projectVersionService) {
    this.extractionService = extractionService;
    this.descriptorRepository = descriptorRepository;
    this.projectVersionService = projectVersionService;
  }

  /**
   * Opens {@code outerJarPath} as a ZipFileSystem, finds all nested JAR entries matching
   * known fat-JAR layout patterns, and extracts constants from each new one.
   *
   * @param outerJarPath path to the outer JAR file on disk (must still exist)
   * @param project      project identifier
   * @return a {@link Mono} of all extracted {@link UnitConstants} across all nested JARs
   */
  public Mono<List<UnitConstants>> extractNestedJars(Path outerJarPath, String project) {
    return projectVersionService
        .getOrCreateOpenVersion(project)
        .flatMap(versionEntity -> {
          int version = versionEntity.version();

          return Flux.using(
                  // Open the outer ZipFS once; Flux.using closes it when the Flux terminates.
                  () -> FileSystems.newFileSystem(outerJarPath, (ClassLoader) null),
                  outerFs ->
                      // List nested JAR entry paths synchronously (fast metadata walk).
                      Mono.fromCallable(() -> listNestedJarEntries(outerFs))
                          .subscribeOn(Schedulers.boundedElastic())
                          .doOnNext(paths -> {
                            if (paths.isEmpty())
                              logger.atDebug().log("No nested JARs found in {}",
                                  outerJarPath.getFileName());
                            else
                              logger.atInfo().log(
                                  "Found {} nested JAR(s) in {} — extracting for project={} v{}",
                                  paths.size(), outerJarPath.getFileName(), project, version);
                          })
                          .flatMapMany(Flux::fromIterable)
                          // Process one nested JAR at a time (ZipFS reads are safe but sequential
                          // is simpler and the bottleneck is bytecode analysis, not I/O).
                          .concatMap(entryPath ->
                              // Step 1: read bytes + compute hash (sync, boundedElastic).
                              Mono.fromCallable(() -> readCandidate(outerFs, entryPath))
                                  .subscribeOn(Schedulers.boundedElastic())
                                  // Step 2: dedup check — reactive, no .block().
                                  .filterWhen(candidate ->
                                      descriptorRepository
                                          .findByProjectAndVersionAndContentHash(
                                              project, version, candidate.hash())
                                          .hasElement()
                                          .map(exists -> {
                                            if (exists) logger.atInfo().log(
                                                "Skipping already-indexed nested JAR '{}' (hash={})",
                                                candidate.jarName(), candidate.hash());
                                            return !exists;
                                          })
                                  )
                                  // Step 3: extract classes (sync, boundedElastic).
                                  .flatMap(candidate ->
                                      Mono.fromCallable(() -> extractFromCandidate(candidate))
                                          .subscribeOn(Schedulers.boundedElastic())
                                  )
                                  // A bad nested JAR must not abort the rest of the upload.
                                  .onErrorResume(e -> {
                                    logger.atWarn().withThrowable(e)
                                        .log("Failed to process nested JAR '{}' — skipping",
                                            entryPath);
                                    return Mono.empty();
                                  })
                          ),
                  outerFs -> {
                    try { outerFs.close(); } catch (IOException ignored) {}
                  }
              )
              .flatMap(Flux::fromIterable)
              .collectList();
        });
  }

  // -------------------------------------------------------------------------
  // Synchronous helpers (all run on boundedElastic)
  // -------------------------------------------------------------------------

  /** Returns the path strings of all nested JAR entries matching known fat-JAR layouts. */
  private List<String> listNestedJarEntries(FileSystem outerFs) throws IOException {
    List<String> result = new ArrayList<>();
    try (var walk = Files.walk(outerFs.getPath("/"))) {
      walk.filter(p -> Files.isRegularFile(p) && isNestedJarPath(p.toString()))
          .map(Path::toString)
          .forEach(result::add);
    }
    return result;
  }

  /** Reads a nested JAR entry into memory and computes its SHA-256 in one pass. */
  private NestedJarCandidate readCandidate(FileSystem outerFs, String entryPath) throws IOException {
    MessageDigest digest = DigestUtils.newSha256();
    Path nestedPath = outerFs.getPath(entryPath);
    String jarName = nestedPath.getFileName().toString();
    byte[] bytes;
    try (DigestInputStream dis = new DigestInputStream(Files.newInputStream(nestedPath), digest)) {
      bytes = dis.readAllBytes();
    }
    String hash = DigestUtils.hexEncode(digest.digest());
    return new NestedJarCandidate(jarName, bytes, hash);
  }

  /**
   * Wraps the candidate's bytes in a {@link ZipInputStream} and delegates to
   * {@link ExtractionService#extractZipStream}.
   */
  private Collection<UnitConstants> extractFromCandidate(NestedJarCandidate candidate)
      throws IOException {
    var descriptor = new UnitDescriptor(
        BytecodeSourceKind.JAR, candidate.jarName(), candidate.size(), candidate.hash());
    try (var zis = new ZipInputStream(new ByteArrayInputStream(candidate.bytes()))) {
      Collection<UnitConstants> result = extractionService.extractZipStream(zis, descriptor);
      logger.atInfo().log("Extracted '{}': {} class unit(s)", candidate.jarName(), result.size());
      return result;
    }
  }

  // -------------------------------------------------------------------------
  // Path classification
  // -------------------------------------------------------------------------

  /**
   * Returns {@code true} for {@code .jar} paths matching recognized fat-JAR layouts.
   * Only one level of nesting is supported.
   */
  private boolean isNestedJarPath(String s) {
    if (!s.endsWith(".jar")) return false;
    return s.startsWith("/BOOT-INF/lib/")
        || s.startsWith("/WEB-INF/lib/")
        || s.matches("/[^/]+\\.jar"); // root-level only
  }
}
