package org.glodean.constants.web.endpoints;

import java.util.Collection;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import org.glodean.constants.extractor.ModelExtractor;
import org.glodean.constants.extractor.ModelExtractorSupplierRepository;
import org.glodean.constants.model.UnitConstants;
import org.glodean.constants.model.UnitDescriptor;
import org.glodean.constants.store.UnitConstantsStore;
import org.glodean.constants.web.validation.ValidProjectName;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

/**
 * REST endpoint for uploading YAML and properties configuration files and extracting constants.
 *
 * <p>Accepts multipart file uploads, reads them into memory, delegates to the appropriate
 * {@link ModelExtractor} via {@link ModelExtractorSupplierRepository}, and persists the results.
 * New file formats can be supported by registering additional suppliers in
 * {@code ConfigFileExtractionConfiguration} without modifying this controller.
 */
@Validated
@RestController
@RequestMapping("/config")
public class ConfigFileController {

    private final UnitConstantsStore storage;
    private final ModelExtractorSupplierRepository extractorRepository;

    @Autowired
    public ConfigFileController(
            UnitConstantsStore storage,
            ModelExtractorSupplierRepository extractorRepository) {
        this.storage = storage;
        this.extractorRepository = extractorRepository;
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
            @NotBlank @ValidProjectName @RequestParam("project") String project,
            @Positive @RequestParam("version") int version) {
        return extractFromUpload(filePart)
                .flatMap(unit -> storage.store(unit, project.strip(), version).thenReturn(ResponseEntity.ok().build()));
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
            @NotBlank @ValidProjectName @RequestParam("project") String project) {
        return extractFromUpload(filePart)
                .flatMap(unit -> storage.store(unit, project.strip()).thenReturn(ResponseEntity.ok().build()));
    }

    private Mono<UnitConstants> extractFromUpload(Mono<FilePart> filePart) {
        return filePart.flatMap(fp -> {
            String fileName = fp.filename();
            return DataBufferUtils.join(fp.content())
                    .flatMap(buffer -> {
                        byte[] bytes = new byte[buffer.readableByteCount()];
                        buffer.read(bytes);
                        DataBufferUtils.release(buffer);
                        return Mono.fromCallable(() -> extractConstants(bytes, fileName));
                    });
        });
    }

    private UnitConstants extractConstants(byte[] bytes, String fileName)
            throws ModelExtractor.ExtractionException {
        var supply = extractorRepository.resolve(fileName, bytes)
                .orElseThrow(() -> new ModelExtractor.ExtractionException(
                        "Unsupported config file type: " + fileName
                        + ". Supported: .yml, .yaml, .properties"));
        var descriptor = new UnitDescriptor(supply.sourceKind(), fileName, bytes.length);
        Collection<UnitConstants> results = supply.extractor().extract(descriptor);
        if (results.isEmpty()) {
            throw new ModelExtractor.ExtractionException("No constants extracted from: " + fileName);
        }
        return results.iterator().next();
    }
}
