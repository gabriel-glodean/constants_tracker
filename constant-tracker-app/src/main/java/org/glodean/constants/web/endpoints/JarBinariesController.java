package org.glodean.constants.web.endpoints;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.glodean.constants.extractor.bytecode.BytecodeSourceKind;
import org.glodean.constants.model.UnitDescriptor;
import org.glodean.constants.services.NestedJarExtractionService;
import org.glodean.constants.dto.JarExtractionStatusResponse;
import org.glodean.constants.store.JarBatch;
import org.glodean.constants.store.UnitConstantsStore;
import org.glodean.constants.store.postgres.repository.JarExtractionRepository;
import org.glodean.constants.web.validation.ValidProjectName;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import static org.glodean.constants.util.DigestUtils.sha256Hex;

/**
 * REST endpoint for uploading JAR files and extracting all contained unit constants.
 *
 * <p>POST /jar?project=... with application/octet-stream body uploads a JAR file for analysis.
 * The request body is streamed to a temporary file and each buffer is released as it is written,
 * so the full JAR bytes are never held in heap memory during extraction.
 *
 * <p>In addition to extracting class-file constants from the top-level JAR, nested JARs found
 * in {@code /BOOT-INF/lib/}, {@code /WEB-INF/lib/}, or at the root (Maven Shade / Gradle Shadow)
 * are also extracted as separate units. Only one level of nesting is supported.
 *
 * <p>All extraction and storage is handled asynchronously after the JAR is written to disk,
 * so the request returns {@code 202 Accepted} before extraction completes. Storage is performed
 * per-unit (outer JAR classes + each embedded JAR) in separate transactions via
 * {@link org.glodean.constants.store.CompositeUnitConstantsStore#storeAllStreaming}.
 */
@Validated
@RestController
@RequestMapping("/jar")
public class JarBinariesController {
    private static final Logger logger = LogManager.getLogger(JarBinariesController.class);
    private final UnitConstantsStore storage;
    private final NestedJarExtractionService nestedJarExtractionService;
    private final JarExtractionRepository jarExtractionRepository;

    @Autowired
    public JarBinariesController(
            UnitConstantsStore storage,
            NestedJarExtractionService nestedJarExtractionService,
            JarExtractionRepository jarExtractionRepository) {
        this.storage = storage;
        this.nestedJarExtractionService = nestedJarExtractionService;
        this.jarExtractionRepository = jarExtractionRepository;
    }

    /**
     * Upload a JAR file, extract all classes / configs and nested JARs, and store them for the
     * given project.
     *
     * <p>The request body is written to a temporary file. Once the write completes, the descriptor
     * is computed and {@code 202 Accepted} is returned immediately. Extraction and storage continue
     * in a detached background task so proxy timeouts (e.g. Cloudflare's 100 s limit) cannot
     * cancel the server-side work.
     *
     * <p>The outer JAR's class files and config files form one transaction; each embedded JAR
     * forms a separate transaction. All batches are processed by
     * {@link org.glodean.constants.services.NestedJarExtractionService#extractFatJar}.
     *
     * @param jarFile the raw bytes of a .jar file (as reactive stream of buffers)
     * @param project the project identifier
     * @param jarName the name of the JAR file being uploaded
     * @return 202 Accepted once the file is written to disk;
     * 500 Internal Server Error if writing to disk fails
     */
    @PreAuthorize("isAuthenticated()")
    @PostMapping(consumes = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public Mono<ResponseEntity<Object>> storeJar(
            @RequestBody Flux<DataBuffer> jarFile,
            @NotBlank @ValidProjectName @RequestParam("project") String project,
            @NotBlank @RequestParam("jarName") String jarName) {

        return Mono.fromCallable(() -> Files.createTempFile("jar-upload-", ".jar"))
                .flatMap(tmp ->
                        // Step 1: stream body to disk
                        DataBufferUtils.write(jarFile, tmp)
                                // Step 2: compute descriptor (cheap, synchronous)
                                .then(Mono.fromCallable(() -> {
                                    long size = Files.size(tmp);
                                    String hash = sha256(tmp);
                                    return new UnitDescriptor(
                                            BytecodeSourceKind.JAR, jarName.strip(), size, hash);
                                }))
                                .flatMap(outerDescriptor -> {
                                    // Step 3: detach the full extraction + storage pipeline from
                                    // the HTTP request chain. Outer JAR classes form batch 0;
                                    // each nested JAR is its own batch — stored atomically in one
                                    // transaction per batch.
                                    Flux<JarBatch> allBatches =
                                            nestedJarExtractionService.extractFatJar(
                                                    tmp, outerDescriptor, project.strip());
                                    storage.storeAllStreaming(allBatches, project.strip())
                                            .doFinally(_ -> {
                                                try {
                                                    Files.deleteIfExists(tmp);
                                                } catch (IOException ignored) {
                                                }
                                            })
                                            .subscribeOn(Schedulers.boundedElastic())
                                            .subscribe(
                                                    _ -> {
                                                    },
                                                    err -> logger.atError().withThrowable(err)
                                                            .log("Background JAR extraction failed for project={} jar={}",
                                                                    project, jarName)
                                            );
                                    // Return 202 immediately — processing continues in background.
                                    return Mono.just(
                                            ResponseEntity.accepted().build());
                                })
                                // Clean up tmp if body-write or descriptor computation fails
                                .onErrorResume(ex -> {
                                    try {
                                        Files.deleteIfExists(tmp);
                                    } catch (IOException ignored) {
                                    }
                                    return Mono.error(ex);
                                })
                )
                .subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * Lists extraction jobs for a project/version.
     *
     * <p>If {@code jarName} is provided, the response contains at most one matching job.
     */
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/jobs")
    public Mono<ResponseEntity<List<JarExtractionStatusResponse>>> getExtractionJobs(
            @NotBlank @ValidProjectName @RequestParam("project") String project,
            @Positive @RequestParam("version") int version,
            @RequestParam(value = "jarName", required = false) String jarName) {
        Flux<JarExtractionStatusResponse> source =
                (jarName != null && !jarName.isBlank())
                        ? jarExtractionRepository
                                .findByProjectAndVersionAndJarName(project.strip(), version, jarName.strip())
                                .map(JarExtractionStatusResponse::from)
                                .flux()
                        : jarExtractionRepository
                                .findAllByProjectAndVersion(project.strip(), version)
                                .map(JarExtractionStatusResponse::from);

        return source.collectList().map(ResponseEntity::ok);
    }

    private static String sha256(Path path) throws IOException {
        try (InputStream in = Files.newInputStream(path)) {
            return sha256Hex(in);
        }
    }
}
