package org.glodean.constants.web.endpoints;

import java.nio.file.Files;
import java.nio.file.Path;
import org.glodean.constants.extractor.configfile.PropertiesConstantsExtractor;
import org.glodean.constants.extractor.configfile.YamlConstantsExtractor;
import org.glodean.constants.model.UnitConstants;
import org.glodean.constants.store.UnitConstantsStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * REST endpoint for uploading YAML and properties configuration files and extracting constants.
 *
 * <p>Accepts multipart file uploads, writes them to a temp file, runs the appropriate
 * {@link org.glodean.constants.extractor.ConstantsExtractor}, and persists the results.
 *
 * <p><b>API Examples:</b>
 * <pre>
 * # Upload a YAML config
 * curl -X POST "http://localhost:8080/config?project=my-app" \
 *      -F "file=@application.yml"
 *
 * # Upload a properties file with explicit version
 * curl -X PUT "http://localhost:8080/config?project=my-app&amp;version=3" \
 *      -F "file=@application.properties"
 * </pre>
 *
 * @see YamlConstantsExtractor
 * @see PropertiesConstantsExtractor
 */
@RestController
@RequestMapping("/config")
public class ConfigFileController {

    private final UnitConstantsStore storage;
    private final YamlConstantsExtractor yamlExtractor;
    private final PropertiesConstantsExtractor propertiesExtractor;

    @Autowired
    public ConfigFileController(
            UnitConstantsStore storage,
            YamlConstantsExtractor yamlExtractor,
            PropertiesConstantsExtractor propertiesExtractor) {
        this.storage = storage;
        this.yamlExtractor = yamlExtractor;
        this.propertiesExtractor = propertiesExtractor;
    }

    /**
     * Upload a config file and store extracted constants with an explicit version.
     *
     * @param filePart the uploaded config file (YAML or properties)
     * @param project  the project identifier
     * @param version  the explicit version number
     * @return 200 OK if successful, 422 if the file type is unsupported, 500 on error
     */
    @PreAuthorize("isAuthenticated()")
    @PutMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Mono<ResponseEntity<Object>> storeConfigVersioned(
            @RequestPart("file") Mono<FilePart> filePart,
            @RequestParam("project") String project,
            @RequestParam("version") int version) {
        return extractFromUpload(filePart)
                .flatMap(unit -> storage.store(unit, project, version).thenReturn(ResponseEntity.ok().build()));
    }

    /**
     * Upload a config file and store extracted constants with auto-incrementing version.
     *
     * @param filePart the uploaded config file (YAML or properties)
     * @param project  the project identifier
     * @return 200 OK if successful, 422 if the file type is unsupported, 500 on error
     */
    @PreAuthorize("isAuthenticated()")
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Mono<ResponseEntity<Object>> storeConfig(
            @RequestPart("file") Mono<FilePart> filePart,
            @RequestParam("project") String project) {
        return extractFromUpload(filePart)
                .flatMap(unit -> storage.store(unit, project).thenReturn(ResponseEntity.ok().build()));
    }

    /**
     * Transfers the uploaded file to a temp path, selects the right extractor, and returns
     * the first (and typically only) {@link UnitConstants} result.
     */
    private Mono<UnitConstants> extractFromUpload(Mono<FilePart> filePart) {
        return filePart.flatMap(fp -> {
            String fileName = fp.filename();
            return Mono.fromCallable(() -> Files.createTempFile("config-upload-", "-" + fileName))
                    .flatMap(tempPath -> fp.transferTo(tempPath)
                            .then(Mono.fromCallable(() -> extractConstants(tempPath, fileName))
                                    .subscribeOn(Schedulers.boundedElastic()))
                            .doFinally(_ -> {
                                try {
                                    Files.deleteIfExists(tempPath);
                                } catch (Exception ignored) {
                                    // best effort cleanup
                                }
                            }));
        });
    }

    private UnitConstants extractConstants(Path path, String fileName) throws Exception {
        String lower = fileName.toLowerCase();
        var result = (lower.endsWith(".yml") || lower.endsWith(".yaml"))
                ? yamlExtractor.extract(path)
                : lower.endsWith(".properties")
                        ? propertiesExtractor.extract(path)
                        : null;

        if (result == null || result.isEmpty()) {
            throw new org.glodean.constants.extractor.ModelExtractor.ExtractionException(
                    "Unsupported config file type: " + fileName
                            + ". Supported: .yml, .yaml, .properties");
        }
        return result.iterator().next();
    }
}
