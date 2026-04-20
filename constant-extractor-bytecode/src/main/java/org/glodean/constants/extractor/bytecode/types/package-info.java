/**
 * Abstract value type system for bytecode dataflow analysis.
 *
 * <p>This package defines the abstract domain used in the points-to and constant propagation
 * analysis. The type hierarchy represents all possible JVM stack/local values:
 *
 * <pre>
 * {@link org.glodean.constants.extractor.bytecode.types.StackAndParameterEntity} (sealed root)
 *   ├── {@link org.glodean.constants.extractor.bytecode.types.Constant}
 *   │     ├── {@link org.glodean.constants.extractor.bytecode.types.NumericConstant} (42, 3.14, etc.)
 *   │     └── {@link org.glodean.constants.extractor.bytecode.types.ObjectConstant} ("hello", String.class)
 *   ├── {@link org.glodean.constants.extractor.bytecode.types.ObjectReference} (allocation-site abstraction)
 *   ├── {@link org.glodean.constants.extractor.bytecode.types.PrimitiveValue} (unknown primitive)
 *   ├── {@link org.glodean.constants.extractor.bytecode.types.NullReference} (null)
 *   └── {@link org.glodean.constants.extractor.bytecode.types.ConstantPropagation} (phi-merged constants)
 * </pre>
 *
 * <p><b>Key abstractions:</b>
 * <ul>
 *   <li><b>{@link org.glodean.constants.extractor.bytecode.types.State}:</b> Per-instruction abstract state
 *       (locals, stack, heap, statics)</li>
 *   <li><b>{@link org.glodean.constants.extractor.bytecode.types.PointsToSet}:</b> Set of possible values
 *       with widening at 32 elements</li>
 *   <li><b>{@link org.glodean.constants.extractor.bytecode.types.FieldKey}/{@link org.glodean.constants.extractor.bytecode.types.StaticFieldKey}:</b>
 *       Keys for abstract heap/static storage</li>
 * </ul>
 *
 * <p><b>Design rationale:</b>
 * <ul>
 *   <li>Sealed interfaces ensure exhaustive pattern matching</li>
 *   <li>Immutable records for efficient copying during dataflow</li>
 *   <li>Widening guarantees termination (fixed-point iteration always converges)</li>
 *   <li>Size tracking (single vs double-cell) models JVM stack accurately</li>
 * </ul>
 *
 * <p><b>Example: Abstract state at a merge point</b>
 * <pre>{@code
 * // Before branch: local 0 = NumericConstant(42)
 * // After branch:  local 0 = ConstantPropagation({42, 100})
 * State merged = in1.copy();
 * boolean changed = merged.unionWith(in2);  // Merges both branches
 * }</pre>
 *
 * @see org.glodean.constants.extractor.bytecode.ByteCodeMethodAnalyzer
 * @see org.glodean.constants.extractor.bytecode.handlers.InstructionHandler
 */
package org.glodean.constants.extractor.bytecode.types;

