package org.glodean.constants.web.endpoints;

import java.util.List;

import org.glodean.constants.services.ProjectVersionService;
import org.glodean.constants.store.postgres.entity.ProjectVersionEntity;
import org.glodean.constants.store.postgres.entity.UnitDescriptorEntity;
import org.glodean.constants.store.postgres.repository.UnitDescriptorRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

/**
 * HTTP endpoints for managing project version lifecycle.
 *
 * <p>Provides operations to finalize a version (sealing it against further uploads/deletions),
 * to query version metadata including the inheritance chain, and to synchronize removals by
 * diffing the current version's units against its parent.
 *
 * <p><b>API Examples:</b>
 * <pre>
 * # Finalize version 3 of project "jdk"
 * curl -X POST "http://localhost:8080/project/jdk/version/3/finalize"
 *
 * # Get version metadata
 * curl "http://localhost:8080/project/jdk/version/3"
 *
 * # Sync removals (detect classes removed since parent version)
 * curl -X POST "http://localhost:8080/project/jdk/version/3/sync"
 * </pre>
 */
@RestController
@RequestMapping("/project")
public record VersionController(
    @Autowired ProjectVersionService projectVersionService,
    @Autowired UnitDescriptorRepository descriptorRepo) {

  /**
   * Finalize a version — sealing it against further uploads or deletions.
   * Units from this version will be inherited by future versions unless explicitly removed.
   *
   * @param project the project identifier
   * @param version the version number to finalize
   * @return 200 OK with the updated version entity,
   *         404 Not Found if the version does not exist,
   *         409 Conflict if the version is already finalized,
   *         500 Internal Server Error for other failures
   */
  @PostMapping("/{project}/version/{version}/finalize")
  public Mono<ResponseEntity<ProjectVersionEntity>> finalizeVersion(
      @PathVariable("project") String project,
      @PathVariable("version") int version) {
    return projectVersionService
        .finalizeVersion(project, version)
        .map(entity -> ResponseEntity.ok(entity))
        .onErrorResume(
            IllegalArgumentException.class,
            _ -> Mono.just(ResponseEntity.notFound().build()))
        .onErrorResume(
            IllegalStateException.class,
            _ -> Mono.just(ResponseEntity.status(409).build()))
        .onErrorResume(
            Exception.class,
            _ -> Mono.just(ResponseEntity.internalServerError().build()));
  }

  /**
   * Get metadata for a specific project version, including parent version information.
   *
   * @param project the project identifier
   * @param version the version number
   * @return 200 OK with the version entity, 404 Not Found if it does not exist
   */
  @GetMapping("/{project}/version/{version}")
  public Mono<ResponseEntity<ProjectVersionEntity>> getVersion(
      @PathVariable("project") String project,
      @PathVariable("version") int version) {
    return projectVersionService
        .getVersion(project, version)
        .map(entity -> ResponseEntity.ok(entity))
        .defaultIfEmpty(ResponseEntity.notFound().build());
  }

  /**
   * Detect and record removals by comparing the units uploaded to this version against
   * the effective unit set of the parent version. Any unit that existed in the parent
   * but is absent from this version's own uploads will be recorded as a deletion,
   * preventing silent inheritance.
   *
   * <p>This is useful when individual class files were uploaded (not a full JAR), and
   * you want to explicitly signal that the upload is complete and missing classes should
   * be treated as removed.
   *
   * @param project the project identifier
   * @param version the version number
   * @return 200 OK with the list of auto-deleted unit paths,
   *         409 Conflict if the version is finalized,
   *         500 Internal Server Error for other failures
   */
  @PostMapping("/{project}/version/{version}/sync")
  public Mono<ResponseEntity<List<String>>> syncRemovals(
      @PathVariable("project") String project,
      @PathVariable("version") int version) {
    // Collect the set of unit paths that were explicitly uploaded to this version
    return descriptorRepo
        .findAllByProjectAndVersion(project, version)
        .map(UnitDescriptorEntity::path)
        .collect(java.util.stream.Collectors.toSet())
        .flatMap(uploadedPaths ->
            projectVersionService
                .recordRemovals(project, version, uploadedPaths)
                .collectList())
        .map(removed -> ResponseEntity.ok(removed))
        .onErrorResume(
            IllegalStateException.class,
            _ -> Mono.just(ResponseEntity.status(409).build()))
        .onErrorResume(
            Exception.class,
            _ -> Mono.just(ResponseEntity.internalServerError().build()));
  }
}
