package org.glodean.constants.web.endpoints;

import jakarta.validation.constraints.NotBlank;
import org.glodean.constants.dto.FuzzySearchResponse;
import org.glodean.constants.store.UnitConstantsStore;
import org.glodean.constants.web.validation.ValidProjectName;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/**
 * REST endpoint for fuzzy / full-text search over indexed constant values.
 *
 * <p>Delegates to the Solr-backed side of {@link org.glodean.constants.store.UnitConstantsStore} via
 * {@link org.glodean.constants.store.CompositeUnitConstantsStore#fuzzySearch}.
 * No Lucene syntax knowledge is required from the caller — the search term is a plain string
 * and fuzzy tolerance is expressed as an integer edit distance.
 *
 * <p><b>Example calls:</b>
 * <pre>
 * # Find all classes in "my-app" whose constants contain "SELECT" (exact)
 * GET /search?project=my-app&amp;term=SELECT&amp;fuzzy=0
 *
 * # Fuzzy match – tolerates one typo ("SELCT" → "SELECT")
 * GET /search?project=my-app&amp;term=SELCT
 *
 * # More tolerant – two character edits per token
 * GET /search?project=my-app&amp;term=SELCT&amp;fuzzy=2
 *
 * # Prefix / partial – edge n-gram field kicks in for short terms
 * GET /search?project=my-app&amp;term=htt&amp;rows=20
 *
 * # Cross-project search (project omitted or empty → all projects)
 * GET /search?term=log4j
 * </pre>
 */
@Validated
@RestController
@RequestMapping("/search")
public class FuzzySearchController {

    private final UnitConstantsStore store;

    @Autowired
    public FuzzySearchController(UnitConstantsStore store) {
        this.store = store;
    }

    /**
     * Performs a fuzzy / full-text search over indexed constant values.
     *
     * @param project      restrict results to this project; omit or leave empty to search all projects
     * @param term         plain-text search term – no Lucene syntax required
     * @param editDistance fuzzy tolerance per token: {@code 0} = exact, {@code 1} = one typo
     *                     (default), {@code 2} = two typos
     * @param rows         maximum number of hits (default 10, max 100)
     * @return 200 OK with {@link FuzzySearchResponse}, or 400 if parameters are invalid,
     * or 500 on search-engine failure
     */
    @GetMapping
    public Mono<ResponseEntity<FuzzySearchResponse>> search(
            @ValidProjectName @RequestParam(value = "project", required = false) String project,
            @NotBlank @RequestParam("term") String term,
            @RequestParam(value = "fuzzy", defaultValue = "1") int editDistance,
            @RequestParam(value = "rows", defaultValue = "10") int rows) {

        if (editDistance < 0 || editDistance > 2) {
            throw new IllegalArgumentException("'fuzzy' must be 0, 1, or 2");
        }

        int cappedRows = Math.clamp(rows, 1, 100);
        // null / empty project → no fq filter in Solr (search all projects)
        String resolvedProject = (project == null || project.isBlank()) ? null : project.strip();

        return store.fuzzySearch(resolvedProject, term.strip(), editDistance, cappedRows)
                .map(ResponseEntity::ok);
    }
}
