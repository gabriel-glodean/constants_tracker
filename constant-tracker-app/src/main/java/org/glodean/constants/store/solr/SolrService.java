package org.glodean.constants.store.solr;

import static org.glodean.constants.store.Constants.DATA_LOCATION;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.solr.client.solrj.impl.HttpSolrClientBase;
import org.apache.solr.client.solrj.request.QueryRequest;
import org.apache.solr.client.solrj.request.UpdateRequest;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;
import org.apache.solr.common.SolrInputDocument;
import org.apache.solr.common.params.ModifiableSolrParams;
import org.apache.solr.common.util.NamedList;
import org.glodean.constants.dto.FuzzySearchHit;
import org.glodean.constants.dto.FuzzySearchResponse;
import org.glodean.constants.model.UnitConstant;
import org.glodean.constants.model.UnitConstants;
import org.glodean.constants.store.UnitConstantsStore;
import io.micrometer.core.annotation.Timed;
import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * Solr-backed implementation of {@link org.glodean.constants.store.UnitConstantsStore} that stores a simplified flat document
 * per unit snapshot: {@code id}, {@code project}, {@code unit_name}, {@code unit_version}, and
 * a multi-valued {@code constant_pairs_ss} field with entries of the form
 * {@code "<value>|<USAGE_TYPE>"}.
 *
 * <p>This service is used by {@link org.glodean.constants.store.CompositeUnitConstantsStore} as
 * the search-index side-car. Version assignment and reads live in PostgreSQL.
 */
@Service
public class SolrService implements UnitConstantsStore {
  private static final Logger logger = LogManager.getLogger(SolrService.class);

  private final HttpSolrClientBase solrClient;

  /**
   * Creates a {@code SolrService} backed by the given HTTP Solr client.
   *
   * @param solrClient the pre-configured Solr client (URL set in {@code constants.solr.url})
   */
  public SolrService(@Autowired HttpSolrClientBase solrClient) {
    this.solrClient = solrClient;
  }

  /** Throws {@link UnsupportedOperationException} — version assignment is owned by the composite. */
  @Override
  public Mono<UnitConstants> store(UnitConstants constants, String project) {
    throw new UnsupportedOperationException(
        "SolrService does not manage version assignment; use CompositeUnitConstantsStore");
  }

  /**
   * Stores a simplified flat Solr document for the given class snapshot.
   *
   * <p>The document contains the fields {@code id}, {@code project}, {@code unit_name},
   * {@code unit_version}, {@code source_kind}, a multi-valued {@code constant_pairs_ss} field
   * ({@code "<value>|<USAGE_TYPE>"}), and a tokenised {@code constant_values_t} field used
   * for full-text search.
   *
     * @param constants the unit constants to index
   * @param project   the project identifier
   * @param version   the version number
   * @return a {@link reactor.core.publisher.Mono} emitting the original {@code constants} on success
   */
  @Timed(value = "solr.store", description = "Time to store a unit in Solr")
  @Override
  public Mono<UnitConstants> store(UnitConstants constants, String project, int version) {
    String sourcePath = constants.source().path();
    logger.atInfo().log("Storing to Solr (simplified): {} project={} version={}", sourcePath, project, version);
    var id = project + ":" + sourcePath + ":" + version;

    List<String> pairs = new ArrayList<>();
    List<String> values = new ArrayList<>();
    List<String> semanticPairs = new ArrayList<>();
    for (var constant : constants.constants()) {
      String value = constant.value().toString();
      values.add(value);
      constant.usages().stream()
          .map(UnitConstant.ConstantUsage::structuralType)
          .distinct()
          .forEach(usage -> pairs.add(value + "|" + usage.name()));
      constant.usages().stream()
          .filter(u -> u.semanticType() != null)
          .forEach(u -> semanticPairs.add(
              value + "|" + u.semanticType().displayName() + "|"
                  + String.format("%.2f", u.confidence())));
    }

    SolrInputDocument doc = new SolrInputDocument();
    doc.setField("id", id);
    doc.setField("project", project);
    doc.setField("unit_name", sourcePath);
    doc.setField("unit_version", version);
    doc.setField("source_kind", constants.source().sourceKind().name());
    doc.setField("constant_pairs_ss", pairs);
    doc.setField("constant_values_t", values);
    if (!semanticPairs.isEmpty()) {
      doc.setField("semantic_pairs_ss", semanticPairs);
    }

    var request = new UpdateRequest();
    request.add(doc);
    request.setParam("commit", "true");

    return Mono.fromFuture(solrClient.requestAsync(request, DATA_LOCATION)).map(_ -> constants);
  }

  /**
   * Full-text / fuzzy search across constant values.
   *
   * <p>Uses the eDisMax query parser with two search fields:
   * <ul>
   *   <li>{@code constant_values_t} — standard tokenised field (BM25 scoring)</li>
   *   <li>{@code constant_values_ngram} — edge n-gram field for prefix / partial matches</li>
   * </ul>
   *
   * <p>The caller provides a plain-text {@code term}; Lucene query syntax is built internally
   * via {@link SearchQueryBuilder}. Special characters are escaped automatically and fuzzy
   * suffixes ({@code ~N}) are appended per-token when {@code editDistance > 0}.
   *
   * @param project      project to restrict results to (pass {@code "*"} for cross-project search)
   * @param term         plain-text search term
   * @param editDistance fuzzy tolerance per token: {@code 0} = exact, {@code 1} = one typo,
   *                     {@code 2} = two typos
   * @param maxRows      maximum number of hits to return
   * @return ranked {@link FuzzySearchResponse}
   */
  @Timed(value = "solr.fuzzy_search", description = "Time to execute fuzzy search")
  @Override
  public Mono<FuzzySearchResponse> fuzzySearch(
      String project, String term, int editDistance, int maxRows) {
    String queryText = SearchQueryBuilder.build(term, editDistance);
    logger.atInfo().log(
        "Fuzzy search: project={} term={} editDistance={} rows={} query={}",
        project, term, editDistance, maxRows, queryText);

    QueryRequest query = getQueryRequest(project, maxRows, queryText);
    query.setPath("/select");

    return Mono.fromFuture(solrClient.requestAsync(query, DATA_LOCATION))
        .map(SolrService::parseFuzzyResponse);
  }

