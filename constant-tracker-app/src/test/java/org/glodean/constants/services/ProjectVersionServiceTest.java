package org.glodean.constants.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import org.glodean.constants.store.VersionIncrementer;
import org.glodean.constants.store.postgres.ProjectVersionEntity;
import org.glodean.constants.store.postgres.ProjectVersionRepository;
import org.glodean.constants.store.postgres.UnitDescriptorEntity;
import org.glodean.constants.store.postgres.UnitDescriptorRepository;
import org.glodean.constants.store.postgres.VersionDeletionEntity;
import org.glodean.constants.store.postgres.VersionDeletionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@ExtendWith(MockitoExtension.class)
class ProjectVersionServiceTest {

  @Mock ProjectVersionRepository versionRepo;
  @Mock VersionDeletionRepository deletionRepo;
  @Mock UnitDescriptorRepository descriptorRepo;
  @Mock VersionIncrementer versionIncrementer;

  ProjectVersionService service;

  @BeforeEach
  void setUp() {
    service =
        new ProjectVersionService(versionRepo, deletionRepo, descriptorRepo, versionIncrementer);
  }

  static ProjectVersionEntity openVersion(String project, int version, Integer parent) {
    return new ProjectVersionEntity(
        (long) version,
        project,
        version,
        parent,
        ProjectVersionEntity.STATUS_OPEN,
        LocalDateTime.now(),
        null);
  }

  static ProjectVersionEntity finalizedVersion(String project, int version, Integer parent) {
    return new ProjectVersionEntity(
        (long) version,
        project,
        version,
        parent,
        ProjectVersionEntity.STATUS_FINALIZED,
        LocalDateTime.now(),
        LocalDateTime.now());
  }

  // -- ensureVersionExists --

  @Test
  void ensureVersionExistsReturnsExistingVersion() {
    var existing = openVersion("proj", 1, null);
    when(versionRepo.findByProjectAndVersion("proj", 1)).thenReturn(Mono.just(existing));

    var result = service.ensureVersionExists("proj", 1).block();
    assertThat(result).isNotNull();
    assertThat(result.version()).isEqualTo(1);
    assertThat(result.project()).isEqualTo("proj");
  }

