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
 */
@RestController
@RequestMapping("/class")
public record ClassBinariesController(
    @Autowired ClassConstantsStore storage, @Autowired ExtractionService extractionService) {

  /** Store a class file for a specific project and version. */
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

  /** Store a class file for a project (version assigned automatically). */
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
            Exception.class, _ -> Mono.just(ResponseEntity.internalServerError().build()));
  }

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

  /** Lookup constants for a class using a request describing project/class/version. */
  @GetMapping
  @ModelAttribute
  public Mono<ResponseEntity<GetClassConstantsReply>> classConstants(
      Mono<GetClassConstantsRequest> request) {
    return request
        .map(GetClassConstantsRequest::key)
        .flatMap(storage::find)
        .map(GetClassConstantsReply::new)
        .map(ResponseEntity::ok)
        .onErrorResume(
            IllegalArgumentException.class, _ -> Mono.just(ResponseEntity.notFound().build()))
        .onErrorResume(
            Exception.class, _ -> Mono.just(ResponseEntity.internalServerError().build()));
  }
}
