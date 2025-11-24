package org.glodean.constants.samples;

import java.util.Random;

public class GotoSample {
  public void loopWithContinue() {
    for (int i = 0; i < new Random().nextInt(3, 10); i++) {
      if (i % 2 == 0) continue;
      System.out.println(i);
    }
  }
}
