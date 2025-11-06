package org.glodean.constants.samples;

import java.util.Random;

public class SimpleIfElse {
    public int number(){
        int number = new Random().nextInt(100);
        if (number%2 == 0) number = number<<2;
        return number;
    }

    public int numberIfElse(){
        int number = new Random().nextInt(100);
        if (number%2 == 0) number = number<<2;
        else number += 7;
        return number;
    }
}
