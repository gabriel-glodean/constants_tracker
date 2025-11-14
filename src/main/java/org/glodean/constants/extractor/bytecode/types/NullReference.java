package org.glodean.constants.extractor.bytecode.types;

public enum NullReference implements StackAndParameterEntity {
  INSTANCE;

  @Override
  public String toString() {
    return "null";
  }
}
