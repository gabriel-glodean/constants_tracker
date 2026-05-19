package org.glodean.constants.web.endpoints;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import jakarta.validation.constraints.NotBlank;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.glodean.constants.extractor.bytecode.BytecodeSourceKind;
import org.glodean.constants.model.UnitConstants;
import org.glodean.constants.model.UnitDescriptor;
import org.glodean.constants.services.ExtractionService;
import org.glodean.constants.services.NestedJarExtractionService;
import org.glodean.constants.store.UnitConstantsStore;
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
 */
@Validated
@RestController
@RequestMapping("/jar")
public class JarBinariesController {
    private static final Logger logger = LogManager.getLogger(JarBinariesController.class);
    private final UnitConstantsStore storage;
    private final ExtractionService extractionService;
    private final NestedJarExtractionService nestedJarExtractionService;

    @Autowired
    public JarBinariesController(
            UnitConstantsStore storage,
            ExtractionService extractionService,
            NestedJarExtractionService nestedJarExtractionService) {
        this.storage = storage;
        this.extractionService = extractionService;
        this.nestedJarExtractionService = nestedJarExtractionService;
    }

    /**
     * Upload a JAR file, extract all classes and nested JARs, and store them for the given project.
     *
     * <p>The request body is written to a temporary file synchronously; the outer JAR constants are
     * then extracted. As soon as that completes the response is sent back as {@code 202 Accepted}
     * and the slow nested-JAR extraction + storage continues in a detached background task, so
     * proxy timeouts (e.g. Cloudflare's 100 s limit) cannot cancel the server-side work.
     *
     * @param jarFile the raw bytes of a .jar file (as reactive stream of buffers)
     * @param project the project identifier
     * @param jarName the name of the JAR file being uploaded
     * @return 202 Accepted immediately after outer extraction / version creation,
     *         422 Unprocessable Entity if the JAR is invalid,
     *         500 Internal Server Error for other failures
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
                                // Step 2: compute descriptor + extract outer JAR classes (~seconds)
                                .then(Mono.fromCallable(() -> {
                                    long size = Files.size(tmp);
                                    String hash = sha256(tmp);
                                    var descriptor = new UnitDescriptor(
                                            BytecodeSourceKind.JAR, jarName.strip(), size, hash);
                                    return extractionService.extractJarFile(tmp, descriptor);
                                }))
                                .flatMap(outerUnits -> {
                                    // Step 3: detach nested-JAR extraction + storage from the HTTP
                                    // request chain so proxy timeouts cannot cancel the work.
                                    nestedJarExtractionService
                                            .extractNestedJars(tmp, project.strip())
                                            .map(nestedUnits -> {
                                                List<UnitConstants> all = new ArrayList<>(outerUnits);
                                                all.addAll(nestedUnits);
                                                return all;
                                            })
                                            .flatMap(allUnits -> storage.storeAll(allUnits, project.strip()))
                                            .doFinally(_ -> {
                                                try {
                                                    Files.deleteIfExists(tmp);
                                                } catch (IOException ignored) {}
                                            })
                                            .subscribeOn(Schedulers.boundedElastic())
                                            .subscribe(
                                                    _ -> {},
                                                    err -> logger.atError().withThrowable(err)
                                                            .log("Background JAR extraction failed for project={} jar={}",
                                                                    project, jarName)
                                            );
                                    // Return 202 immediately — processing continues in the background.
                                    return Mono.just(
                                            ResponseEntity.accepted().build());
                                })
                                // Clean up tmp if body-write or outer extraction fails
                                .onErrorResume(ex -> {
                                    try { Files.deleteIfExists(tmp); } catch (IOException ignored) {}
                                    return Mono.error(ex);
                                })
                )
                .subscribeOn(Schedulers.boundedElastic());
    }

    private static String sha256(Path path) throws IOException {
        try (InputStream in = Files.newInputStream(path)) {
            return sha256Hex(in);
        }
    }
}
