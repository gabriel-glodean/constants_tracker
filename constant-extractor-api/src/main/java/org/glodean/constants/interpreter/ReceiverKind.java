package org.glodean.constants.interpreter;

/**
 * Qualifies the receiver of a method call or the target of a field store
 * discovered during constant analysis.
 */
public enum ReceiverKind {
    STATIC,
    THIS,
    EXTERNAL_OBJECT
}
