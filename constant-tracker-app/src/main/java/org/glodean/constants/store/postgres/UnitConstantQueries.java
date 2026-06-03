package org.glodean.constants.store.postgres;
import io.r2dbc.spi.Row;
import io.r2dbc.spi.RowMetadata;
import java.util.function.BiFunction;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.glodean.constants.dto.UnitListingRequest;
import org.glodean.constants.model.UnitConstant;
import org.glodean.constants.store.SemanticTypeStore;
import org.glodean.constants.store.postgres.repository.projection.ConstantDetailRow;
import org.glodean.constants.store.postgres.repository.projection.UnitConstantsCountRow;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
/**
 * {@code DatabaseClient}-based query component for cross-entity reads that span
 * {@code unit_descriptors -> unit_snapshots -> unit_constants -> constant_usages}.
 *
 * <p>These queries do not belong to any single entity repository because they join across
 * the full constant storage hierarchy.  Using {@link DatabaseClient} directly also gives
 * safe null-filter handling: parameters are only bound (and clauses only appended) when
 * a filter value is actually supplied, avoiding the {@code :param IS NULL OR col = :param}
 * trick that is unreliable with some R2DBC drivers.
 *
 * <p>{@link SemanticTypeStore} is used to resolve a caller-supplied {@code semanticType} name
 * (as returned by {@code /metadata/semantic-types}) into the two DB columns
 * {@code semantic_type_kind} and {@code semantic_type_name}.
 *
 * <p>Two queries are provided:
 * <ul>
 *   <li>{@link #unitCounts} - grouped unit-level counts, used by {@code GET /units} when
 *       at least one usage filter is present.</li>
 *   <li>{@link #constantDetails} - flat per-usage rows, used by {@code GET /units/constants}
 *       for bulk LLM-discovery inspection.</li>
 * </ul>
 */
