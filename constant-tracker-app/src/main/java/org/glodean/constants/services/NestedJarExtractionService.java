package org.glodean.constants.services;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.zip.ZipInputStream;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.glodean.constants.extractor.bytecode.BytecodeSourceKind;
import org.glodean.constants.model.UnitConstants;
import org.glodean.constants.model.UnitDescriptor;
import org.glodean.constants.store.JarBatch;
import org.glodean.constants.store.postgres.entity.JarExtractionEntity;
import org.glodean.constants.store.postgres.repository.JarExtractionRepository;
import org.glodean.constants.store.postgres.repository.UnitDescriptorRepository;
import org.glodean.constants.util.DigestUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * Extracts constants from nested JARs found inside an uploaded fat JAR.
 *
 * <p>Each nested JAR (and the outer JAR itself) is represented as a single
 * {@link UnitDescriptor} with {@code source_kind = JAR}.  The class files
 * extracted from that JAR become {@link org.glodean.constants.store.postgres.entity.UnitSnapshotEntity}
 * rows under that descriptor, producing a proper M:1 hierarchy instead of the
 * old 1:1 redundancy.
 *
 * <p>The pipeline emits {@link JarBatch} elements.  Each batch carries the JAR
 * descriptor plus a chunk of at most {@code batchSize} class/config files.
 * {@code firstBatch = true} on the first chunk for a given container so the
 * storage layer knows when to clear stale snapshots.
 */
@Service
public class NestedJarExtractionService {

    private static final Logger logger = LogManager.getLogger(NestedJarExtractionService.class);

    /**
     * Intermediate value object carrying a nested JAR's identity before extraction.
     */
    private record NestedJarCandidate(String jarName, byte[] bytes, String hash) {
        long size() {
            return bytes.length;
        }
    }

    /**
     * Pairs the JAR-level descriptor with the class files extracted from it.
     */
    private record ExtractionResult(UnitDescriptor descriptor, Collection<UnitConstants> units) {
    }

    private final ExtractionService extractionService;
    private final UnitDescriptorRepository descriptorRepository;
    private final ProjectVersionService projectVersionService;
    private final JarExtractionRepository jarExtractionRepository;
    private final int batchSize;

    @Autowired
    public NestedJarExtractionService(
            ExtractionService extractionService,
            UnitDescriptorRepository descriptorRepository,
            ProjectVersionService projectVersionService,
            JarExtractionRepository jarExtractionRepository,
            @Value("${constants.jar.batch-size:500}") int batchSize) {
        this.extractionService = extractionService;
        this.descriptorRepository = descriptorRepository;
        this.projectVersionService = projectVersionService;
        this.jarExtractionRepository = jarExtractionRepository;
        this.batchSize = batchSize;
    }

    /**
     * Full fat-JAR extraction.
     *
     * <p>The outer JAR's class files and config files form the first batch(es) under
     * {@code outerDescriptor}; each embedded JAR produces its own batch(es) under its
     * own JAR descriptor.  The version is resolved once so all batches land in the same
     * project version.
     *
     * <p>Each emitted {@link JarBatch} should be stored atomically in its own transaction
     * by the caller (e.g. via
     * {@link org.glodean.constants.store.CompositeUnitConstantsStore#storeAllStreaming}).
     *
     * @param outerJarPath    path to the fat JAR file on disk
     * @param outerDescriptor descriptor for the fat JAR as a whole (name, size, hash)
     * @param project         project identifier
     * @return a {@link Flux} where early elements are the outer JAR's chunks and later
     * elements are chunks from each embedded JAR
     */
    public Flux<JarBatch> extractFatJar(
            Path outerJarPath, UnitDescriptor outerDescriptor, String project) {
        return projectVersionService
                .getOrCreateOpenVersion(project)
                .flatMapMany(versionEntity -> {
                    int version = versionEntity.version();
                    String jarName = outerDescriptor.path();

                    // Outer JAR: streaming by chunk; first chunk sets firstBatch=true.
                    Flux<JarBatch> outerBatches =
                            extractionService.extractJarFileStreaming(outerJarPath, outerDescriptor, batchSize)
                                    .index()
                                    .map(t -> new JarBatch(outerDescriptor, t.getT2(), t.getT1() == 0))
                                    .doOnComplete(() -> logger.atInfo().log(
                                            "Finished streaming outer JAR {} for project={} v{}",
                                            outerJarPath.getFileName(), project, version));

                    // Nested JARs: each JAR produces one or more JarBatch elements.
                    Flux<JarBatch> nestedBatches =
                            extractNestedJarsWithVersion(outerJarPath, jarName, project, version);

                    Flux<JarBatch> allBatches = Flux.concat(outerBatches, nestedBatches);

                    return createStartedTracking(project, version, jarName)
                            .thenMany(allBatches)
                            .concatWith(
                                    updateTrackingStatus(project, version, jarName,
                                            JarExtractionEntity.STATUS_COMPLETED, null)
                                            .thenMany(Flux.empty())
                            )
                            .onErrorResume(e ->
                                    updateTrackingStatus(project, version, jarName,
                                            JarExtractionEntity.STATUS_FAILED, e.getMessage())
                                            .then(Mono.error(e))
                            );
                });
    }

