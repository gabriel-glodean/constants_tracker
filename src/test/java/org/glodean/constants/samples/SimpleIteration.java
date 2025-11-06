package org.glodean.constants.samples;

public class SimpleIteration {
    public int sum(int n){
        var sum = 0;
        for (var value = 0; value < n; value++){
            sum += value;
        }
        return sum;
    }
}
