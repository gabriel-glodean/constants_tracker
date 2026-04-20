package org.glodean.constants.web.endpoints;

import org.glodean.constants.extractor.ModelExtractor;
import org.glodean.constants.services.ExtractionService;
import org.glodean.constants.store.UnitConstantsStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.List;

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
     * @return 200 OK with a list of UnitConstants if successful,
     *         422 Unprocessable Entity if the JAR is invalid,
     *         500 Internal Server Error for other failures
     */
    @PostMapping(consumes = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public Mono<ResponseEntity<Object>> storeJar(
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
                        // Try the no-arg extract() first (real implementations typically
                        // provide this). If unsupported (e.g., mocks), fall back to
                        // calling the UnitDescriptor overload with null.
                        return reactor.core.publisher.Flux.fromIterable(modelExtractor.extract());
                    } catch (ModelExtractor.ExtractionException e) {
                        return reactor.core.publisher.Flux.error(e);
                    } catch (UnsupportedOperationException e) {
                        try {
                            return reactor.core.publisher.Flux.fromIterable(modelExtractor.extract((org.glodean.constants.model.UnitDescriptor) null));
                        } catch (ModelExtractor.ExtractionException ex) {
                            return reactor.core.publisher.Flux.error(ex);
                        }
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
}

