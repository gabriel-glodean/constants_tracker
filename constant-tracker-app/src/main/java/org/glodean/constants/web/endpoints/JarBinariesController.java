package org.glodean.constants.web.endpoints;

import org.glodean.constants.extractor.ModelExtractor;
import org.glodean.constants.model.ClassConstants;
import org.glodean.constants.services.ExtractionService;
import org.glodean.constants.store.ClassConstantsStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * REST endpoint for uploading JAR files and extracting all contained class constants.
 *
 * <p>POST /jar?project=... with application/octet-stream body uploads a JAR file for analysis.
 */
@RestController
@RequestMapping("/jar")
public class JarBinariesController {
    private final ClassConstantsStore storage;
    private final ExtractionService extractionService;

    @Autowired
    public JarBinariesController(ClassConstantsStore storage, ExtractionService extractionService) {
        this.storage = storage;
        this.extractionService = extractionService;
    }

    /**
     * Upload a JAR file, extract all classes, and store them for the given project.
     *
     * @param jarFile the raw bytes of a .jar file (as reactive stream)
     * @param project the project identifier
     * @return 200 OK with a list of ClassConstants if successful,
     *         422 Unprocessable Entity if the JAR is invalid,
     *         500 Internal Server Error for other failures
     */
    @PostMapping(consumes = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public Mono<ResponseEntity<List<ClassConstants>>> storeJar(
            @RequestBody Mono<DataBuffer> jarFile, @RequestParam("project") String project) {
        return jarFile
                .map(dataBuffer -> {
                    byte[] bytes = new byte[dataBuffer.readableByteCount()];
                    dataBuffer.read(bytes);
                    DataBufferUtils.release(dataBuffer);
                    return bytes;
                })
                .map(extractionService::extractorForJarFile)
                .flatMapMany(modelExtractor -> {
                    try {
                        return reactor.core.publisher.Flux.fromIterable(modelExtractor.extract());
                    } catch (ModelExtractor.ExtractionException e) {
                        return reactor.core.publisher.Flux.error(e);
                    }
                })
                .flatMap(classConstants -> storage.store(classConstants, project))
                .collectList()
                .map(ResponseEntity::ok)
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
}

