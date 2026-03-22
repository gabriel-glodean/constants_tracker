package org.glodean.constants.store.solr;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class SearchQueryBuilderTest {

  // ── exact (editDistance = 0) ──────────────────────────────────────────────

  @Test
  void exactMatchReturnsTermUnchanged() {
    assertThat(SearchQueryBuilder.build("SELECT", 0)).isEqualTo("SELECT");
  }

  @Test
  void exactMatchMultipleTokens() {
    assertThat(SearchQueryBuilder.build("hello world", 0)).isEqualTo("hello world");
  }

  // ── fuzzy suffixes ────────────────────────────────────────────────────────

  @Test
  void editDistanceOneAppendsSuffix() {
    assertThat(SearchQueryBuilder.build("SELECT", 1)).isEqualTo("SELECT~1");
  }

  @Test
  void editDistanceTwoAppendsSuffix() {
    assertThat(SearchQueryBuilder.build("SELCT", 2)).isEqualTo("SELCT~2");
  }

  @Test
  void fuzzyAppliedToEveryToken() {
    assertThat(SearchQueryBuilder.build("hello world", 1)).isEqualTo("hello~1 world~1");
  }

  @Test
  void extraWhitespaceBetweenTokensIsNormalised() {
    assertThat(SearchQueryBuilder.build("  foo   bar  ", 1)).isEqualTo("foo~1 bar~1");
  }

  // ── character escaping ────────────────────────────────────────────────────

  @ParameterizedTest(name = "escape [{0}] → [{1}]")
  @CsvSource({
    "key:value,    key\\:value",
    "a+b,          a\\+b",
    "a-b,          a\\-b",
    "a&&b,         a\\&\\&b",
    "a||b,         a\\|\\|b",
    "a!b,          a\\!b",
    "(test),       \\(test\\)",
    "{test},       \\{test\\}",
    "[test],       \\[test\\]",
    "a^2,          a\\^2",
    "\"quoted\",   \\\"quoted\\\"",
    "fuzzy~1,      fuzzy\\~1",
    "wild*card,    wild\\*card",
    "sin?le,       sin\\?le",
    "a/b,          a\\/b",
  })
  void specialCharactersAreEscaped(String input, String expected) {
    // fuzzy=0 so no suffix is added – we only test escaping here
    assertThat(SearchQueryBuilder.build(input.trim(), 0)).isEqualTo(expected.trim());
  }

  @Test
  void escapedSpecialCharsReceiveFuzzySuffix() {
    // "key:value" → "key\:value" with fuzzy → "key\:value~1"
    assertThat(SearchQueryBuilder.build("key:value", 1)).isEqualTo("key\\:value~1");
  }

  // ── validation ────────────────────────────────────────────────────────────

  @Test
  void editDistanceAboveTwoThrows() {
    assertThatThrownBy(() -> SearchQueryBuilder.build("test", 3))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("3");
  }

  @Test
  void negativeEditDistanceThrows() {
    assertThatThrownBy(() -> SearchQueryBuilder.build("test", -1))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("-1");
  }
}

