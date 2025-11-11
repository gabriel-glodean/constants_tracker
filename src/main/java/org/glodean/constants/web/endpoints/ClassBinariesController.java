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
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.lang.classfile.ClassFile;
import java.util.List;

@RestController
@RequestMapping("/class")
public record ClassBinariesController(@Autowired SolrService storage) {
    private static final Logger logger = LogManager.getLogger(ClassBinariesController.class);

    @PutMapping(consumes = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public Mono<ClassConstants> storeClass(@RequestBody Mono<DataBuffer> javaClass,
                                           @RequestParam("project") String project,
                                           @RequestParam("version") int version) {
        return javaClass
                .map(dataBuffer -> {
                    byte[] bytes = new byte[dataBuffer.readableByteCount()];
                    dataBuffer.read(bytes);
                    DataBufferUtils.release(dataBuffer);
                    return bytes;
                })
                .map(bytes -> ClassFile.of().parse(bytes))
                .map(cm -> {
                    try {
                        logger.atInfo().log("Extracting constants for class {}, for version {}"
                                , cm.thisClass().asInternalName(), version);
                        return new ClassModelExtractor(cm).extract();
                    } catch (ModelExtractor.ExtractionException e) {
                        return List.<ClassConstants>of();
                    }
                })
                .map(Iterables::getOnlyElement)
                .flatMap(classConstants -> storage.store(classConstants, project, version));
    }

    @PostMapping(consumes = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public Mono<ClassConstants> storeClass(@RequestBody Mono<DataBuffer> javaClass,
                                           @RequestParam("project") String project) {
        return javaClass
                .map(dataBuffer -> {
                    byte[] bytes = new byte[dataBuffer.readableByteCount()];
                    dataBuffer.read(bytes);
                    DataBufferUtils.release(dataBuffer);
                    return bytes;
                })
                .map(bytes -> ClassFile.of().parse(bytes))
                .map(cm -> {
                    try {
                        logger.atInfo().log("Extracting constants for class {}, at latest version"
                                , cm.thisClass().asInternalName());
                        return new ClassModelExtractor(cm).extract();
                    } catch (ModelExtractor.ExtractionException e) {
                        return List.<ClassConstants>of();
                    }
                })
                .map(Iterables::getOnlyElement)
                .flatMap(classConstants -> storage.store(classConstants, project));
    }

    @GetMapping
    @ModelAttribute
    public Mono<GetClassConstantsReply> classConstants(
            Mono<GetClassConstantsRequest> request) {
        return request.flatMap(storage::find);
    }

}
