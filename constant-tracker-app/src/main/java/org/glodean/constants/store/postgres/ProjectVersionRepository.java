package org.glodean.constants.store.postgres;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Mono;
public interface ProjectVersionRepository
    extends ReactiveCrudRepository<ProjectVersionEntity, Long> {
  Mono<ProjectVersionEntity> findByProjectAndVersion(String project, int version);
  Mono<ProjectVersionEntity> findTopByProjectAndStatusOrderByVersionDesc(
      String project, String status);
}