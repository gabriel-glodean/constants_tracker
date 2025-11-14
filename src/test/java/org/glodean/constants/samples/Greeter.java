package org.glodean.constants.samples;

public record Greeter(String name) {
  public static final String FORMAT = "Hello guys from %s, I am %d characters long %n";
  public static String wackyFormat = "Roll me Scooby snacks %s! %n";

  public Greeter() {
    this("Default");
  }

  public String greet() {
    return FORMAT.formatted(this.name, this.name.length());
  }

  public String wackyGreet() {
    return wackyFormat.formatted(this.name);
  }
}
