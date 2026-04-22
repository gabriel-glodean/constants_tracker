package org.glodean.constants.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import org.glodean.constants.dto.ConstantDiffEntry;
import org.glodean.constants.store.postgres.ConstantDiffRow;
import org.glodean.constants.store.postgres.DiffRepository;
import org.glodean.constants.store.postgres.ProjectVersionEntity;
import org.glodean.constants.store.postgres.ProjectVersionRepository;
import org.glodean.constants.store.postgres.UnitDescriptorEntity;
import org.glodean.constants.store.postgres.UnitDescriptorRepository;
import org.glodean.constants.store.postgres.UnitSnapshotEntity;
import org.glodean.constants.store.postgres.UnitSnapshotRepository;
import org.glodean.constants.store.postgres.VersionDeletionEntity;
import org.glodean.constants.store.postgres.VersionDeletionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class DiffServiceTest {

  @Mock ProjectVersionRepository versionRepo;
  @Mock UnitDescriptorRepository descriptorRepo;
  @Mock UnitSnapshotRepository snapshotRepo;
  @Mock VersionDeletionRepository deletionRepo;
  @Mock DiffRepository diffRepo;

  DiffService diffService;

  @BeforeEach
  void setUp() {
    diffService =
        new DiffService(versionRepo, descriptorRepo, snapshotRepo, deletionRepo, diffRepo);
  }

  private ProjectVersionEntity ver(int v, Integer parent) {
    return new ProjectVersionEntity(
        (long) v, "proj", v, parent, "FINALIZED", LocalDateTime.now(), LocalDateTime.now());
  }

  private UnitDescriptorEntity desc(long id, String path, int version) {
    return new UnitDescriptorEntity(id, "proj", version, "CLASS_FILE", path, 100L, "h" + id);
  }

  private UnitSnapshotEntity snap(long id, long descId, String name) {
    return new UnitSnapshotEntity(id, descId, name, "{}");
  }

  private ConstantDiffRow row(long snapId, String value, String structural, String semantic) {
    return new ConstantDiffRow(
        snapId, value, "String", structural, "CORE", semantic,
        null, null, "com.Foo", "bar", "()V", 0, 10, 1.0);
  }

  private ConstantDiffRow rowAt(long snapId, String value, String structural, String semantic,
      String className, String methodName, int lineNumber) {
    return new ConstantDiffRow(
        snapId, value, "String", structural, "CORE", semantic,
        null, null, className, methodName, "()V", 0, lineNumber, 1.0);
  }

  private void noDeletions() {
    when(deletionRepo.findAllByProjectAndVersion(anyString(), anyInt()))
        .thenReturn(Flux.empty());
  }

  @Test
  void addedConstant_appearsInDiff() {
    when(versionRepo.findByProjectAndVersion("proj", 1)).thenReturn(Mono.just(ver(1, null)));
    when(versionRepo.findByProjectAndVersion("proj", 2)).thenReturn(Mono.just(ver(2, 1)));
    when(descriptorRepo.findAllByProjectAndVersion("proj", 1))
        .thenReturn(Flux.just(desc(1L, "ClassA", 1)));
    when(descriptorRepo.findAllByProjectAndVersion("proj", 2))
        .thenReturn(Flux.just(desc(2L, "ClassA", 2)));
    when(snapshotRepo.findByDescriptorIdAndUnitName(1L, "ClassA"))
        .thenReturn(Mono.just(snap(10L, 1L, "ClassA")));
    when(snapshotRepo.findByDescriptorIdAndUnitName(2L, "ClassA"))
        .thenReturn(Mono.just(snap(20L, 2L, "ClassA")));
    noDeletions();
    when(diffRepo.loadForSnapshots(java.util.Set.of(10L)))
        .thenReturn(Flux.just(row(10L, "hello", "METHOD_INVOCATION_PARAMETER", "LOG_MESSAGE")));
    when(diffRepo.loadForSnapshots(java.util.Set.of(20L)))
        .thenReturn(Flux.just(
            row(20L, "hello", "METHOD_INVOCATION_PARAMETER", "LOG_MESSAGE"),
            row(20L, "world", "METHOD_INVOCATION_PARAMETER", "UNKNOWN")));

    StepVerifier.create(diffService.diff("proj", 1, 2))
        .assertNext(r -> {
          assertThat(r.units()).hasSize(1);
          var e = r.units().getFirst().changedConstants().getFirst();
          assertThat(e.value()).isEqualTo("world");
          assertThat(e.changeKind()).isEqualTo(ConstantDiffEntry.ChangeKind.ADDED);
        })
        .verifyComplete();
  }

  @Test
  void removedUnit_markedAsRemoved() {
    when(versionRepo.findByProjectAndVersion("proj", 1)).thenReturn(Mono.just(ver(1, null)));
    when(versionRepo.findByProjectAndVersion("proj", 2)).thenReturn(Mono.just(ver(2, 1)));
    when(descriptorRepo.findAllByProjectAndVersion("proj", 1))
        .thenReturn(Flux.just(desc(1L, "ClassA", 1), desc(2L, "ClassB", 1)));
    when(descriptorRepo.findAllByProjectAndVersion("proj", 2))
        .thenReturn(Flux.just(desc(3L, "ClassA", 2)));
    when(snapshotRepo.findByDescriptorIdAndUnitName(1L, "ClassA"))
        .thenReturn(Mono.just(snap(10L, 1L, "ClassA")));
    when(snapshotRepo.findByDescriptorIdAndUnitName(2L, "ClassB"))
        .thenReturn(Mono.just(snap(11L, 2L, "ClassB")));
    when(snapshotRepo.findByDescriptorIdAndUnitName(3L, "ClassA"))
        .thenReturn(Mono.just(snap(10L, 3L, "ClassA"))); // same snapshotId => skip
    when(deletionRepo.findAllByProjectAndVersion("proj", 1)).thenReturn(Flux.empty());
    // ClassB explicitly deleted in v2 so it is removed and not silently inherited
    when(deletionRepo.findAllByProjectAndVersion("proj", 2))
        .thenReturn(Flux.just(
            new VersionDeletionEntity(99L, "proj", 2, "ClassB", LocalDateTime.now())));
    when(diffRepo.loadForSnapshots(java.util.Set.of(11L)))
        .thenReturn(Flux.just(row(11L, "SELECT 1", "METHOD_INVOCATION_PARAMETER", "SQL")));
    when(diffRepo.loadForSnapshots(java.util.Set.of())).thenReturn(Flux.empty());

    StepVerifier.create(diffService.diff("proj", 1, 2))
        .assertNext(r -> {
          assertThat(r.units()).hasSize(1);
          assertThat(r.units().getFirst().removedUnit()).isTrue();
          assertThat(r.units().getFirst().changedConstants().getFirst().changeKind())
              .isEqualTo(ConstantDiffEntry.ChangeKind.REMOVED);
        })
        .verifyComplete();
  }

  @Test
  void inheritedUnchangedUnit_notInDiff() {
    when(versionRepo.findByProjectAndVersion("proj", 1)).thenReturn(Mono.just(ver(1, null)));
    when(versionRepo.findByProjectAndVersion("proj", 2)).thenReturn(Mono.just(ver(2, 1)));
    when(descriptorRepo.findAllByProjectAndVersion("proj", 1))
        .thenReturn(Flux.just(desc(1L, "ClassA", 1)));
    when(descriptorRepo.findAllByProjectAndVersion("proj", 2)).thenReturn(Flux.empty());
    when(snapshotRepo.findByDescriptorIdAndUnitName(1L, "ClassA"))
        .thenReturn(Mono.just(snap(10L, 1L, "ClassA")));
    noDeletions();
    when(diffRepo.loadForSnapshots(any())).thenReturn(Flux.empty());

    StepVerifier.create(diffService.diff("proj", 1, 2))
        .assertNext(r -> assertThat(r.units()).isEmpty())
        .verifyComplete();
  }

  @Test
  void deletedUnit_notInheritedByChild() {
    when(versionRepo.findByProjectAndVersion("proj", 1)).thenReturn(Mono.just(ver(1, null)));
    when(versionRepo.findByProjectAndVersion("proj", 2)).thenReturn(Mono.just(ver(2, 1)));
    when(descriptorRepo.findAllByProjectAndVersion("proj", 1))
        .thenReturn(Flux.just(desc(1L, "ClassA", 1)));
    when(descriptorRepo.findAllByProjectAndVersion("proj", 2)).thenReturn(Flux.empty());
    when(snapshotRepo.findByDescriptorIdAndUnitName(1L, "ClassA"))
        .thenReturn(Mono.just(snap(10L, 1L, "ClassA")));
    when(deletionRepo.findAllByProjectAndVersion("proj", 1)).thenReturn(Flux.empty());
    when(deletionRepo.findAllByProjectAndVersion("proj", 2))
        .thenReturn(Flux.just(
            new VersionDeletionEntity(1L, "proj", 2, "ClassA", LocalDateTime.now())));
    when(diffRepo.loadForSnapshots(java.util.Set.of(10L)))
        .thenReturn(Flux.just(row(10L, "hello", "METHOD_INVOCATION_PARAMETER", "LOG_MESSAGE")));
    when(diffRepo.loadForSnapshots(java.util.Set.of())).thenReturn(Flux.empty());

    StepVerifier.create(diffService.diff("proj", 1, 2))
        .assertNext(r -> {
          assertThat(r.units()).hasSize(1);
          assertThat(r.units().getFirst().removedUnit()).isTrue();
        })
        .verifyComplete();
  }

  @Test
  void unknownVersion_returnsError() {
    when(versionRepo.findByProjectAndVersion("proj", 1)).thenReturn(Mono.just(ver(1, null)));
    when(versionRepo.findByProjectAndVersion("proj", 99)).thenReturn(Mono.empty());
    // fromVersion resolution starts before the error — stub its dependencies
    when(descriptorRepo.findAllByProjectAndVersion("proj", 1)).thenReturn(Flux.empty());
    when(deletionRepo.findAllByProjectAndVersion("proj", 1)).thenReturn(Flux.empty());

    StepVerifier.create(diffService.diff("proj", 1, 99))
        .expectError(IllegalArgumentException.class)
        .verify();
  }

  @Test
  void sameUsagesDifferentDbOrder_notReportedAsChange() {
    // Two snapshots differ only in the order that the DB returns their usage rows.
    // After canonical sorting they must compare equal → no diff entry.
    when(versionRepo.findByProjectAndVersion("proj", 1)).thenReturn(Mono.just(ver(1, null)));
    when(versionRepo.findByProjectAndVersion("proj", 2)).thenReturn(Mono.just(ver(2, 1)));
    when(descriptorRepo.findAllByProjectAndVersion("proj", 1))
        .thenReturn(Flux.just(desc(1L, "ClassA", 1)));
    when(descriptorRepo.findAllByProjectAndVersion("proj", 2))
        .thenReturn(Flux.just(desc(2L, "ClassA", 2)));
    when(snapshotRepo.findByDescriptorIdAndUnitName(1L, "ClassA"))
        .thenReturn(Mono.just(snap(10L, 1L, "ClassA")));
    when(snapshotRepo.findByDescriptorIdAndUnitName(2L, "ClassA"))
        .thenReturn(Mono.just(snap(20L, 2L, "ClassA")));
    noDeletions();
    // from: row A first, then row B
    when(diffRepo.loadForSnapshots(java.util.Set.of(10L)))
        .thenReturn(Flux.just(
            rowAt(10L, "hello", "STRING_CONCATENATION_MEMBER", "UNKNOWN", "UserRepo", "updateUser", 5),
            rowAt(10L, "hello", "METHOD_INVOCATION_PARAMETER", "LOG_MESSAGE", "UserRepo", "listUsers", 10)));
    // to: same rows but reversed order
    when(diffRepo.loadForSnapshots(java.util.Set.of(20L)))
        .thenReturn(Flux.just(
            rowAt(20L, "hello", "METHOD_INVOCATION_PARAMETER", "LOG_MESSAGE", "UserRepo", "listUsers", 10),
            rowAt(20L, "hello", "STRING_CONCATENATION_MEMBER", "UNKNOWN", "UserRepo", "updateUser", 5)));

    StepVerifier.create(diffService.diff("proj", 1, 2))
        .assertNext(r -> assertThat(r.units()).isEmpty())
        .verifyComplete();
  }

  @Test
  void changedUsage_appearsAsChanged() {    when(versionRepo.findByProjectAndVersion("proj", 1)).thenReturn(Mono.just(ver(1, null)));
    when(versionRepo.findByProjectAndVersion("proj", 2)).thenReturn(Mono.just(ver(2, 1)));
    when(descriptorRepo.findAllByProjectAndVersion("proj", 1))
        .thenReturn(Flux.just(desc(1L, "ClassA", 1)));
    when(descriptorRepo.findAllByProjectAndVersion("proj", 2))
        .thenReturn(Flux.just(desc(2L, "ClassA", 2)));
    when(snapshotRepo.findByDescriptorIdAndUnitName(1L, "ClassA"))
        .thenReturn(Mono.just(snap(10L, 1L, "ClassA")));
    when(snapshotRepo.findByDescriptorIdAndUnitName(2L, "ClassA"))
        .thenReturn(Mono.just(snap(20L, 2L, "ClassA")));
    noDeletions();
    when(diffRepo.loadForSnapshots(java.util.Set.of(10L)))
        .thenReturn(Flux.just(row(10L, "hello", "METHOD_INVOCATION_PARAMETER", "LOG_MESSAGE")));
    when(diffRepo.loadForSnapshots(java.util.Set.of(20L)))
        .thenReturn(Flux.just(row(20L, "hello", "FIELD_STORE", "LOG_MESSAGE")));

    StepVerifier.create(diffService.diff("proj", 1, 2))
        .assertNext(r -> {
          var e = r.units().getFirst().changedConstants().getFirst();
          assertThat(e.value()).isEqualTo("hello");
          assertThat(e.changeKind()).isEqualTo(ConstantDiffEntry.ChangeKind.CHANGED);
        })
        .verifyComplete();
  }
}