  @Test
  void ensureVersionExistsCreatesNewWithParent() {
    when(versionRepo.findByProjectAndVersion("proj", 2)).thenReturn(Mono.empty());
    var parent = finalizedVersion("proj", 1, null);
    when(versionRepo.findTopByProjectAndStatusOrderByVersionDesc("proj", "FINALIZED"))
        .thenReturn(Mono.just(parent));
    when(versionRepo.save(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

    var v = service.ensureVersionExists("proj", 2).block();
    assertThat(v.version()).isEqualTo(2);
    assertThat(v.parentVersion()).isEqualTo(1);
    assertThat(v.status()).isEqualTo("OPEN");
  }

  @Test
  void ensureVersionExistsCreatesNewWithoutParent() {
    when(versionRepo.findByProjectAndVersion("proj", 1)).thenReturn(Mono.empty());
    when(versionRepo.findTopByProjectAndStatusOrderByVersionDesc("proj", "FINALIZED"))
        .thenReturn(Mono.empty());
    when(versionRepo.save(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

    var v = service.ensureVersionExists("proj", 1).block();
    assertThat(v.parentVersion()).isNull();
    assertThat(v.status()).isEqualTo("OPEN");
  }

  // -- getOrCreateOpenVersion --

  @Test
  void getOrCreateOpenVersionReusesExisting() {
    var existing = openVersion("proj", 3, 2);
    when(versionRepo.findTopByProjectAndStatusOrderByVersionDesc("proj", "OPEN"))
        .thenReturn(Mono.just(existing));

    var result = service.getOrCreateOpenVersion("proj").block();
    assertThat(result).isEqualTo(existing);
    verify(versionIncrementer, never()).getNextVersion(anyString());
  }

  @Test
  void getOrCreateOpenVersionCreatesWhenNoneOpen() {
    when(versionRepo.findTopByProjectAndStatusOrderByVersionDesc("proj", "OPEN"))
        .thenReturn(Mono.empty());
    when(versionIncrementer.getNextVersion("proj")).thenReturn(4);
    // createVersion is called directly (not via ensureVersionExists),
    // so findByProjectAndVersion is not needed here
    var parent = finalizedVersion("proj", 3, null);
    when(versionRepo.findTopByProjectAndStatusOrderByVersionDesc("proj", "FINALIZED"))
        .thenReturn(Mono.just(parent));
    when(versionRepo.save(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

    var v = service.getOrCreateOpenVersion("proj").block();
    assertThat(v.version()).isEqualTo(4);
    assertThat(v.parentVersion()).isEqualTo(3);
  }

  // -- finalizeVersion --

  @Test
  void finalizeVersionSealsOpenVersion() {
    var open = openVersion("proj", 1, null);
    when(versionRepo.findByProjectAndVersion("proj", 1)).thenReturn(Mono.just(open));
    when(versionRepo.save(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

    var v = service.finalizeVersion("proj", 1).block();
    assertThat(v.status()).isEqualTo("FINALIZED");
    assertThat(v.finalizedAt()).isNotNull();
  }

  @Test
  void finalizeVersionRejectsAlreadyFinalized() {
    var finalized = finalizedVersion("proj", 1, null);
    when(versionRepo.findByProjectAndVersion("proj", 1)).thenReturn(Mono.just(finalized));

    assertThatThrownBy(() -> service.finalizeVersion("proj", 1).block())
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void finalizeVersionRejectsNonExistent() {
    when(versionRepo.findByProjectAndVersion("proj", 99)).thenReturn(Mono.empty());

    assertThatThrownBy(() -> service.finalizeVersion("proj", 99).block())
        .isInstanceOf(IllegalArgumentException.class);
  }

  // -- deleteUnit --

  @Test
  void deleteUnitCreatesRecordWhenNotExists() {
    var open = openVersion("proj", 2, 1);
    when(versionRepo.findByProjectAndVersion("proj", 2)).thenReturn(Mono.just(open));
    when(deletionRepo.existsByProjectAndVersionAndUnitPath("proj", 2, "com/Foo"))
        .thenReturn(Mono.just(false));
    when(deletionRepo.save(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

    service.deleteUnit("proj", 2, "com/Foo").block();
    verify(deletionRepo).save(any(VersionDeletionEntity.class));
  }

  @Test
  void deleteUnitSkipsWhenAlreadyDeleted() {
    var open = openVersion("proj", 2, 1);
    when(versionRepo.findByProjectAndVersion("proj", 2)).thenReturn(Mono.just(open));
    when(deletionRepo.existsByProjectAndVersionAndUnitPath("proj", 2, "com/Foo"))
        .thenReturn(Mono.just(true));

    service.deleteUnit("proj", 2, "com/Foo").block();
    verify(deletionRepo, never()).save(any());
  }

  @Test
  void deleteUnitRejectsFinalizedVersion() {
    var finalized = finalizedVersion("proj", 1, null);
    when(versionRepo.findByProjectAndVersion("proj", 1)).thenReturn(Mono.just(finalized));

    assertThatThrownBy(() -> service.deleteUnit("proj", 1, "com/Foo").block())
        .isInstanceOf(IllegalStateException.class);
  }

  // -- recordRemovals --

  @Test
  void recordRemovalsDetectsMissingUnitsFromParent() {
    var v2 = openVersion("proj", 2, 1);
    when(versionRepo.findByProjectAndVersion("proj", 2)).thenReturn(Mono.just(v2));

    var v1 = openVersion("proj", 1, null);
    when(versionRepo.findByProjectAndVersion("proj", 1)).thenReturn(Mono.just(v1));
    when(descriptorRepo.findAllByProjectAndVersion("proj", 1))
        .thenReturn(
            Flux.just(
                new UnitDescriptorEntity(1L, "proj", 1, "CLASS_FILE", "com/ClassA", 100, null),
                new UnitDescriptorEntity(2L, "proj", 1, "CLASS_FILE", "com/ClassB", 100, null),
                new UnitDescriptorEntity(
                    3L, "proj", 1, "CLASS_FILE", "com/ClassC", 100, null)));

    when(deletionRepo.existsByProjectAndVersionAndUnitPath(anyString(), anyInt(), anyString()))
        .thenReturn(Mono.just(false));
    when(deletionRepo.save(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

    Set<String> uploaded = Set.of("com/ClassA", "com/ClassB");
    List<String> removed = service.recordRemovals("proj", 2, uploaded).collectList().block();
    assertThat(removed).containsExactly("com/ClassC");
  }

  @Test
  void recordRemovalsNoOpWhenNoParent() {
    var v1 = openVersion("proj", 1, null);
    when(versionRepo.findByProjectAndVersion("proj", 1)).thenReturn(Mono.just(v1));

    List<String> removed =
        service.recordRemovals("proj", 1, Set.of("com/ClassA")).collectList().block();
    assertThat(removed).isEmpty();
  }
}