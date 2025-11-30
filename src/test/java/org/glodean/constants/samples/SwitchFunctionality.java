package org.glodean.constants.samples;

public class SwitchFunctionality {
  public static String getDayType(int day) {
    return switch (day) {
      case 1, 2, 3, 4, 5 -> "Weekday";
      case 6, 7 -> "Weekend";
      default -> "Invalid day";
    };
  }

  public static String labelForKey(int key) {
    switch (key) {
      case 10:
        return "ten";
      case 101:
        return "one-o-one";
      case 1000:
        return "thousand";
      case 12345:
        return "twelve-three-four-five";
      case 99999:
        return "ninety-nine-nine-nine-nine";
      case Integer.MAX_VALUE:
        return "max";
      default:
        return "other";
    }
  }
}