@Component
public class UnitConstantQueries {
  private static final Logger log = LogManager.getLogger(UnitConstantQueries.class);
  // GROUP BY + ORDER BY live in UNIT_COUNTS_SUFFIX — filters are appended between the two.
  private static final String UNIT_COUNTS_PREFIX = """
      SELECT ds.path               AS path,
             ds.unit_name          AS name,
             COUNT(DISTINCT c.id)  AS constants
      FROM (
        SELECT d.path, s.id AS snapshot_id, s.unit_name
        FROM unit_descriptors d
        JOIN unit_snapshots   s ON s.descriptor_id = d.id
        WHERE d.project = :project AND d.version = :version
      ) ds
      JOIN unit_constants  c  ON c.snapshot_id  = ds.snapshot_id
      JOIN constant_usages cu ON cu.constant_id = c.id
      """;
  private static final String UNIT_COUNTS_SUFFIX =
      " GROUP BY ds.path, ds.unit_name ORDER BY ds.path, ds.unit_name LIMIT :limit OFFSET :offset";
  // GROUP BY + ORDER BY live in CONSTANT_DETAILS_SUFFIX — filters are appended between the two.
  private static final String CONSTANT_DETAILS_PREFIX = """
      SELECT uc.constant_value             AS constantValue,
             uc.constant_value_type        AS constantValueType,
             cu.structural_type            AS structuralType,
             cu.semantic_type_name         AS semanticType,
             cu.confidence                 AS confidence,
             cu.metadata                   AS metadata,
             COUNT(*)                      AS occurrenceCount
      FROM unit_constants  uc
      JOIN constant_usages cu ON cu.constant_id = uc.id
      WHERE uc.snapshot_id IN (
        SELECT s.id
        FROM unit_snapshots   s
        JOIN unit_descriptors d ON d.id = s.descriptor_id
        WHERE d.project = :project AND d.version = :version
      )
      """;
  private static final String CONSTANT_DETAILS_SUFFIX =
      " GROUP BY uc.constant_value, uc.constant_value_type, cu.structural_type,"
      + " cu.semantic_type_name, cu.confidence, cu.metadata"
      + " ORDER BY occurrenceCount DESC, uc.constant_value LIMIT :limit OFFSET :offset";
  private final DatabaseClient db;
  private final SemanticTypeStore semanticTypeStore;
  public UnitConstantQueries(DatabaseClient db, SemanticTypeStore semanticTypeStore) {
    this.db = db;
    this.semanticTypeStore = semanticTypeStore;
  }
  /**
   * Returns one row per unit snapshot containing at least one constant usage that matches the
   * filters in {@code req}.  Any {@code null} filter is omitted from the {@code WHERE} clause.
   *
   * @param req             query parameters - filters, page, pageSize
   * @param defaultPageSize per-endpoint page-size default applied when {@code req.pageSize()} is null
   */
  public Flux<UnitConstantsCountRow> unitCounts(UnitListingRequest req, int defaultPageSize) {
    return query(req, defaultPageSize, UNIT_COUNTS_PREFIX, UNIT_COUNTS_SUFFIX,
        (row, ignore) -> new UnitConstantsCountRow(
            row.get("path",      String.class),
            row.get("name",      String.class),
            row.get("constants", Long.class)));
  }
  /**
   * Returns a paged flat list of constant values together with their usage observations.
   *
   * <p>One row per {@code (unit_constant, constant_usage)} pair - a constant with multiple usages
   * appears multiple times.  Callers group by {@code constantValue + structuralType} as needed.
   *
   * @param req             query parameters - filters, page, pageSize
   * @param defaultPageSize per-endpoint page-size default applied when {@code req.pageSize()} is null
   */
  public Flux<ConstantDetailRow> constantDetails(UnitListingRequest req, int defaultPageSize) {
    return query(req, defaultPageSize, CONSTANT_DETAILS_PREFIX, CONSTANT_DETAILS_SUFFIX,
        (row, ignore) -> new ConstantDetailRow(
            row.get("constantValue",   String.class),
            row.get("constantValueType", String.class),
            row.get("structuralType",  String.class),
            row.get("semanticType",    String.class),
            row.get("confidence",      Double.class),
            row.get("metadata",        String.class),
            row.get("occurrenceCount", Long.class)));
  }
  // -- private -----------------------------------------------------------------
  /**
   * Resolved pair of {@code semantic_type_kind} and {@code semantic_type_name} derived from a
   * single caller-supplied semantic type name via {@link SemanticTypeStore}.
   */
  private record ResolvedSemantic(String kind, String name) {}
  /**
   * Looks up {@code semanticType} in the store and returns the corresponding DB column values.
   * Returns {@code null} when {@code semanticType} is {@code null} (no filter requested).
   * When the name is not found in the store a warning is logged and a sentinel kind value is
   * returned so the query produces zero rows rather than silently scanning everything.
   */
  private ResolvedSemantic resolveSemantic(String semanticType) {
    if (semanticType == null) return null;
    return semanticTypeStore.getSupportedSemanticTypes().stream()
        .filter(t -> {
          if (t instanceof UnitConstant.CoreSemanticType c)   return c.name().equals(semanticType);
          if (t instanceof UnitConstant.CustomSemanticType cu) return cu.category().equals(semanticType);
          return false;
        })
        .findFirst()
        .map(t -> t instanceof UnitConstant.CoreSemanticType
            ? new ResolvedSemantic("CORE",   semanticType)
            : new ResolvedSemantic("CUSTOM", semanticType))
        .orElseGet(() -> {
          log.warn("Unknown semanticType filter '{}' - query will return no rows", semanticType);
          return new ResolvedSemantic("__UNKNOWN__", semanticType);
        });
  }
  private <R> Flux<R> query(
      UnitListingRequest req,
      int defaultPageSize,
      String sqlPrefix,
      String sqlSuffix,
      BiFunction<Row, RowMetadata, R> mapper) {
    int  limit    = req.effectivePageSize(defaultPageSize);
    long offset   = req.offset(defaultPageSize);
    ResolvedSemantic semantic = resolveSemantic(req.semanticType());
    var sql = new StringBuilder(sqlPrefix);
    if (req.structuralType()    != null) sql.append(" AND cu.structural_type    = :structuralType");
    if (semantic                != null) sql.append(" AND cu.semantic_type_kind = :semanticTypeKind"
                                                  + " AND cu.semantic_type_name = :semanticTypeName");
    if (req.constantValueType() != null) sql.append(" AND uc.constant_value_type = :constantValueType");
    sql.append(sqlSuffix);
    log.debug("UnitConstantQueries sql: {}", sql);
    var spec = db.sql(sql.toString())
        .bind("project", req.project().strip())
        .bind("version", req.version())
        .bind("limit",   limit)
        .bind("offset",  offset);
    if (req.structuralType()    != null) spec = spec.bind("structuralType",      req.structuralType());
    if (semantic                != null) {
      spec = spec.bind("semanticTypeKind", semantic.kind());
      spec = spec.bind("semanticTypeName", semantic.name());
    }
    if (req.constantValueType() != null) spec = spec.bind("constantValueType", req.constantValueType());
    return spec.map(mapper).all();
  }
}
