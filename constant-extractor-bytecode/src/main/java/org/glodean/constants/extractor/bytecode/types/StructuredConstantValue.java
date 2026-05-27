package org.glodean.constants.extractor.bytecode.types;

import java.util.Map;

/**
 * Canonical, structured representation for non-literal bytecode constants.
 *
 * <p>These values are persisted as a stable string form and retain machine-readable
 * attributes that can be stored in metadata JSON.
 */
public sealed interface StructuredConstantValue permits ClassDescConstantValue, DynamicConstantValue, MethodHandleConstantValue {

  /** Stable type token used for filtering and metadata. */
  String constantValueType();

  /** Canonical string used as the persisted constant value. */
  String storageValue();

  /** Structured attributes describing the constant payload. */
  Map<String, Object> attributes();
}
