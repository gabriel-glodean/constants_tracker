package org.glodean.constants.web.endpoints;

import org.glodean.constants.dto.ProjectDiffResponse;
import org.glodean.constants.services.DiffService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/**
 * Constant-diff endpoint.
 *
 * <p>Example:
 * <pre>
 * GET /project/my-app/diff?from=1&to=2
 * </pre>
 *
 * <p>Returns a list of {@link org.glodean.constants.dto.UnitDiff} entries — one per unit that
 * changed, was added, or was removed. Units with no changes are omitted.
 * The diff is inheritance-aware: if a unit was not re-uploaded in a version it is resolved
 * from the parent chain, and deletions are fully respected.
 */
@RestController
@RequestMapping("/project")
public class DiffController {

  private final DiffService diffService;

  public DiffController(@Autowired DiffService diffService) {
    this.diffService = diffService;
  }

  @GetMapping("/{project}/diff")
  public Mono<ResponseEntity<ProjectDiffResponse>> diff(
      @PathVariable("project") String project,
      @RequestParam("from") int from,
      @RequestParam("to") int to) {
    return diffService
        .diff(project, from, to)
        .map(ResponseEntity::ok);
  }
}
