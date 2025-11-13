package org.glodean.constants.web.endpoints;

import com.google.common.collect.Iterables;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.glodean.constants.dto.GetClassConstantsRequest;
import org.glodean.constants.extractor.ModelExtractor;
import org.glodean.constants.extractor.bytecode.ClassModelExtractor;
import org.glodean.constants.model.ClassConstants;
import org.glodean.constants.store.SolrService;
import org.glodean.constants.dto.GetClassConstantsReply;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.lang.classfile.ClassFile;

@RestController
@RequestMapping("/class")
public record ClassBinariesController(@Autowired SolrService storage) {
    private static final Logger logger = LogManager.getLogger(ClassBinariesController.class);

    @PutMapping(consumes = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public Mono<ResponseEntity<ClassConstants>> storeClass(@RequestBody Mono<DataBuffer> javaClass,
                                                           @RequestParam("project") String project,
                                                           @RequestParam("version") int version) {
        return modelMono(javaClass)
                .flatMap(classConstants -> storage.store(classConstants, project, version))
                .map(ResponseEntity::ok)
                .onErrorResume(ModelExtractor.ExtractionException.class, _ -> Mono.just(ResponseEntity.unprocessableEntity().build()))
                .onErrorResume(IllegalArgumentException.class, _ -> Mono.just(ResponseEntity.unprocessableEntity().build()))
                .onErrorResume(Exception.class, _ -> Mono.just(ResponseEntity.internalServerError().build()));
    }

    @PostMapping(consumes = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public Mono<ResponseEntity<ClassConstants>> storeClass(@RequestBody Mono<DataBuffer> javaClass,
                                           @RequestParam("project") String project) {
        return modelMono(javaClass)
                .flatMap(classConstants -> storage.store(classConstants, project))
                .map(ResponseEntity::ok)
                .onErrorResume(ModelExtractor.ExtractionException.class, _ -> Mono.just(ResponseEntity.unprocessableEntity().build()))
                .onErrorResume(IllegalArgumentException.class, _ -> Mono.just(ResponseEntity.unprocessableEntity().build()))
                .onErrorResume(Exception.class, _ -> Mono.just(ResponseEntity.internalServerError().build()));
    }

    private static Mono<ClassConstants> modelMono(Mono<DataBuffer> javaClass) {
        return javaClass
                .map(dataBuffer -> {
                    byte[] bytes = new byte[dataBuffer.readableByteCount()];
                    dataBuffer.read(bytes);
                    DataBufferUtils.release(dataBuffer);
                    return bytes;
                })
                .flatMap(bytes -> {
                    try {
                        return Mono.just(ClassFile.of().parse(bytes));
                    } catch (IllegalArgumentException e) {
                        return Mono.error(e);
                    }
                })
                .flatMap(cm -> {
                    try {
                        logger.atInfo().log("Extracting constants for class {}"
                                , cm.thisClass().asInternalName());
                        return Mono.just(new ClassModelExtractor(cm).extract());
                    } catch (ModelExtractor.ExtractionException e) {
                        return Mono.error(e);
                    }
                })
                .map(Iterables::getOnlyElement);
    }

    @GetMapping
    @ModelAttribute
    public Mono<ResponseEntity<GetClassConstantsReply>> classConstants(
            Mono<GetClassConstantsRequest> request) {
        return request.flatMap(storage::find)
                .map(ResponseEntity::ok)
                .onErrorResume(IllegalArgumentException.class, _ -> Mono.just(ResponseEntity.notFound().build()))
                .onErrorResume(Exception.class, _ -> Mono.just(ResponseEntity.internalServerError().build()));
    }

}
