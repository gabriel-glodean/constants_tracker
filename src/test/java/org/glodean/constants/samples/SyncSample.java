package org.glodean.constants.samples;

public class SyncSample {
  private static long counter = 0;

  public static long incAndGet() {
    synchronized (SyncSample.class) {
      return counter++;
    }
  }
}
