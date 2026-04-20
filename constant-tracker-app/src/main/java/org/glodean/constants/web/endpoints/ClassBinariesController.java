package org.glodean.constants.web.endpoints;

import com.google.common.collect.Iterables;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.glodean.constants.dto.GetUnitConstantsReply;
import org.glodean.constants.extractor.ModelExtractor;
import org.glodean.constants.model.UnitConstants;
import org.glodean.constants.extractor.bytecode.BytecodeSourceKind;
import org.glodean.constants.model.UnitDescriptor;
import org.glodean.constants.services.ExtractionService;
import org.glodean.constants.services.ProjectVersionService;
import org.glodean.constants.store.UnitConstantsStore;
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
        @Autowired UnitConstantsStore storage,
        @Autowired ExtractionService extractionService,
        @Autowired ProjectVersionService projectVersionService) {

    /**
     * Store a class file for a specific project and version.
     *
     * <p>Use this endpoint when you need explicit version control (e.g., analyzing different
     * JDK versions with known version numbers).
     *
     * @param javaClass the raw bytes of a .class file (as reactive stream)
     * @param project   the project identifier (e.g., "jdk", "spring-boot")
     * @param version   the explicit version number (e.g., 8, 11, 17)
     * @return 200 OK with {@link org.glodean.constants.model.UnitConstants} if successful,
     * 422 Unprocessable Entity if bytecode is invalid,
     * 500 Internal Server Error for other failures
     */
    @PutMapping(consumes = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public Mono<ResponseEntity<Object>> storeClass(
            @RequestBody Mono<DataBuffer> javaClass,
            @RequestParam("project") String project,
            @RequestParam("version") int version) {
        return modelMono(javaClass)
                .flatMap(classConstants -> storage.store(classConstants, project, version)
                        .thenReturn(ResponseEntity.ok().build()))
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
     * @return 200 OK with {@link org.glodean.constants.model.UnitConstants} if successful,
     * 422 Unprocessable Entity if bytecode is invalid,
     * 500 Internal Server Error for other failures
     */
    @PostMapping(consumes = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public Mono<ResponseEntity<Object>> storeClass(
            @RequestBody Mono<DataBuffer> javaClass, @RequestParam("project") String project) {
        return modelMono(javaClass)
                .flatMap(classConstants -> storage.store(classConstants, project)
                        .thenReturn(ResponseEntity.ok().build()))
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
     * @return a {@link Mono} emitting the sole {@link org.glodean.constants.model.UnitConstants} from the class file;
     *         errors with {@link org.glodean.constants.extractor.ModelExtractor.ExtractionException}
     *         on malformed bytecode
     */
    private Mono<UnitConstants> modelMono(Mono<DataBuffer> javaClass) {
        return javaClass
                .map(
                        dataBuffer -> {
                            byte[] bytes = new byte[dataBuffer.readableByteCount()];
                            dataBuffer.read(bytes);
                            DataBufferUtils.release(dataBuffer);
                            return bytes;
                        })
                .flatMap(
                        bytes -> {
                            var modelExtractor = extractionService.extractorForClassFile(bytes);
                            try {
                                var descriptor = new UnitDescriptor(
                                        BytecodeSourceKind.CLASS_FILE, "uploaded-class",
                                        bytes.length, sha256(bytes));
                                return Mono.just(modelExtractor.extract(descriptor));
                            } catch (ModelExtractor.ExtractionException e) {
                                return Mono.error(e);
                            }
                        })
                .map(Iterables::getOnlyElement);
    }

    private static String sha256(byte[] bytes) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(bytes);
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    /**
     * Lookup constants for a class using query parameters (GET /class?project=...&className=...&version=...).
     *
     * @param project the project identifier
     * @param className the class internal or binary name (slash-separated, e.g., "java/lang/String")
     * @param version the project version number
     * @return 200 OK with a {@link GetUnitConstantsReply} if the class is found,
     *         404 Not Found if no matching snapshot exists,
     *         500 Internal Server Error for other failures
     */
    @GetMapping
    public Mono<ResponseEntity<GetUnitConstantsReply>> classConstants(
            @RequestParam("project") String project,
            @RequestParam("className") String className,
            @RequestParam("version") int version) {
        String key = project + ":" + className + ":" + version;
        return storage.find(key)
                .map(GetUnitConstantsReply::new)
                .map(ResponseEntity::ok)
                .onErrorResume(
                        IllegalArgumentException.class, _ -> Mono.just(ResponseEntity.notFound().build()))
                .onErrorResume(
                        Exception.class, _ -> Mono.just(ResponseEntity.internalServerError().build()));
    }

    /**
     * Explicitly remove a unit from a project version, preventing it from being inherited
     * from a parent version.
     *
     * @param project   the project identifier
     * @param className the unit path (slash-separated, e.g., "java/lang/String")
     * @param version   the version number
     * @return 204 No Content on success, 409 Conflict if the version is finalized,
     *         500 Internal Server Error for other failures
     */
    @DeleteMapping
    public Mono<ResponseEntity<Object>> deleteUnit(
            @RequestParam("project") String project,
            @RequestParam("className") String className,
            @RequestParam("version") int version) {
        return projectVersionService.deleteUnit(project, version, className)
                .thenReturn(ResponseEntity.noContent().<Object>build())
                .onErrorResume(
                        IllegalStateException.class,
                        _ -> Mono.just(ResponseEntity.status(409).build()))
                .onErrorResume(
                        Exception.class, _ -> Mono.just(ResponseEntity.internalServerError().build()));
    }
}
