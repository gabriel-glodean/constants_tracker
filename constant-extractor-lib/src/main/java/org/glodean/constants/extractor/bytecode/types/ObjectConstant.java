package org.glodean.constants.extractor.bytecode.types;

/**
 * Wrapper for non-numeric object constants encountered during analysis.
 *
 * <p>Represents compile-time constant objects loaded via {@code ldc} (load constant)
 * bytecode instructions. Includes:
 * <ul>
 *   <li><b>String literals:</b> {@code "hello world"}</li>
 *   <li><b>Class references:</b> {@code String.class} → {@link java.lang.constant.ClassDesc}</li>
 *   <li><b>MethodType/MethodHandle:</b> Used in invokedynamic instructions</li>
 * </ul>
 *
 * <p>Object constants are distinct from {@link ObjectReference}s—constants have known
 * values at compile-time, while references represent runtime-allocated objects.
 */
public final class ObjectConstant extends Constant<Object> {
  public ObjectConstant(Object value) {
    super(value);
  }
}
