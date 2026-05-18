package org.glodean.constants.web.endpoints;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import jakarta.validation.constraints.NotBlank;
import org.glodean.constants.extractor.bytecode.BytecodeSourceKind;
import org.glodean.constants.model.UnitDescriptor;
import org.glodean.constants.services.ExtractionService;
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

/**
 * REST endpoint for uploading JAR files and extracting all contained unit constants.
 *
 * <p>POST /jar?project=... with application/octet-stream body uploads a JAR file for analysis.
 * The request body is streamed to a temporary file and each buffer is released as it is written,
 * so the full JAR bytes are never held in heap memory during extraction.
 */
@Validated
@RestController
@RequestMapping("/jar")
public class JarBinariesController {
    private final UnitConstantsStore storage;
    private final ExtractionService extractionService;

    @Autowired
    public JarBinariesController(UnitConstantsStore storage, ExtractionService extractionService) {
        this.storage = storage;
        this.extractionService = extractionService;
    }

    /**
     * Upload a JAR file, extract all classes, and store them for the given project.
     *
     * @param jarFile the raw bytes of a .jar file (as reactive stream of buffers)
     * @param project the project identifier
     * @param jarName the name of the JAR file being uploaded
     * @return 200 OK if successful,
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
                        // Step 1: stream body to disk — each DataBuffer is released after writing
                        DataBufferUtils.write(jarFile, tmp)
                                // Step 2: compute descriptor, extract via ZipFileSystem, clean up
                                .then(Mono.fromCallable(() -> {
                                    try {
                                        long size = Files.size(tmp);
                                        String hash = sha256(tmp);
                                        var descriptor = new UnitDescriptor(
                                                BytecodeSourceKind.JAR, jarName.strip(), size, hash);
                                        return extractionService.extractJarFile(tmp, descriptor);
                                    } finally {
                                        Files.deleteIfExists(tmp);
                                    }
                                }))
                                .onErrorResume(e -> {
                                    try { Files.deleteIfExists(tmp); } catch (IOException ignored) {}
                                    return Mono.error(e);
                                })
                )
                .subscribeOn(Schedulers.boundedElastic())
                .flatMapMany(Flux::fromIterable)
                .collectList()
                .flatMap(allUnits -> storage.storeAll(allUnits, project.strip()))
                .thenReturn(ResponseEntity.ok().build());
    }

    private static String sha256(Path path) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream in = new DigestInputStream(Files.newInputStream(path), digest)) {
                in.transferTo(OutputStream.nullOutputStream());
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        } catch (IOException e) {
            throw new IllegalArgumentException("Could not read JAR for hashing", e);
        }
    }
}
