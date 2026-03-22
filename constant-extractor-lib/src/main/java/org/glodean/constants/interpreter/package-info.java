/**
 * Contract and context types for interpreting constant usages discovered during class analysis.
 *
 * <p>The central abstraction is {@link org.glodean.constants.interpreter.ConstantUsageInterpreter},
 * a strategy interface whose implementations convert a raw
 * {@link org.glodean.constants.model.ClassConstant.UsageLocation} into a structured
 * {@link org.glodean.constants.model.ClassConstant.ConstantUsage}.
 * Each implementation handles exactly one
 * {@link org.glodean.constants.model.ClassConstant.UsageType} and receives the matching
 * {@link org.glodean.constants.interpreter.ConstantUsageInterpreter.InterpretationContext}
 * subtype that carries the data required for that specific interpretation.
 *
 * <p>Available context types:
 * <ul>
 *   <li>{@link org.glodean.constants.interpreter.MethodCallContext} &ndash;
 *       constant passed as an argument to a method call</li>
 *   <li>{@link org.glodean.constants.interpreter.FieldStoreContext} &ndash;
 *       constant assigned to an instance field</li>
 *   <li>{@link org.glodean.constants.interpreter.ArithmeticOperandContext} &ndash;
 *       constant used as an operand in an arithmetic expression</li>
 *   <li>{@link org.glodean.constants.interpreter.StringConcatenationContext} &ndash;
 *       constant participating in a string concatenation</li>
 * </ul>
 *
 * <p>{@link org.glodean.constants.interpreter.ReceiverKind} qualifies whether the receiver
 * of a method call or the target of a field store is a static target, the current instance
 * ({@code this}), or an external object.
 */
package org.glodean.constants.interpreter;

