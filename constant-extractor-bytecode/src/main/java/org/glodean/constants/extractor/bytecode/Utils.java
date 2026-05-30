package org.glodean.constants.extractor.bytecode;

import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;
import java.util.stream.Collectors;

public class Utils {
    private Utils() {
        // prevent instantiation
    }

    /**
     * Converts a {@link MethodTypeDesc} to compact Java dot-notation.
     *
     * <p>Examples:
     * <ul>
     *   <li>{@code (Ljava/lang/String;I)V} → {@code (java.lang.String,int)void}</li>
     *   <li>{@code ()Ljava/lang/String;}   → {@code ()java.lang.String}</li>
     * </ul>
     *
     * @param desc the method type descriptor
     * @return compact Java dot-notation method descriptor
     */
    public static String toJavaDescriptor(MethodTypeDesc desc) {
        String params = desc.parameterList().stream()
                .map(Utils::toJavaName)
                .collect(Collectors.joining(","));
        return "(" + params + ")" + toJavaName(desc.returnType());
    }

    /**
     * Converts a raw JVM descriptor string (method or type) to compact Java dot-notation.
     *
     * <p>Used for constant value storage. Method descriptors start with {@code (};
     * type descriptors do not. Inner-class separator {@code $} is treated as {@code .}.
     * Examples:
     * <ul>
     *   <li>{@code (Ljava/lang/String;I)V}          → {@code (java.lang.String,int)void}</li>
     *   <li>{@code Ljava/lang/invoke/MethodHandles$Lookup;} → {@code java.lang.invoke.MethodHandles.Lookup}</li>
     *   <li>{@code I}                               → {@code int}</li>
     * </ul>
     *
     * @param descriptor the raw JVM descriptor string
     * @return compact Java dot-notation representation
     */
    public static String toJavaDescriptor(String descriptor) {
        if (descriptor.startsWith("(")) {
            return toJavaDescriptor(MethodTypeDesc.ofDescriptor(descriptor));
        } else {
            return toJavaName(ClassDesc.ofDescriptor(descriptor));
        }
    }

    /**
     * Converts a {@link ClassDesc} to its dot-separated Java class name.
     * Inner-class separator {@code $} is treated as {@code .}.
     *
     * <p>Examples:
     * <ul>
     *   <li>{@code Ljava/lang/String;} → {@code java.lang.String}</li>
     *   <li>{@code I} → {@code int}</li>
     *   <li>{@code [Ljava/lang/String;} → {@code java.lang.String[]}</li>
     *   <li>{@code V} → {@code void}</li>
     *   <li>{@code Ljava/lang/invoke/MethodHandles$Lookup;} → {@code java.lang.invoke.MethodHandles.Lookup}</li>
     * </ul>
     *
     * @param desc the class descriptor
     * @return the Java class name
     */
    public static String toJavaName(ClassDesc desc) {
        if (desc.isPrimitive()) return desc.displayName();
        if (desc.isArray()) return toJavaName(desc.componentType()) + "[]";
        String d = desc.descriptorString(); // Lcom/example/Foo$Inner;
        return d.substring(1, d.length() - 1).replace('/', '.').replace('$', '.');
    }
}
