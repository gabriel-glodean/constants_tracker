package org.glodean.constants.extractor.bytecode.types;

/** Singleton sentinel representing the Java null reference on the operand stack. */
public enum NullReference implements StackAndParameterEntity {
  INSTANCE;

  @Override
  public String toString() {
    return "null";
  }
}
