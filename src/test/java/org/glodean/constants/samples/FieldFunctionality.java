package org.glodean.constants.samples;

public class FieldFunctionality {
    private static String staticField = "STATIC";
    private String field = "Instance";

    public static String getStaticField(){
        return staticField;
    }

    public String getField(){
        return field;
    }
}
