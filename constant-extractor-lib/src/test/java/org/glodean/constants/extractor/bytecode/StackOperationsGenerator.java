package org.glodean.constants.extractor.bytecode;

import static java.lang.classfile.ClassFile.ACC_PUBLIC;
import static java.lang.classfile.ClassFile.ACC_STATIC;

import java.lang.classfile.ClassFile;
import java.lang.classfile.attribute.SourceFileAttribute;
import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;

public class StackOperationsGenerator {
  static final String CLASS_NAME = "org/glodeam/constants/samples/StackOperationsExample";

  static byte[] generateClass() {
    // Create the class file in memory
    return ClassFile.of()
        .build(
            ClassDesc.ofInternalName(CLASS_NAME),
            cb -> {
              cb.withFlags(ACC_PUBLIC)
                  .withVersion(69, 0)
                  .with(SourceFileAttribute.of(CLASS_NAME + ".java"));

              // Single-cell handlers
              cb.withMethod(
                  "genDup",
                  MethodTypeDesc.ofDescriptor("()V"),
                  ACC_PUBLIC | ACC_STATIC,
                  mb ->
                      mb.withCode(
                          codeBuilder -> {
                            codeBuilder.aconst_null();
                            codeBuilder.dup();
                            codeBuilder.pop();
                            codeBuilder.return_();
                          }));

              cb.withMethod(
                  "genPop",
                  MethodTypeDesc.ofDescriptor("()V"),
                  ACC_PUBLIC | ACC_STATIC,
                  mb ->
                      mb.withCode(
                          codeBuilder -> {
                            codeBuilder.aconst_null();
                            codeBuilder.pop();
                            codeBuilder.return_();
                          }));

              cb.withMethod(
                  "genPop2_single_cells",
                  MethodTypeDesc.ofDescriptor("()V"),
                  ACC_PUBLIC | ACC_STATIC,
                  mb ->
                      mb.withCode(
                          codeBuilder -> {
                            codeBuilder.aconst_null();
                            codeBuilder.aconst_null();
                            codeBuilder.pop2();
                            codeBuilder.return_();
                          }));

              cb.withMethod(
                  "genSwap",
                  MethodTypeDesc.ofDescriptor("()V"),
                  ACC_PUBLIC | ACC_STATIC,
                  mb ->
                      mb.withCode(
                          codeBuilder -> {
                            codeBuilder.aconst_null();
                            codeBuilder.aconst_null();
                            codeBuilder.swap();
                            codeBuilder.pop();
                            codeBuilder.pop();
                            codeBuilder.return_();
                          }));

              cb.withMethod(
                  "genDupX1",
                  MethodTypeDesc.ofDescriptor("()V"),
                  ACC_PUBLIC | ACC_STATIC,
                  mb ->
                      mb.withCode(
                          codeBuilder -> {
                            codeBuilder.aconst_null();
                            codeBuilder.aconst_null();
                            codeBuilder.dup_x1();
                            codeBuilder.return_();
                          }));

              cb.withMethod(
                  "genDupX2",
                  MethodTypeDesc.ofDescriptor("()V"),
                  ACC_PUBLIC | ACC_STATIC,
                  mb ->
                      mb.withCode(
                          codeBuilder -> {
                            codeBuilder.aconst_null();
                            codeBuilder.aconst_null();
                            codeBuilder.aconst_null();
                            codeBuilder.dup_x2();
                            codeBuilder.return_();
                          }));

              cb.withMethod(
                  "genDup2_from_two_singles",
                  MethodTypeDesc.ofDescriptor("()V"),
                  ACC_PUBLIC | ACC_STATIC,
                  mb ->
                      mb.withCode(
                          codeBuilder -> {
                            // duplicate two single-cell values
                            codeBuilder.aconst_null();
                            codeBuilder.aconst_null();
                            codeBuilder.dup2();
                            codeBuilder.return_();
                          }));

              cb.withMethod(
                  "genDup2_X1",
                  MethodTypeDesc.ofDescriptor("()V"),
                  ACC_PUBLIC | ACC_STATIC,
                  mb ->
                      mb.withCode(
                          codeBuilder -> {
                            codeBuilder.aconst_null();
                            codeBuilder.aconst_null();
                            codeBuilder.aconst_null();
                            codeBuilder.dup2_x1();
                            codeBuilder.return_();
                          }));

              cb.withMethod(
                  "genDup2_X2",
                  MethodTypeDesc.ofDescriptor("()V"),
                  ACC_PUBLIC | ACC_STATIC,
                  mb ->
                      mb.withCode(
                          codeBuilder -> {
                            codeBuilder.aconst_null();
                            codeBuilder.aconst_null();
                            codeBuilder.aconst_null();
                            codeBuilder.aconst_null();
                            codeBuilder.dup2_x2();
                            codeBuilder.return_();
                          }));

              // Category-2 (double/long) handlers
              cb.withMethod(
                  "genCat2Pop",
                  MethodTypeDesc.ofDescriptor("()V"),
                  ACC_PUBLIC | ACC_STATIC,
                  mb ->
                      mb.withCode(
                          codeBuilder -> {
                            codeBuilder.loadConstant(Math.PI); // pushes a category-2 value
                            codeBuilder.pop();
                            codeBuilder.return_();
                          }));

              cb.withMethod(
                  "genCat2Pop2",
                  MethodTypeDesc.ofDescriptor("()V"),
                  ACC_PUBLIC | ACC_STATIC,
                  mb ->
                      mb.withCode(
                          codeBuilder -> {
                            codeBuilder.loadConstant(Math.PI);
                            codeBuilder.pop2();
                            codeBuilder.return_();
                          }));

              cb.withMethod(
                  "genCat2Dup2",
                  MethodTypeDesc.ofDescriptor("()V"),
                  ACC_PUBLIC | ACC_STATIC,
                  mb ->
                      mb.withCode(
                          codeBuilder -> {
                            codeBuilder.loadConstant(Math.PI);
                            codeBuilder.dup2();
                            codeBuilder.return_();
                          }));

              cb.withMethod(
                  "genCat2Dup2_X1",
                  MethodTypeDesc.ofDescriptor("()V"),
                  ACC_PUBLIC | ACC_STATIC,
                  mb ->
                      mb.withCode(
                          codeBuilder -> {
                            // top is category-2, next is single
                            codeBuilder.aconst_null();
                            codeBuilder.loadConstant(Math.PI);
                            codeBuilder.dup2_x1();
                            codeBuilder.return_();
                          }));

              cb.withMethod(
                  "genCat2Dup2_X2",
                  MethodTypeDesc.ofDescriptor("()V"),
                  ACC_PUBLIC | ACC_STATIC,
                  mb ->
                      mb.withCode(
                          codeBuilder -> {
                            // top and next are category-2
                            codeBuilder.loadConstant(Math.PI);
                            codeBuilder.loadConstant(Math.E);
                            codeBuilder.dup2_x2();
                            codeBuilder.return_();
                          }));
            });
  }

