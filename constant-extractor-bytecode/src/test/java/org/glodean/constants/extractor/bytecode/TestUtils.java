package org.glodean.constants.extractor.bytecode;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;

public class TestUtils {
  public static ClassModel convertClassToModel(Class<?> clazz) throws IOException {
    // Get the class file name
    String className = clazz.getName().replace('.', '/') + ".class";
    // Get the input stream for the class file
    InputStream inputStream = clazz.getClassLoader().getResourceAsStream(className);
    if (inputStream == null) {
      throw new IllegalArgumentException("Class file not found for " + clazz.getName());
    }

    ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
    byte[] buffer = new byte[4096];
    int bytesRead;
    // Read the class file content into the byte array output stream
    while ((bytesRead = inputStream.read(buffer)) != -1) {
      byteArrayOutputStream.write(buffer, 0, bytesRead);
    }
    inputStream.close();
    // Convert the output stream to a byte array
    return ClassFile.of().parse(byteArrayOutputStream.toByteArray());
  }
}
