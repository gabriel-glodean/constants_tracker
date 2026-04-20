package org.glodean.constants.services;

import java.time.LocalDateTime;
import java.util.Set;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.glodean.constants.store.VersionIncrementer;
import org.glodean.constants.store.postgres.ProjectVersionEntity;
import org.glodean.constants.store.postgres.ProjectVersionRepository;
import org.glodean.constants.store.postgres.UnitDescriptorEntity;
import org.glodean.constants.store.postgres.UnitDescriptorRepository;
import org.glodean.constants.store.postgres.VersionDeletionEntity;
import org.glodean.constants.store.postgres.VersionDeletionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
/**
 * Manages the lifecycle of project versions and the inheritance chain.
 *
 * <p>When a new version is created via {@link #ensureVersionExists}, it records the latest
 * finalized version as its parent. Units from the parent version are inherited lazily:
 * lookups that miss in the current version walk the parent chain unless the unit was
 * explicitly deleted via {@link #deleteUnit}.
 *
 * <p>A version is sealed by calling {@link #finalizeVersion}, after which no further
 * uploads or deletions are accepted.
 */
@Service
public class ProjectVersionService {
  private static final Logger logger = LogManager.getLogger(ProjectVersionService.class);
  private final ProjectVersionRepository versionRepo;
  private final VersionDeletionRepository deletionRepo;
  private final UnitDescriptorRepository descriptorRepo;
  private final VersionIncrementer versionIncrementer;

  public ProjectVersionService(
      @Autowired ProjectVersionRepository versionRepo,
      @Autowired VersionDeletionRepository deletionRepo,
      @Autowired UnitDescriptorRepository descriptorRepo,
      @Autowired VersionIncrementer versionIncrementer) {
    this.versionRepo = versionRepo;
    this.deletionRepo = deletionRepo;
    this.descriptorRepo = descriptorRepo;
    this.versionIncrementer = versionIncrementer;
  }
  /**
   * Ensures a {@link ProjectVersionEntity} row exists for the given project/version.
   * If this is the first time the version is seen, the latest finalized version
   * becomes its parent (for inheritance).
   *
   * @return the existing or newly created version entity
   */
  @Transactional
  public Mono<ProjectVersionEntity> ensureVersionExists(String project, int version) {
    return versionRepo
        .findByProjectAndVersion(project, version)
        .switchIfEmpty(Mono.defer(() -> createVersion(project, version)));
  }