  private static @NonNull QueryRequest getQueryRequest(String project, int maxRows, String queryText) {
    ModifiableSolrParams params = new ModifiableSolrParams();
    params.set("q", queryText);
    params.set("defType", "edismax");
    params.set("qf", "constant_values_t^2.0 constant_values_ngram^1.0");
    params.set("pf", "constant_values_t^3.0"); // phrase boost
    if (!"*".equals(project)) {
      params.set("fq", "project:\"" + project + "\"");
    }
    params.set("fl", "project,unit_name,unit_version,source_kind,constant_values_t,semantic_pairs_ss");
    params.set("rows", maxRows);

    return new QueryRequest(params);
  }

  /**
   * Looks up constant pairs for an exact document key ({@code project:className:version}).
   *
   * <p>Issues a Solr {@code id} query and parses the {@code constant_pairs_ss} multi-value
   * field into the result map.
   *
   * @param key composite key in the format {@code "<project>:<className>:<version>"}
   * @return a {@link reactor.core.publisher.Mono} emitting the constant-to-usage-type map;
   *         errors with {@link IllegalArgumentException} if the document is not found
   */
  @Timed(value = "solr.find", description = "Time to find a unit in Solr")
  @Override
  public Mono<Map<Object, Collection<UnitConstant.UsageType>>> find(String key) {
    ModifiableSolrParams params = new ModifiableSolrParams();
    params.set("q", "id:\"" + key + "\"");
    params.set("fl", "constant_pairs_ss");
    params.set("rows", 1);
    QueryRequest query = new QueryRequest(params);
    query.setPath("/select");
    logger.atInfo().log("Solr search query for key={}", key);
    return Mono.fromFuture(solrClient.requestAsync(query, DATA_LOCATION))
        .map(SolrService::parsePairs);
  }

  /**
   * Parses the raw Solr response into a constant-value-to-usage-type map.
   *
   * <p>Expects the response to contain a {@code response} block with at least one document
   * that has a {@code constant_pairs_ss} multi-value field. Each pair has the format
   * {@code "<value>|<USAGE_TYPE>"}.
   *
   * @param list the raw Solr {@link NamedList} response
    * @return map of constant value ({@link String}) to a set of {@link org.glodean.constants.model.UnitConstant.UsageType}
   * @throws IllegalArgumentException if the response contains no matching documents
   */
  private static Map<Object, Collection<UnitConstant.UsageType>> parsePairs(
      NamedList<Object> list) {
    Map<Object, Collection<UnitConstant.UsageType>> result = HashMap.newHashMap(10);
    if (list.get("response") instanceof Collection<?> response) {
      if (response.isEmpty()) {
        throw new IllegalArgumentException("Unknown unit!");
      }
      for (var doc : response) {
        if (doc instanceof Map<?, ?> solrDoc) {
          Object raw = solrDoc.get("constant_pairs_ss");
          if (raw instanceof Collection<?> pairsList) {
            for (var item : pairsList) {
              if (item instanceof String pairStr) {
                int sep = pairStr.lastIndexOf('|');
                if (sep > 0) {
                  String value = pairStr.substring(0, sep);
                   UnitConstant.UsageType type =
                       UnitConstant.UsageType.valueOf(pairStr.substring(sep + 1));
                   result
                       .computeIfAbsent(value, _ -> EnumSet.noneOf(UnitConstant.UsageType.class))
                       .add(type);
                }
              }
            }
          }
        }
      }
    }
    return result;
  }
  
  /**
   * Parses a Solr query response into a {@link FuzzySearchResponse}.
   *
   * <p>Reads the {@link SolrDocumentList} from the {@code "response"} key, maps each
   * document to a {@link FuzzySearchHit}, and returns the total-found count alongside
   * the hit list.
   *
   * @param list the raw Solr {@link NamedList} response
   * @return a {@link FuzzySearchResponse} with hits and total count; empty response if
   *         the response block is missing or not a {@link SolrDocumentList}
   */
  private static FuzzySearchResponse parseFuzzyResponse(NamedList<Object> list) {
    if (!(list.get("response") instanceof SolrDocumentList docs)) {
      return new FuzzySearchResponse(List.of(), 0L);
    }
    long numFound = docs.getNumFound();
    List<FuzzySearchHit> hits = new ArrayList<>(docs.size());
    for (SolrDocument doc : docs) {
      String project = (String) doc.getFieldValue("project");
      String unitName = (String) doc.getFieldValue("unit_name");
      Object rawVersion = doc.getFieldValue("unit_version");
      int version = rawVersion instanceof Number n ? n.intValue() : 0;
      String sourceKind = (String) doc.getFieldValue("source_kind");
      Collection<Object> rawValues = doc.getFieldValues("constant_values_t");
      List<String> values = rawValues == null
          ? List.of()
          : rawValues.stream().map(Object::toString).toList();
      Collection<Object> rawSemantic = doc.getFieldValues("semantic_pairs_ss");
      List<String> semanticPairs = rawSemantic == null
          ? List.of()
          : rawSemantic.stream().map(Object::toString).toList();
      hits.add(new FuzzySearchHit(project, unitName, version, sourceKind, values, semanticPairs));
    }
    return new FuzzySearchResponse(hits, numFound);
  }
}
