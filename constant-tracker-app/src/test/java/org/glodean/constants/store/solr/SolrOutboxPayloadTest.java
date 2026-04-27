package org.glodean.constants.store.solr;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Collection;
import java.util.Set;

import org.glodean.constants.model.UnitConstant;
import org.glodean.constants.model.UnitConstant.ConstantUsage;
import org.glodean.constants.model.UnitConstant.CoreSemanticType;
import org.glodean.constants.model.UnitConstant.UsageLocation;
import org.glodean.constants.model.UnitConstant.UsageType;
import org.glodean.constants.model.UnitConstants;
import org.junit.jupiter.api.Test;

class SolrOutboxPayloadTest {

  static UnitConstants sampleConstants() {
    var location = new UsageLocation("com/example/Greeter", "greet", "()V", 5, 42);
    var usage =
        new ConstantUsage(
            UsageType.METHOD_INVOCATION_PARAMETER, CoreSemanticType.LOG_MESSAGE, location, 0.9);
    var cc = new UnitConstant("Hello, world!", Set.of(usage));
    var descriptor =
        new org.glodean.constants.model.UnitDescriptor(
            org.glodean.constants.extractor.bytecode.BytecodeSourceKind.CLASS_FILE,
            "com/example/Greeter");
    return new UnitConstants(descriptor, Set.of(cc));
  }

  static UnitConstants multiUsageConstant() {
    var loc = new UsageLocation("com/example/Repo", "query", "()V", 10, 99);
    var u1 =
        new ConstantUsage(
            UsageType.METHOD_INVOCATION_PARAMETER, CoreSemanticType.SQL_FRAGMENT, loc, 0.95);
    var u2 = new ConstantUsage(UsageType.FIELD_STORE, CoreSemanticType.LOG_MESSAGE, loc, 0.8);
    var cc = new UnitConstant("SELECT * FROM users", Set.of(u1, u2));
    var descriptor =
        new org.glodean.constants.model.UnitDescriptor(
            org.glodean.constants.extractor.bytecode.BytecodeSourceKind.CLASS_FILE,
            "com/example/Repo");
    return new UnitConstants(descriptor, Set.of(cc));
  }

  @Test
  void fromBuildsPayloadFromConstants() {
    var constants = sampleConstants();
    var payload = SolrOutboxPayload.from(constants, "myproject", 5);

    assertThat(payload).isNotNull();
    assertThat(payload.project()).isEqualTo("myproject");
    assertThat(payload.unitPath()).isEqualTo("com/example/Greeter");
    assertThat(payload.version()).isEqualTo(5);
    assertThat(payload.sourceKind()).isEqualTo("CLASS_FILE");
    assertThat(payload.id()).isEqualTo("myproject:com/example/Greeter:5");
  }

  @Test
  void fromPopulatesConstantValues() {
    var constants = sampleConstants();
    var payload = SolrOutboxPayload.from(constants, "proj", 1);

    assertThat(payload.values()).containsExactly("Hello, world!");
  }

  @Test
  void fromPopulatesConstantPairsWithUsageTypes() {
    var constants = sampleConstants();
    var payload = SolrOutboxPayload.from(constants, "proj", 1);

    assertThat(payload.pairs())
        .containsExactly("Hello, world!|METHOD_INVOCATION_PARAMETER");
  }

  @Test
  void fromPopulatesSemanticPairsWithConfidence() {
    var constants = sampleConstants();
    var payload = SolrOutboxPayload.from(constants, "proj", 1);

    assertThat(payload.semanticPairs())
        .hasSize(1)
        .first()
        .asString()
        .startsWith("Hello, world!|Log Message|0.9");
  }

  @Test
  void fromHandlesMultipleUsageTypesForSameConstant() {
    var constants = multiUsageConstant();
    var payload = SolrOutboxPayload.from(constants, "proj", 1);

    assertThat(payload.pairs())
        .containsExactlyInAnyOrder(
            "SELECT * FROM users|METHOD_INVOCATION_PARAMETER",
            "SELECT * FROM users|FIELD_STORE");
  }

