package org.glodean.constants.extractor.bytecode;

import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;
import java.util.StringJoiner;

class Utils {
    private Utils() {
        // prevent instantiation
    }

    /**
     * Converts a {@link MethodTypeDesc} to a readable Java method signature.
     *
     * <p>Examples:
     * <ul>
     *   <li>{@code (Ljava/lang/String;I)V} → {@code (java.lang.String, int): void}</li>
     *   <li>{@code ()Ljava/lang/String;} → {@code (): java.lang.String}</li>
     * </ul>
     *
     * @param desc the method type descriptor
     * @return a human-readable Java method signature
     */
    static String toJavaDescriptor(MethodTypeDesc desc) {
        StringJoiner params = new StringJoiner(", ", "(", ")");
        desc.parameterList().forEach(p -> params.add(toJavaName(p)));
        return params + ": " + toJavaName(desc.returnType());
    }

    /**
     * Converts a {@link ClassDesc} to its dot-separated Java class name.
     *
     * <p>Examples:
     * <ul>
     *   <li>{@code Ljava/lang/String;} → {@code java.lang.String}</li>
     *   <li>{@code I} → {@code int}</li>
     *   <li>{@code [Ljava/lang/String;} → {@code java.lang.String[]}</li>
     *   <li>{@code V} → {@code void}</li>
     * </ul>
     *
     * @param desc the class descriptor
     * @return the Java class name
     */
    static String toJavaName(ClassDesc desc) {
        if (desc.isPrimitive()) return desc.displayName();
        if (desc.isArray()) return toJavaName(desc.componentType()) + "[]";
        String d = desc.descriptorString(); // Lcom/example/Foo;
        return d.substring(1, d.length() - 1).replace('/', '.');
    }

}