  /**
   * Returns the current open version for a project, or creates a new one if none exists.
   *
   * <p>This is the preferred way to obtain a version for single-unit uploads (e.g.,
   * individual {@code .class} files). Multiple uploads will share the same open version
   * until it is finalized, at which point the next call creates a fresh version.
   *
   * @param project the project identifier
   * @return the current open {@link ProjectVersionEntity}
   */
  @Transactional
  public Mono<ProjectVersionEntity> getOrCreateOpenVersion(String project) {
    return versionRepo
        .findTopByProjectAndStatusOrderByVersionDesc(project, ProjectVersionEntity.STATUS_OPEN)
        .switchIfEmpty(Mono.defer(() -> {
          int nextVersion = versionIncrementer.getNextVersion(project);
          return createVersion(project, nextVersion);
        }));
  }
  private Mono<ProjectVersionEntity> createVersion(String project, int version) {
    Mono<Integer> parentVersionMono = versionRepo
        .findTopByProjectAndStatusOrderByVersionDesc(
            project, ProjectVersionEntity.STATUS_FINALIZED)
        .map(ProjectVersionEntity::version);
    return parentVersionMono
        .map(pv -> new ProjectVersionEntity(
            null, project, version, pv,
            ProjectVersionEntity.STATUS_OPEN, LocalDateTime.now(), null))
        .switchIfEmpty(Mono.fromSupplier(() -> new ProjectVersionEntity(
            null, project, version, null,
            ProjectVersionEntity.STATUS_OPEN, LocalDateTime.now(), null)))
        .doOnNext(e -> logger.atInfo().log(
            "Creating version {} for project {} (parent={})", version, project, e.parentVersion()))
        .flatMap(versionRepo::save);
  }
  /**
   * Marks a version as finalized. No further uploads or deletions are allowed.
   *
   * @throws IllegalArgumentException if the version does not exist
   * @throws IllegalStateException if the version is already finalized
   */
  @Transactional
  public Mono<ProjectVersionEntity> finalizeVersion(String project, int version) {
    return versionRepo
        .findByProjectAndVersion(project, version)
        .switchIfEmpty(Mono.error(new IllegalArgumentException(
            "Version " + version + " does not exist for project " + project)))
        .flatMap(entity -> {
          if (ProjectVersionEntity.STATUS_FINALIZED.equals(entity.status())) {
            return Mono.error(new IllegalStateException(
                "Version " + version + " is already finalized for project " + project));
          }
          var finalized = new ProjectVersionEntity(
              entity.id(), entity.project(), entity.version(), entity.parentVersion(),
              ProjectVersionEntity.STATUS_FINALIZED, entity.createdAt(), LocalDateTime.now());
          logger.atInfo().log("Finalizing version {} for project {}", version, project);
          return versionRepo.save(finalized);
        });
  }
  /**
   * Explicitly deletes a unit from a version, preventing it from being inherited
   * from the parent version chain.
   *
   * @throws IllegalStateException if the version is finalized
   */
  @Transactional
  public Mono<Void> deleteUnit(String project, int version, String unitPath) {
    return assertVersionOpen(project, version)
        .then(Mono.defer(() -> deletionRepo.existsByProjectAndVersionAndUnitPath(project, version, unitPath)))
        .flatMap(exists -> {
          if (exists) {
            return Mono.<Void>empty();
          }
          var deletion = new VersionDeletionEntity(
              null, project, version, unitPath, LocalDateTime.now());
          logger.atInfo().log(
              "Deleting unit {} from version {} of project {}", unitPath, version, project);
          return deletionRepo.save(deletion).then();
        });
  }
  /** Checks whether a unit has been explicitly deleted in the given version. */
  public Mono<Boolean> isUnitDeleted(String project, int version, String unitPath) {
    return deletionRepo.existsByProjectAndVersionAndUnitPath(project, version, unitPath);
  }
  /** Looks up the version entity (for inheritance chain walking). */
  public Mono<ProjectVersionEntity> getVersion(String project, int version) {
    return versionRepo.findByProjectAndVersion(project, version);
  }
  /** Returns whether the version is open for mutations. */
  public Mono<Boolean> isVersionOpen(String project, int version) {
    return versionRepo
        .findByProjectAndVersion(project, version)
        .map(e -> ProjectVersionEntity.STATUS_OPEN.equals(e.status()))
        .defaultIfEmpty(true);
  }
  /**
   * Compares the set of uploaded unit paths against the parent version's units and
   * automatically records deletions for any units present in the parent but absent
   * from {@code uploadedPaths}.
   *
   * <p>This should be called after a complete batch upload (e.g., a JAR) so that
   * classes removed between versions are not silently inherited.
   *
   * @param project       the project identifier
   * @param version       the current version
   * @param uploadedPaths the set of unit paths that were uploaded in this version
   * @return a {@link Flux} of the unit paths that were auto-deleted
   */
  @Transactional
  public Flux<String> recordRemovals(String project, int version, Set<String> uploadedPaths) {
    return versionRepo
        .findByProjectAndVersion(project, version)
        .flatMapMany(versionEntity -> {
          Integer parentVersion = versionEntity.parentVersion();
          if (parentVersion == null) {
            return Flux.empty();
          }
          return collectInheritedPaths(project, parentVersion)
              .filter(parentPath -> !uploadedPaths.contains(parentPath))
              .flatMap(removedPath ->
                  deletionRepo
                      .existsByProjectAndVersionAndUnitPath(project, version, removedPath)
                      .flatMap(exists -> {
                        if (exists) {
                          return Mono.just(removedPath);
                        }
                        var deletion = new VersionDeletionEntity(
                            null, project, version, removedPath, LocalDateTime.now());
                        logger.atInfo().log(
                            "Auto-deleting unit {} from version {} of project {} (absent from upload)",
                            removedPath, version, project);
                        return deletionRepo.save(deletion).thenReturn(removedPath);
                      }));
        });
  }

  /**
   * Collects all effective unit paths for a version by combining its own descriptors
   * with those inherited from the parent chain (excluding deleted ones).
   */
  private Flux<String> collectInheritedPaths(String project, int version) {
    Flux<String> ownPaths = descriptorRepo
        .findAllByProjectAndVersion(project, version)
        .map(UnitDescriptorEntity::path);

    return versionRepo
        .findByProjectAndVersion(project, version)
        .flatMapMany(versionEntity -> {
          Integer parentVersion = versionEntity.parentVersion();
          if (parentVersion == null) {
            return ownPaths;
          }
          // Recursively get parent paths, exclude deletions at this level, merge with own
          Flux<String> parentPaths = collectInheritedPaths(project, parentVersion)
              .filterWhen(path ->
                  deletionRepo
                      .existsByProjectAndVersionAndUnitPath(project, version, path)
                      .map(deleted -> !deleted));
          return Flux.merge(ownPaths, parentPaths).distinct();
        })
        .switchIfEmpty(ownPaths);
  }

  private Mono<Void> assertVersionOpen(String project, int version) {
    return versionRepo
        .findByProjectAndVersion(project, version)
        .flatMap(entity -> {
          if (ProjectVersionEntity.STATUS_FINALIZED.equals(entity.status())) {
            return Mono.<Void>error(new IllegalStateException(
                "Version " + version + " is finalized for project " + project));
          }
          return Mono.<Void>empty();
        });
  }
}
