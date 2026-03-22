package org.glodean.constants.store.solr;

/**
 * Translates plain-text user input into an eDisMax-compatible query string for Solr.
 *
 * <p>All Lucene / eDisMax special characters in the user's term are escaped before use,
 * preventing query-injection and shielding callers from search-engine syntax entirely.
 *
 * <p><b>Fuzzy matching</b> is controlled by {@code editDistance}:
 * <ul>
 *   <li>{@code 0} — exact token match (no fuzzy suffix)</li>
 *   <li>{@code 1} — tolerates one character edit per token (good default for short terms)</li>
 *   <li>{@code 2} — tolerates two character edits per token (broader, may over-match)</li>
 * </ul>
 *
 * <p>Multi-word inputs are split on whitespace; the fuzzy suffix is appended to each token
 * individually so that eDisMax scores each token independently.
 *
 * <p>Examples:
 * <pre>
 * build("SELECT",        0) → "SELECT"
 * build("SELECT",        1) → "SELECT~1"
 * build("hello world",   1) → "hello~1 world~1"
 * build("key:value",     0) → "key\:value"
 * build("100% match",    1) → "100\%~1 match~1"   // % is not special, but : and ~ are
 * </pre>
 */
public final class SearchQueryBuilder {

  /**
   * Characters that carry special meaning inside a Lucene / eDisMax query.
   * Each occurrence in user input is prefixed with a backslash.
   */
  private static final String SPECIAL_CHARS = "\\+-&|!(){}[]^\"~*?:/";

  private SearchQueryBuilder() {}

  /**
   * Builds an eDisMax query string from a plain-text user term.
   *
   * @param term         raw user input; must not be {@code null}
   * @param editDistance {@code 0} for exact, {@code 1}–{@code 2} for fuzzy tolerance per token
   * @return escaped eDisMax query string ready to be passed to Solr's {@code q} parameter
   * @throws IllegalArgumentException if {@code editDistance} is outside the range 0–2
   */
  public static String build(String term, int editDistance) {
    if (editDistance < 0 || editDistance > 2) {
      throw new IllegalArgumentException(
          "editDistance must be 0, 1, or 2 – got: " + editDistance);
    }

    String[] tokens = term.trim().split("\\s+");
    StringBuilder sb = new StringBuilder();

    for (int i = 0; i < tokens.length; i++) {
      if (i > 0) sb.append(' ');
      String escaped = escape(tokens[i]);
      sb.append(escaped);
      if (editDistance > 0 && !escaped.isEmpty()) {
        sb.append('~').append(editDistance);
      }
    }

    return sb.toString();
  }

  // ── internals ────────────────────────────────────────────────────────────

  private static String escape(String token) {
    StringBuilder sb = new StringBuilder(token.length() * 2);
    for (char c : token.toCharArray()) {
      if (SPECIAL_CHARS.indexOf(c) >= 0) {
        sb.append('\\');
      }
      sb.append(c);
    }
    return sb.toString();
  }
}

