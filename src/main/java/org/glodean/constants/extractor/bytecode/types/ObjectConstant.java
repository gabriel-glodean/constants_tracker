package org.glodean.constants.extractor.bytecode.types;

/** Wrapper for non-numeric object constants encountered during analysis. */
public final class ObjectConstant extends Constant<Object> {
  public ObjectConstant(Object value) {
    super(value);
  }
}
