package org.glodean.constants.services;

import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.classfile.ClassFile;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.glodean.constants.extractor.ModelExtractor;
import org.glodean.constants.extractor.bytecode.AnalysisMerger;
import org.glodean.constants.extractor.bytecode.ClassModelExtractor;
import org.glodean.constants.extractor.bytecode.FileSystemModelExtractor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public record ConcreteExtractionService(@Autowired AnalysisMerger merger)
    implements ExtractionService {

  @Override
  public ModelExtractor extractorForClassFile(byte[] classFileBytes) {
    return new ClassModelExtractor(ClassFile.of().parse(classFileBytes), merger);
  }

  @Override
  public ModelExtractor extractorForJarFile(byte[] jarFileBytes) {
    return new FileSystemModelExtractor(fromZipBytesWithJimfs(jarFileBytes), merger);
  }

  private static FileSystem fromZipBytesWithJimfs(byte[] zipBytes) {
    FileSystem fs = Jimfs.newFileSystem(Configuration.unix());
    try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
      ZipEntry entry;
      while ((entry = zis.getNextEntry()) != null) {
        Path path = fs.getPath("/", entry.getName());
        if (entry.isDirectory()) {
          Files.createDirectories(path);
        } else {
          Path parent = path.getParent();
          if (parent != null) {
            Files.createDirectories(parent);
          }
          try (OutputStream out = Files.newOutputStream(path)) {
            zis.transferTo(out);
          }
        }
        zis.closeEntry();
      }
    } catch (IOException e) {
      throw new IllegalArgumentException(e);
    }
    return fs;
  }
}
