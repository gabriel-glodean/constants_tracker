package org.glodean.constants.web.endpoints;

import com.google.common.collect.Iterables;
import org.glodean.constants.dto.GetClassConstantsReply;
import org.glodean.constants.dto.GetClassConstantsRequest;
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

/**
 * HTTP endpoints that accept class/jar binaries and persist discovered constants into a storage
 * backend.
 *
 * <p>This REST controller provides the primary API for uploading Java bytecode for analysis.
 * It supports:
 * <ul>
 *   <li><b>Single class upload:</b> POST/PUT binary .class files</li>
 *   <li><b>JAR file upload:</b> POST binary .jar files (analyzes all contained classes)</li>
 *   <li><b>Version control:</b> Explicit version (PUT) or auto-increment (POST)</li>
 *   <li><b>Search:</b> Query stored constants by class name</li>
 * </ul>
 *
 * <p><b>API Examples:</b>
 * <pre>
 * # Upload class with explicit version
 * curl -X PUT "http://localhost:8080/class?project=jdk&version=8" \
 *      -H "Content-Type: application/octet-stream" \
 *      --data-binary @String.class
 *
 * # Upload class with auto-versioning
 * curl -X POST "http://localhost:8080/class?project=my-app" \
 *      -H "Content-Type: application/octet-stream" \
 *      --data-binary @MyClass.class
 *
 * # Upload JAR file
 * curl -X POST "http://localhost:8080/jar?project=spring-boot" \
 *      -H "Content-Type: application/octet-stream" \
 *      --data-binary @app.jar
 *
 * # Search for constants
 * curl -X POST "http://localhost:8080/class/search" \
 *      -H "Content-Type: application/json" \
 *      -d '{"key": "java/lang/String"}'
 * </pre>
 *
 * @param storage           the backing store for persisting analysis results
 * @param extractionService service for creating bytecode extractors
 */
@RestController
@RequestMapping("/class")
public record ClassBinariesController(
        @Autowired ClassConstantsStore storage, @Autowired ExtractionService extractionService) {

    /**
     * Store a class file for a specific project and version.
     *
     * <p>Use this endpoint when you need explicit version control (e.g., analyzing different
     * JDK versions with known version numbers).
     *
     * @param javaClass the raw bytes of a .class file (as reactive stream)
     * @param project   the project identifier (e.g., "jdk", "spring-boot")
     * @param version   the explicit version number (e.g., 8, 11, 17)
     * @return 200 OK with {@link ClassConstants} if successful,
     * 422 Unprocessable Entity if bytecode is invalid,
     * 500 Internal Server Error for other failures
     */
    @PutMapping(consumes = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public Mono<ResponseEntity<ClassConstants>> storeClass(
            @RequestBody Mono<DataBuffer> javaClass,
            @RequestParam("project") String project,
            @RequestParam("version") int version) {
        return modelMono(javaClass)
                .flatMap(classConstants -> storage.store(classConstants, project, version))
                .map(ResponseEntity::ok)
                .onErrorResume(
                        ModelExtractor.ExtractionException.class,
                        _ -> Mono.just(ResponseEntity.unprocessableEntity().build()))
                .onErrorResume(
                        IllegalArgumentException.class,
                        _ -> Mono.just(ResponseEntity.unprocessableEntity().build()))
                .onErrorResume(
                        Exception.class, _ -> Mono.just(ResponseEntity.internalServerError().build()));
    }

    /**
     * Store a class file for a project (version assigned automatically).
     *
     * <p>Use this endpoint for incremental uploads where you want the system to track
     * versions automatically. The version is assigned by the configured {@link
     * org.glodean.constants.store.VersionIncrementer}.
     *
     * @param javaClass the raw bytes of a .class file (as reactive stream)
     * @param project   the project identifier
     * @return 200 OK with {@link ClassConstants} if successful,
     * 422 Unprocessable Entity if bytecode is invalid,
     * 500 Internal Server Error for other failures
     */
    @PostMapping(consumes = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public Mono<ResponseEntity<ClassConstants>> storeClass(
            @RequestBody Mono<DataBuffer> javaClass, @RequestParam("project") String project) {
        return modelMono(javaClass)
                .flatMap(classConstants -> storage.store(classConstants, project))
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

    /**
     * Reads all bytes from the reactive {@code DataBuffer}, extracts a single-class binary,
     * and runs the bytecode extractor.
     *
     * @param javaClass the reactive stream carrying the raw class-file bytes
     * @return a {@link Mono} emitting the sole {@link ClassConstants} from the class file;
     *         errors with {@link org.glodean.constants.extractor.ModelExtractor.ExtractionException}
     *         on malformed bytecode
     */
    private Mono<ClassConstants> modelMono(Mono<DataBuffer> javaClass) {
        return javaClass
                .map(
                        dataBuffer -> {
                            byte[] bytes = new byte[dataBuffer.readableByteCount()];
                            dataBuffer.read(bytes);
                            DataBufferUtils.release(dataBuffer);
                            return bytes;
                        })
                .map(extractionService::extractorForClassFile)
                .flatMap(
                        modelExtractor -> {
                            try {
                                return Mono.just(modelExtractor.extract());
                            } catch (ModelExtractor.ExtractionException e) {
                                return Mono.error(e);
                            }
                        })
                .map(Iterables::getOnlyElement);
    }

    /**
     * Lookup constants for a class using query parameters (GET /class?project=...&className=...&version=...).
     *
     * @param project the project identifier
     * @param className the class internal or binary name (slash-separated, e.g., "java/lang/String")
     * @param version the project version number
     * @return 200 OK with a {@link GetClassConstantsReply} if the class is found,
     *         404 Not Found if no matching snapshot exists,
     *         500 Internal Server Error for other failures
     */
    @GetMapping
    public Mono<ResponseEntity<GetClassConstantsReply>> classConstants(
            @RequestParam("project") String project,
            @RequestParam("className") String className,
            @RequestParam("version") int version) {
        String key = project + ":" + className + ":" + version;
        return storage.find(key)
                .map(GetClassConstantsReply::new)
                .map(ResponseEntity::ok)
                .onErrorResume(
                        IllegalArgumentException.class, _ -> Mono.just(ResponseEntity.notFound().build()))
                .onErrorResume(
                        Exception.class, _ -> Mono.just(ResponseEntity.internalServerError().build()));
    }
}