    /**
     * Opens {@code outerJarPath} as a ZipFileSystem and extracts constants from each new
     * nested JAR, emitting {@link JarBatch} elements.
     *
     * @param outerJarPath path to the outer JAR file on disk
     * @param project      project identifier
     * @return a {@link Flux} emitting one or more {@link JarBatch}es per nested JAR
     */
    public Flux<JarBatch> extractNestedJars(Path outerJarPath, String project) {
        return projectVersionService
                .getOrCreateOpenVersion(project)
                .flatMapMany(versionEntity ->
                        extractNestedJarsWithVersion(outerJarPath, null, project, versionEntity.version()));
    }

    /**
     * Core nested-JAR extraction logic, given an already-resolved {@code version}.
     *
     * @param trackingJarName the jar_name used in the fat_jar_extractions row, or {@code null}
     *                        when called standalone (no tracking row exists)
     */
    private Flux<JarBatch> extractNestedJarsWithVersion(
            Path outerJarPath, String trackingJarName, String project, int version) {
        var jarName = trackingJarName != null ? trackingJarName : outerJarPath.getFileName().toString();
        return Flux.using(
                () -> FileSystems.newFileSystem(outerJarPath, (ClassLoader) null),
                outerFs ->
                        Mono.fromCallable(() -> listNestedJarEntries(outerFs))
                                .subscribeOn(Schedulers.boundedElastic())
                                .flatMap(paths -> {
                                    if (paths.isEmpty()) {
                                        logger.atDebug().log("No nested JARs found in {}", jarName);
                                        return Mono.just(paths);
                                    }
                                    logger.atInfo().log(
                                            "Found {} nested JAR(s) in {} — extracting for project={} v{}",
                                            paths.size(), jarName, project, version);
                                    if (trackingJarName == null) {
                                        return Mono.just(paths);
                                    }
                                    return updateNestedTotal(project, version, jarName, paths.size())
                                            .thenReturn(paths);
                                })
                                .flatMapMany(Flux::fromIterable)
                                .concatMap(entryPath -> {
                                    Flux<JarBatch> perJarBatches =
                                            Mono.fromCallable(() -> readCandidate(outerFs, entryPath))
                                                    .subscribeOn(Schedulers.boundedElastic())
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
                                                    .flatMapMany(candidate ->
                                                            Mono.fromCallable(() -> extractFromCandidate(candidate))
                                                                    .subscribeOn(Schedulers.boundedElastic())
                                                                    .flatMapMany(result ->
                                                                            // Split into batchSize chunks; tag first chunk.
                                                                            Flux.fromIterable(new ArrayList<>(result.units()))
                                                                                    .buffer(batchSize)
                                                                                    .index()
                                                                                    .map(t -> new JarBatch(
                                                                                            result.descriptor(), t.getT2(), t.getT1() == 0))
                                                                    )
                                                    );
                                    return perJarBatches
                                            .concatWith(
                                                    (trackingJarName != null
                                                            ? incrementNestedProcessed(project, version, jarName)
                                                            : Mono.empty())
                                                            .thenMany(Flux.empty())
                                            )
                                            .onErrorResume(e -> {
                                                logger.atWarn().withThrowable(e)
                                                        .log("Failed to process nested JAR '{}' — skipping", entryPath);
                                                return (trackingJarName != null
                                                        ? incrementNestedFailed(project, version, jarName)
                                                        : Mono.empty())
                                                        .thenMany(Flux.empty());
                                            });
                                }),
                outerFs -> {
                    try {
                        outerFs.close();
                    } catch (IOException ignored) {
                    }
                }
        );
    }

