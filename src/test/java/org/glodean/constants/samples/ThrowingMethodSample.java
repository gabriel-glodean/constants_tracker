package org.glodean.constants.samples;

import java.nio.file.Files;
import java.nio.file.Path;

public class ThrowingMethodSample {
  public void open() {
    try {
      Files.readAllBytes(Path.of("C:\\non_existent_file.txt"));
    } catch (Exception e) {
      IO.println("Caught exception: " + e.getMessage());
    }
  }
}
