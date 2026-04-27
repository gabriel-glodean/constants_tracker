package org.glodean.constants.store.postgres.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;

class SolrOutboxDeadEntryTest {

  @Test
  void recordAccessorsReturnConstructorValues() {
    OffsetDateTime now = OffsetDateTime.now();
    var entry = new SolrOutboxDeadEntry(
        10L, now, "proj", "com/example/Foo", 3, "{}", "Solr was down");

    assertThat(entry.id()).isEqualTo(10L);
    assertThat(entry.failedAt()).isEqualTo(now);
    assertThat(entry.project()).isEqualTo("proj");
    assertThat(entry.unitPath()).isEqualTo("com/example/Foo");
    assertThat(entry.version()).isEqualTo(3);
    assertThat(entry.payloadJson()).isEqualTo("{}");
    assertThat(entry.lastError()).isEqualTo("Solr was down");
  }

  @Test
  void recordEqualityAndHashCode() {
    OffsetDateTime now = OffsetDateTime.now();
    var a = new SolrOutboxDeadEntry(1L, now, "p", "path", 1, "{}", "err");
    var b = new SolrOutboxDeadEntry(1L, now, "p", "path", 1, "{}", "err");

    assertThat(a).isEqualTo(b);
    assertThat(a.hashCode()).isEqualTo(b.hashCode());
  }

  @Test
  void recordToStringContainsKeyFields() {
    OffsetDateTime now = OffsetDateTime.now();
    var entry = new SolrOutboxDeadEntry(5L, now, "myproj", "com/Foo", 2, "{}", "timeout");
    String str = entry.toString();

    assertThat(str).contains("myproj");
    assertThat(str).contains("com/Foo");
  }
}