    private Mono<Void> updateNestedTotal(String project, int version, String jarName, int total) {
        return jarExtractionRepository
                .findFirstByProjectAndVersionAndJarNameOrderByIdDesc(project, version, jarName)
                .flatMap(existing -> jarExtractionRepository.save(
                        existing.toBuilder()
                                .nestedTotal(total)
                                .lastUpdatedAt(OffsetDateTime.now())
                                .build()
                ))
                .then();
    }

    private Mono<Void> incrementNestedProcessed(String project, int version, String jarName) {
        return jarExtractionRepository
                .findFirstByProjectAndVersionAndJarNameOrderByIdDesc(project, version, jarName)
                .flatMap(existing -> jarExtractionRepository.save(
                        existing.toBuilder()
                                .nestedProcessed(existing.nestedProcessed() + 1)
                                .lastUpdatedAt(OffsetDateTime.now())
                                .build()
                ))
                .then();
    }

    private Mono<Void> incrementNestedFailed(String project, int version, String jarName) {
        return jarExtractionRepository
                .findFirstByProjectAndVersionAndJarNameOrderByIdDesc(project, version, jarName)
                .flatMap(existing -> jarExtractionRepository.save(
                        existing.toBuilder()
                                .nestedFailed(existing.nestedFailed() + 1)
                                .lastUpdatedAt(OffsetDateTime.now())
                                .build()
                ))
                .then();
    }

    // -------------------------------------------------------------------------
    // Synchronous helpers (all run on boundedElastic)
    // -------------------------------------------------------------------------

    private List<String> listNestedJarEntries(FileSystem outerFs) throws IOException {
        List<String> result = new ArrayList<>();
        try (var walk = Files.walk(outerFs.getPath("/"))) {
            walk.filter(p -> Files.isRegularFile(p) && isNestedJarPath(p.toString()))
                    .map(Path::toString)
                    .forEach(result::add);
        }
        return result;
    }

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

    private ExtractionResult extractFromCandidate(NestedJarCandidate candidate) throws IOException {
        var descriptor = new UnitDescriptor(
                BytecodeSourceKind.JAR, candidate.jarName(), candidate.size(), candidate.hash());
        try (var zis = new ZipInputStream(new ByteArrayInputStream(candidate.bytes()))) {
            Collection<UnitConstants> result = extractionService.extractZipStream(zis, descriptor);
            logger.atInfo().log("Extracted '{}': {} class unit(s)", candidate.jarName(), result.size());
            return new ExtractionResult(descriptor, result);
        }
    }

    // -------------------------------------------------------------------------
    // Path classification
    // -------------------------------------------------------------------------

    private boolean isNestedJarPath(String s) {
        if (!s.endsWith(".jar")) return false;
        return s.startsWith("/BOOT-INF/lib/")
                || s.startsWith("/WEB-INF/lib/")
                || s.matches("/[^/]+\\.jar");
    }

    private Mono<JarExtractionEntity> createStartedTracking(
            String project, int version, String jarName) {
        return jarExtractionRepository.save(JarExtractionEntity.started(project, version, jarName));
    }

    private Mono<JarExtractionEntity> updateTrackingStatus(
            String project, int version, String jarName, String status, String errorMessage) {
        return jarExtractionRepository
                .findFirstByProjectAndVersionAndJarNameOrderByIdDesc(project, version, jarName)
                .flatMap(existing -> jarExtractionRepository.save(
                        existing.toBuilder().status(status).lastUpdatedAt(OffsetDateTime.now()).errorMessage(errorMessage).build()
                ));
    }
}