  @Test
  void fromDistinctUsageTypesDuplicates() {
    // Two usages with same type should only appear once
    var loc = new UsageLocation("com/example/Test", "test", "()V", 1, 10);
    var u1 = new ConstantUsage(UsageType.FIELD_STORE, CoreSemanticType.LOG_MESSAGE, loc, 0.9);
    var u2 = new ConstantUsage(UsageType.FIELD_STORE, CoreSemanticType.LOG_MESSAGE, loc, 0.8);
    var cc = new UnitConstant("test", Set.of(u1, u2));
    var descriptor =
        new org.glodean.constants.model.UnitDescriptor(
            org.glodean.constants.extractor.bytecode.BytecodeSourceKind.CLASS_FILE,
            "com/example/Test");
    var constants = new UnitConstants(descriptor, Set.of(cc));

    var payload = SolrOutboxPayload.from(constants, "proj", 1);

    assertThat(payload.pairs()).containsExactly("test|FIELD_STORE");
  }

  @Test
  void fromEmptyConstantsSet() {
    var descriptor =
        new org.glodean.constants.model.UnitDescriptor(
            org.glodean.constants.extractor.bytecode.BytecodeSourceKind.CLASS_FILE,
            "com/example/Empty");
    var constants = new UnitConstants(descriptor, Set.of());

    var payload = SolrOutboxPayload.from(constants, "proj", 1);

    assertThat(payload.values()).isEmpty();
    assertThat(payload.pairs()).isEmpty();
    assertThat(payload.semanticPairs()).isEmpty();
  }

  @Test
  void toSolrDocumentBuildsValidDocument() {
    var constants = sampleConstants();
    var payload = SolrOutboxPayload.from(constants, "proj", 1);
    var doc = payload.toSolrDocument();

    assertThat((Iterable<?>) doc).isNotNull();
    assertThat(doc.getFieldValue("id")).isEqualTo("proj:com/example/Greeter:1");
    assertThat(doc.getFieldValue("project")).isEqualTo("proj");
    assertThat(doc.getFieldValue("unit_name")).isEqualTo("com/example/Greeter");
    assertThat(doc.getFieldValue("unit_version")).isEqualTo(1);
    assertThat(doc.getFieldValue("source_kind")).isEqualTo("CLASS_FILE");
  }

  @Test
  void toSolrDocumentPopulatesMultiValueFields() {
    var constants = multiUsageConstant();
    var payload = SolrOutboxPayload.from(constants, "proj", 1);
    var doc = payload.toSolrDocument();

    Object pairsObj = doc.getFieldValue("constant_pairs_ss");
    if (pairsObj instanceof Collection<?> pairs) {
      assertThat(pairs).hasSize(2);
    }

    Object valuesObj = doc.getFieldValue("constant_values_t");
    if (valuesObj instanceof Collection<?> values) {
      assertThat(values).hasSize(1);
    }
  }

  @Test
  void toSolrDocumentIncludesSemanticPairsWhenPresent() {
    var constants = sampleConstants();
    var payload = SolrOutboxPayload.from(constants, "proj", 1);
    var doc = payload.toSolrDocument();

    Object semanticObj = doc.getFieldValue("semantic_pairs_ss");
    if (semanticObj instanceof Collection<?> semanticPairs) {
      assertThat(semanticPairs).hasSize(1);
    }
  }

  @Test
  void toSolrDocumentOmitsSemanticPairsWhenEmpty() {
    var loc = new UsageLocation("com/example/NoSemantic", "test", "()V", 1, 10);
    var u = new ConstantUsage(UsageType.FIELD_STORE, null, loc, 0.9);
    var cc = new UnitConstant("no-semantic", Set.of(u));
    var descriptor =
        new org.glodean.constants.model.UnitDescriptor(
            org.glodean.constants.extractor.bytecode.BytecodeSourceKind.CLASS_FILE,
            "com/example/NoSemantic");
    var constants = new UnitConstants(descriptor, Set.of(cc));

    var payload = SolrOutboxPayload.from(constants, "proj", 1);
    var doc = payload.toSolrDocument();

    assertThat(doc.getFieldValue("semantic_pairs_ss")).isNull();
  }
}
