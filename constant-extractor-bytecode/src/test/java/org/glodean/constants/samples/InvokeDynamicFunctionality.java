package org.glodean.constants.samples;

import java.util.Set;

public class InvokeDynamicFunctionality {
  public final String concat(Set<String> inputs) {
    return inputs.stream().reduce("", (a, b) -> a + b);
  }
}
