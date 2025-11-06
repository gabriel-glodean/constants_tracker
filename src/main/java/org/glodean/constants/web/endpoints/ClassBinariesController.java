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
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.lang.classfile.ClassFile;
import java.util.List;

@RestController
@RequestMapping("/class")
public record ClassBinariesController(@Autowired SolrService storage) {
    private static final Logger logger = LogManager.getLogger(ClassBinariesController.class);

    @PutMapping(consumes = {"application/java-vm"})
    public Mono<ClassConstants> storeClass(@RequestBody Mono<byte[]> javaClass,
                                           @RequestParam("project") String project,
                                           @RequestParam("version") int version) {
        return javaClass
                .map(bytes -> ClassFile.of().parse(bytes))
                .map(cm -> {
                    try {
                        logger.atInfo().log("Extracting constants for class {}", cm.thisClass().asInternalName());
                        return new ClassModelExtractor(cm).extract();
                    } catch (ModelExtractor.ExtractionException e) {
                        return List.<ClassConstants>of();
                    }
                })
                .map(Iterables::getOnlyElement)
                .flatMap(classConstants -> storage.store(classConstants, project, version));
    }

    @GetMapping
    public Mono<GetClassConstantsReply> classConstants(@RequestParam("class") String clazz,
                                                       @RequestParam("project") String project,
                                                       @RequestParam("version") int version) {
        return storage.find(new GetClassConstantsRequest(clazz, project, version));
    }

}
