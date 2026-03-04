package org.glodean.constants.extractor.bytecode;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.lang.classfile.MethodModel;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

/**
 * Debug-focused test that runs the bytecode analysis on a single method. This is useful for
 * stepping through the analysis of a specific method in detail, which can be helpful for diagnosing
 * stack underflows or other analysis issues.
 *
 * <p>Set a breakpoint inside {@link ByteCodeMethodAnalyzer#run()} and run in debug mode to step
 * through the analysis instruction by instruction.
 */
class SpecificRTClassDebugTest {

  private static final String CLASS_PATH = "/modules/java.desktop/sun/java2d/pipe/DrawImage.class";
  private static final String METHOD_NAME = "renderImageXform";
  private static final String METHOD_DESCRIPTOR =
      "(Lsun/java2d/SunGraphics2D;Ljava/awt/Image;Ljava/awt/geom/AffineTransform;IIIIILjava/awt/Color;)V";

  @Test
  @Disabled
  void analyzeSpecificMethod() throws Exception {
    FileSystem jrtFs = FileSystems.getFileSystem(URI.create("jrt:/"));
    Path classPath = jrtFs.getPath(CLASS_PATH);
    assertTrue(Files.exists(classPath), "Class file must exist: " + CLASS_PATH);

    ClassModel classModel = ClassFile.of().parse(classPath);

    MethodModel target = null;
    for (MethodModel mm : classModel.methods()) {
      if (mm.methodName().stringValue().equals(METHOD_NAME)
          && mm.methodType().stringValue().equals(METHOD_DESCRIPTOR)) {
        target = mm;
        break;
      }
    }
    assertNotNull(target, "Method not found: " + METHOD_NAME + METHOD_DESCRIPTOR);

    System.out.println(
        "Analyzing: "
            + classModel.thisClass().asInternalName()
            + "::"
            + METHOD_NAME
            + METHOD_DESCRIPTOR);

    // --- Place a breakpoint on the next line to debug the analysis ---
    var analyzer = new ByteCodeMethodAnalyzer(classModel, target);
    analyzer.run();

    System.out.println("Analysis completed successfully.");
    System.out.println("Code elements: " + analyzer.code.size());
    System.out.println("Calls discovered: " + analyzer.calls);
  }
}
