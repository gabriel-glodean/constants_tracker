package org.glodean.constants.samples;

public class ArrayFunctionality {
  void incrementAndPrintLength() {
    var array = new int[] {1, 2, 3, 4};
    IO.println(array.length);
  }

  public static int readIndex(int[] array, int index) {
    return array[index];
  }

  public static void writeToIndex(int[] array, int index, int value) {
    array[index] = value;
  }
}
