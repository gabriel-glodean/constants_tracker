package org.glodean.constants.interpreter;

/**
 * Qualifies the receiver of a method call or the target of a field store
 * discovered during constant analysis.
 *
 * <p>This distinction lets interpreters refine the semantic meaning of a constant.
 * For example, a value passed to a static utility differs in interpretation profile
 * from one passed to a method on the current instance.
 */
public enum ReceiverKind {
    /** The target is a static method or static field ({@code putstatic}); no receiver instance. */
    STATIC,
    /** The call or store targets the current instance ({@code this}). */
    THIS,
    /** The call or store targets an external object (parameter, local variable, or return value). */
    EXTERNAL_OBJECT
}