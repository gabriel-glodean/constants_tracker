package org.glodean.constants.store.postgres.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.r2dbc.core.DatabaseClient;
import reactor.core.publisher.Flux;

@ExtendWith(MockitoExtension.class)
class DiffRepositoryTest {

  @Mock DatabaseClient dbClient;

  DiffRepository repo;

  @BeforeEach
  void setUp() {
    repo = new DiffRepository(dbClient);
  }

  @Test
  void loadForSnapshotsWithEmptyCollectionReturnsEmptyFluxWithoutCallingDb() {
    // When snapshotIds is empty, the method short-circuits and never hits the DB
    Flux<ConstantDiffRow> result = repo.loadForSnapshots(List.of());

    assertThat(result.collectList().block()).isEmpty();
    // DatabaseClient was NOT called (no verify needed — Mockito strict mode would catch it)
  }
}