  public static byte[] generateClassInvalidDupOnCat2() {
    // Create the class file in memory
    return ClassFile.of()
        .build(
            ClassDesc.ofInternalName(CLASS_NAME),
            cb -> {
              cb.withFlags(ACC_PUBLIC)
                  .withVersion(69, 0)
                  .with(SourceFileAttribute.of(CLASS_NAME + ".java"));

              cb.withMethod(
                  "invalid_dup_on_cat2",
                  MethodTypeDesc.ofDescriptor("()V"),
                  ACC_PUBLIC | ACC_STATIC,
                  mb ->
                      mb.withCode(
                          codeBuilder -> {
                            codeBuilder.loadConstant(Math.PI);
                            codeBuilder.dup(); // invalid when top is category-2 -> should exercise
                            // exception branch
                            codeBuilder.return_();
                          }));
            });
  }

  public static byte[] generateClassInvalidSwapOnCat2() {
    // Create the class file in memory
    return ClassFile.of()
        .build(
            ClassDesc.ofInternalName(CLASS_NAME),
            cb -> {
              cb.withFlags(ACC_PUBLIC)
                  .withVersion(69, 0)
                  .with(SourceFileAttribute.of(CLASS_NAME + ".java"));

              cb.withMethod(
                  "invalid_swap_on_cat2",
                  MethodTypeDesc.ofDescriptor("()V"),
                  ACC_PUBLIC | ACC_STATIC,
                  mb ->
                      mb.withCode(
                          codeBuilder -> {
                            codeBuilder.aconst_null();
                            codeBuilder.loadConstant(Math.PI);
                            codeBuilder.swap(); // invalid when top is category-2
                            codeBuilder.return_();
                          }));
            });
  }

  public static byte[] generateClassInvalidDupX1OnCat2() {
    // Create the class file in memory
    return ClassFile.of()
        .build(
            ClassDesc.ofInternalName(CLASS_NAME),
            cb -> {
              cb.withFlags(ACC_PUBLIC)
                  .withVersion(69, 0)
                  .with(SourceFileAttribute.of(CLASS_NAME + ".java"));

              cb.withMethod(
                  "invalid_dup_x1_on_cat2",
                  MethodTypeDesc.ofDescriptor("()V"),
                  ACC_PUBLIC | ACC_STATIC,
                  mb ->
                      mb.withCode(
                          codeBuilder -> {
                            codeBuilder.aconst_null();
                            codeBuilder.loadConstant(Math.PI);
                            codeBuilder.dup_x1(); // invalid when top is category-2
                            codeBuilder.return_();
                          }));
            });
  }

  public static byte[] generateClassInvalidDup21OnCat2() {
    // Create the class file in memory
    return ClassFile.of()
        .build(
            ClassDesc.ofInternalName(CLASS_NAME),
            cb -> {
              cb.withFlags(ACC_PUBLIC)
                  .withVersion(69, 0)
                  .with(SourceFileAttribute.of(CLASS_NAME + ".java"));

              cb.withMethod(
                  "invalid_dup_x2_on_cat2",
                  MethodTypeDesc.ofDescriptor("()V"),
                  ACC_PUBLIC | ACC_STATIC,
                  mb ->
                      mb.withCode(
                          codeBuilder -> {
                            codeBuilder.aconst_null();
                            codeBuilder.aconst_null();
                            codeBuilder.loadConstant(Math.PI);
                            codeBuilder.dup_x2(); // invalid when top is category-2
                            codeBuilder.return_();
                          }));
            });
  }

  public static byte[] generateClassInvalidStack() {
    // Create the class file in memory
    return ClassFile.of()
        .build(
            ClassDesc.ofInternalName(CLASS_NAME),
            cb -> {
              cb.withFlags(ACC_PUBLIC)
                  .withVersion(69, 0)
                  .with(SourceFileAttribute.of(CLASS_NAME + ".java"));

              cb.withMethod(
                  "invalid_pop",
                  MethodTypeDesc.ofDescriptor("()V"),
                  ACC_PUBLIC | ACC_STATIC,
                  mb ->
                      mb.withCode(
                          codeBuilder -> {
                            codeBuilder.pop(); // invalid pop on empty stack
                            codeBuilder.return_();
                          }));
            });
  }
}
