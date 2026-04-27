package org.glodean.constants.services;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.glodean.constants.dto.UsageDetail;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link DiffService#canonicallySorted(List)}.
 */
@DisplayName("DiffService – canonicallySorted")
class DiffServiceCanonicalSortTest {

  private static UsageDetail usage(String structural, String className, String method,
      Integer lineNumber, double confidence) {
    return new UsageDetail(structural, "CORE", "UNKNOWN", null, className, method, lineNumber,
        confidence);
  }

  @Test
  @DisplayName("Empty list returns empty list")
  void emptyList_returnsEmpty() {
    assertThat(DiffService.canonicallySorted(List.of())).isEmpty();
  }

  @Test
  @DisplayName("Single element list is unchanged")
  void singleElement_unchanged() {
    var u = usage("METHOD_INVOCATION_PARAMETER", "Foo", "bar", 10, 1.0);
    assertThat(DiffService.canonicallySorted(List.of(u))).containsExactly(u);
  }

  @Test
  @DisplayName("Sorts primarily by structuralType")
  void sortsByStructuralType() {
    var a = usage("STRING_CONCATENATION_MEMBER", "Foo", "bar", 5, 1.0);
    var b = usage("METHOD_INVOCATION_PARAMETER", "Foo", "bar", 5, 1.0);

    var result = DiffService.canonicallySorted(List.of(a, b));

    assertThat(result).containsExactly(b, a); // METHOD < STRING lexicographically
  }

  @Test
  @DisplayName("Sorts by locationClassName as second key")
  void sortsByClassName() {
    var a = usage("METHOD_INVOCATION_PARAMETER", "com.B", "m", 1, 1.0);
    var b = usage("METHOD_INVOCATION_PARAMETER", "com.A", "m", 1, 1.0);

    var result = DiffService.canonicallySorted(List.of(a, b));

    assertThat(result).containsExactly(b, a);
  }

  @Test
  @DisplayName("Sorts by locationMethodName as third key")
  void sortsByMethodName() {
    var a = usage("METHOD_INVOCATION_PARAMETER", "Foo", "z", 1, 1.0);
    var b = usage("METHOD_INVOCATION_PARAMETER", "Foo", "a", 1, 1.0);

    var result = DiffService.canonicallySorted(List.of(a, b));

    assertThat(result).containsExactly(b, a);
  }

  @Test
  @DisplayName("Sorts by locationLineNumber as fourth key; nulls go last")
  void sortsByLineNumber_nullsLast() {
    var withNull = usage("METHOD_INVOCATION_PARAMETER", "Foo", "bar", null, 1.0);
    var line5    = usage("METHOD_INVOCATION_PARAMETER", "Foo", "bar", 5, 1.0);
    var line20   = usage("METHOD_INVOCATION_PARAMETER", "Foo", "bar", 20, 1.0);

    var result = DiffService.canonicallySorted(List.of(withNull, line20, line5));

    assertThat(result).containsExactly(line5, line20, withNull);
  }

  @Test
  @DisplayName("Sorts by confidence descending as tiebreaker")
  void sortsByConfidenceDescending() {
    var low  = usage("METHOD_INVOCATION_PARAMETER", "Foo", "bar", 5, 0.5);
    var high = usage("METHOD_INVOCATION_PARAMETER", "Foo", "bar", 5, 0.9);

    var result = DiffService.canonicallySorted(List.of(low, high));

    assertThat(result).containsExactly(high, low);
  }

  @Test
  @DisplayName("Null className treated as empty string (sorted before non-null)")
  void nullClassNameTreatedAsEmpty() {
    var nullClass  = usage("METHOD_INVOCATION_PARAMETER", null, "bar", 1, 1.0);
    var namedClass = usage("METHOD_INVOCATION_PARAMETER", "com.A", "bar", 1, 1.0);

    var result = DiffService.canonicallySorted(List.of(namedClass, nullClass));

    // empty string sorts before "com.A"
    assertThat(result).containsExactly(nullClass, namedClass);
  }
}
