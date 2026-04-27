package org.glodean.constants.web.endpoints;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.glodean.constants.extractor.ModelExtractor;
import org.glodean.constants.extractor.bytecode.BytecodeSourceKind;
import org.glodean.constants.model.UnitDescriptor;
import org.glodean.constants.services.ExtractionService;
import org.glodean.constants.store.UnitConstantsStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;


/**
 * REST endpoint for uploading JAR files and extracting all contained unit constants.
 *
 * <p>POST /jar?project=... with application/octet-stream body uploads a JAR file for analysis.
 */
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
     * @param jarFile the raw bytes of a .jar file (as reactive stream)
     * @param project the project identifier
     * @param jarName the name of the JAR file being uploaded
     * @return 200 OK with a list of UnitConstants if successful,
     *         422 Unprocessable Entity if the JAR is invalid,
     *         500 Internal Server Error for other failures
     */
    @PostMapping(consumes = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public Mono<ResponseEntity<Object>> storeJar(
            @RequestBody Mono<DataBuffer> jarFile,
            @RequestParam("project") String project,
            @RequestParam("jarName") String jarName) {
        return jarFile
                .map(dataBuffer -> {
                    byte[] bytes = new byte[dataBuffer.readableByteCount()];
                    dataBuffer.read(bytes);
                    DataBufferUtils.release(dataBuffer);
                    return bytes;
                })
                .flatMapMany(bytes -> {
                    var descriptor = new UnitDescriptor(
                            BytecodeSourceKind.JAR, jarName, bytes.length, sha256(bytes));
                    var modelExtractor = extractionService.extractorForJarFile(bytes);
                    try {
                        return reactor.core.publisher.Flux.fromIterable(modelExtractor.extract(descriptor));
                    } catch (ModelExtractor.ExtractionException e) {
                        return reactor.core.publisher.Flux.error(e);
                    }
                })
                .collectList()
                .flatMap(allUnits -> storage.storeAll(allUnits, project))
                .thenReturn(ResponseEntity.ok().build())
                .onErrorResume(
                        ModelExtractor.ExtractionException.class,
                        _ -> Mono.just(ResponseEntity.unprocessableEntity().build()))
                .onErrorResume(
                        IllegalArgumentException.class,
                        _ -> Mono.just(ResponseEntity.unprocessableEntity().build()))
                .onErrorResume(
                        Exception.class,
                        _ -> Mono.just(ResponseEntity.internalServerError().build()));
    }

    private static String sha256(byte[] bytes) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(bytes);
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
