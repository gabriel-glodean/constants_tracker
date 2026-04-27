package org.glodean.constants.store.solr;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import org.glodean.constants.store.postgres.entity.SolrOutboxEntry;
import org.junit.jupiter.api.Test;

class SolrOutboxEntryTest {

  static SolrOutboxEntry sample() {
    OffsetDateTime now = OffsetDateTime.now();
    return new SolrOutboxEntry(1L, now, "proj", "com/example/Foo", 1, "{\"id\":\"proj:com/example/Foo:1\"}", 0, null, now);
  }

  // ── newEntry ───────────────────────────────────────────────────────────────

  @Test
  void newEntryHasZeroAttemptsAndNullError() {
    OffsetDateTime before = OffsetDateTime.now();
    var entry = SolrOutboxEntry.newEntry("proj", "com/example/Foo", 1, "{}");

    assertThat(entry.id()).isNull();
    assertThat(entry.project()).isEqualTo("proj");
    assertThat(entry.unitPath()).isEqualTo("com/example/Foo");
    assertThat(entry.version()).isEqualTo(1);
    assertThat(entry.payloadJson()).isEqualTo("{}");
    assertThat(entry.attempts()).isEqualTo(0);
    assertThat(entry.lastError()).isNull();
    assertThat(entry.nextAttemptAt()).isAfterOrEqualTo(before);
  }

  // ── withFailure ────────────────────────────────────────────────────────────

  @Test
  void withFailureIncrementsAttemptsAndSetsErrorMessage() {
    var original = sample();
    var failed = original.withFailure("Solr unavailable");

    assertThat(failed.id()).isEqualTo(original.id());
    assertThat(failed.project()).isEqualTo(original.project());
    assertThat(failed.unitPath()).isEqualTo(original.unitPath());
    assertThat(failed.version()).isEqualTo(original.version());
    assertThat(failed.payloadJson()).isEqualTo(original.payloadJson());
    assertThat(failed.attempts()).isEqualTo(1);
    assertThat(failed.lastError()).isEqualTo("Solr unavailable");
  }

  @Test
  void withFailureSchedulesNextAttemptWithExponentialBackoff() {
    var entry = sample(); // attempts=0
    var failed = entry.withFailure("error"); // attempts=1, delay=2^1=2s

    assertThat(failed.nextAttemptAt())
        .isAfterOrEqualTo(OffsetDateTime.now().plusSeconds(1))
        .isBefore(OffsetDateTime.now().plusSeconds(4));
  }

  @Test
  void withFailureBackoffCapsAtOneHour() {
    // After 13+ failures, 2^attempts > 3600; verify cap is applied
    var entry = sample();
    for (int i = 0; i < 13; i++) {
      entry = entry.withFailure("error");
    }
    // delay should be capped at 3600 seconds, so nextAttemptAt ≤ now + 3601s
    assertThat(entry.nextAttemptAt()).isBefore(OffsetDateTime.now().plusSeconds(3602));
    assertThat(entry.attempts()).isEqualTo(13);
  }

  @Test
  void withFailureChainedMultipleTimesAccumulatesAttempts() {
    var entry = sample();
    entry = entry.withFailure("e1");
    entry = entry.withFailure("e2");
    entry = entry.withFailure("e3");

    assertThat(entry.attempts()).isEqualTo(3);
    assertThat(entry.lastError()).isEqualTo("e3");
  }
}
