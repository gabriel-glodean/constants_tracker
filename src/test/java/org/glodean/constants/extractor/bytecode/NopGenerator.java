package org.glodean.constants.extractor.bytecode;

import static java.lang.classfile.ClassFile.ACC_PUBLIC;
import static java.lang.classfile.ClassFile.ACC_STATIC;

import java.lang.classfile.ClassFile;
import java.lang.classfile.attribute.SourceFileAttribute;
import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;

public class NopGenerator {
  static final String CLASS_NAME = "org/glodeam/constants/samples/NopExample";

  public static byte[] generateNop() {
    // Create the class file in memory
    return ClassFile.of()
        .build(
            ClassDesc.ofInternalName(CLASS_NAME),
            cb -> {
              cb.withFlags(ACC_PUBLIC)
                  .withVersion(69, 0)
                  .with(SourceFileAttribute.of(CLASS_NAME + ".java"));

              cb.withMethod(
                  "nopMethod",
                  MethodTypeDesc.ofDescriptor("()V"),
                  ACC_PUBLIC | ACC_STATIC,
                  mb ->
                      mb.withCode(
                          codeBuilder -> {
                            codeBuilder.nop();
                            codeBuilder.return_();
                          }));
            });
  }
}
