package org.glodean.constants.store.solr;

import java.util.ArrayList;
import java.util.List;
import org.apache.solr.common.SolrInputDocument;
import org.glodean.constants.model.UnitConstant;
import org.glodean.constants.model.UnitConstants;

/**
 * Serialisable snapshot of the fields needed to index a unit in Solr.
 *
 * Stored as JSON in the {@code solr_outbox} table and reconstructed by the outbox processor
 * when submitting documents to Solr. Using this record as the
 * outbox payload keeps {@code solr_outbox} rows self-contained: the processor never needs
 * to re-fetch the original {@link UnitConstants} domain object.
 */
public record SolrOutboxPayload(
    String id,
    String project,
    String unitPath,
    int version,
    String sourceKind,
    List<String> pairs,
    List<String> values,
    List<String> semanticPairs) {

  /**
   * Builds a {@code SolrOutboxPayload} from the domain model, pre-computing all Solr field
   * values so that the outbox row is self-contained.
   *
   * @param constants the extracted unit constants
   * @param project   the project identifier
   * @param version   the version number
   * @return a fully populated payload ready for JSON serialisation
   */
  public static SolrOutboxPayload from(UnitConstants constants, String project, int version) {
    String sourcePath = constants.source().path();
    String id = project + ":" + sourcePath + ":" + version;
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
          .forEach(
              u ->
                  semanticPairs.add(
                      value
                          + "|"
                          + u.semanticType().displayName()
                          + "|"
                          + String.format("%.2f", u.confidence())));
    }

    return new SolrOutboxPayload(
        id,
        project,
        sourcePath,
        version,
        constants.source().sourceKind().name(),
        pairs,
        values,
        semanticPairs);
  }

  /**
   * Converts this payload into a {@link SolrInputDocument} ready for indexing.
   *
   * @return a populated Solr input document
   */
  public SolrInputDocument toSolrDocument() {
    SolrInputDocument doc = new SolrInputDocument();
    doc.setField("id", id);
    doc.setField("project", project);
    doc.setField("unit_name", unitPath);
    doc.setField("unit_version", version);
    doc.setField("source_kind", sourceKind);
    doc.setField("constant_pairs_ss", pairs);
    doc.setField("constant_values_t", values);
    if (!semanticPairs.isEmpty()) {
      doc.setField("semantic_pairs_ss", semanticPairs);
    }
    return doc;
  }
}
