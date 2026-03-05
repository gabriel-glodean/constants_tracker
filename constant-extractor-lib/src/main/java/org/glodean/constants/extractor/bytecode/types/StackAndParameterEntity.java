package org.glodean.constants.extractor.bytecode.types;

import static java.lang.constant.ConstantDescs.CD_void;

import java.lang.constant.ClassDesc;

/**
 * Abstract representation for objects/values that appear on the operand stack or in parameters.
 *
 * <p>This sealed interface is the root of the abstract value hierarchy used in bytecode analysis.
 * It represents all possible values that can exist on the JVM operand stack or in local variables:
 * <ul>
 *   <li><b>{@link Constant}:</b> Compile-time known values (numeric/object constants)</li>
 *   <li><b>{@link ObjectReference}:</b> Runtime-allocated objects (allocation-site abstraction)</li>
 *   <li><b>{@link PrimitiveValue}:</b> Primitive values with unknown compile-time value</li>
 *   <li><b>{@link NullReference}:</b> The null value</li>
 *   <li><b>{@link ConstantPropagation}:</b> Multiple possible constant values (phi merge)</li>
 * </ul>
 *
 * <p>The sealed interface ensures exhaustive pattern matching and type safety throughout the
 * analysis. Each implementation provides stack size information (single vs double-cell) used
 * to model JVM stack behavior accurately.
 *
 * <p><b>Example value representations:</b>
 * <pre>{@code
 * "hello"              → ObjectConstant("hello")
 * 42                   → NumericConstant(42)
 * new StringBuilder()  → ObjectReference(StringBuilder, "MyClass::method@15")
 * null                 → NullReference.INSTANCE
 * phi(5, 10)          → ConstantPropagation({5, 10})
 * }</pre>
 */
public sealed interface StackAndParameterEntity
    permits Constant,
        ConstantPropagatingEntity,
        ConstantPropagation,
        ConvertibleEntity,
        NullReference,
        ObjectReference {
  default SizeType size() {
    return SizeType.SINGLE_CELL;
  }

  static StackAndParameterEntity convert(ClassDesc type, String tag) {
    if (type.isPrimitive()) {
      return new PrimitiveValue(type, tag);
    }
    if (type != CD_void) {
      return new ObjectReference(type, tag);
    }
    throw new IllegalArgumentException("Cannot resolve void types");
  }
}
