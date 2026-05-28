package org.glodean.constants.extractor.bytecode;

import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Predicate;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.glodean.constants.extractor.ExtractionNotifier;
import org.glodean.constants.extractor.ModelExtractor;
import org.glodean.constants.extractor.ModelExtractorSupplierRepository;
import org.glodean.constants.model.UnitConstants;
import org.glodean.constants.model.UnitDescriptor;

/**
 * Extracts {@link UnitConstants} from bytecode sources — either a {@link FileSystem} (JAR or
 * directory tree) or a {@link ZipInputStream} (nested JAR already in memory).
 *
 * <p>All file-type dispatch is delegated to the injected {@link ModelExtractorSupplierRepository}.
 * This means the extractor is not limited to {@code .class} files: any format registered in the
 * repository (e.g. YAML, properties) found inside a JAR or filesystem will also be extracted.
 *
 * <p>By default, each {@link #extract} call creates and tears down its own thread pool. Pass a
 * shared {@link ExecutorService} via the overloaded factory methods to reuse one pool across
 * multiple extraction calls (recommended in server environments):
 *
 * <pre>{@code
 * // Standalone (creates its own pool per call):
 * var extractor = BytecodeModelExtractor.forFileSystem(jarFs, merger, notifier);
 *
 * // Shared pool (server use — executor lifecycle managed by caller):
 * ExecutorService pool = Executors.newFixedThreadPool(N);
 * var extractor = BytecodeModelExtractor.forFileSystem(pool, jarFs, merger, notifier, repository);
 * }</pre>
 */
public final class BytecodeModelExtractor implements ModelExtractor {

  /**
   * Strategy that feeds entries into an {@link ExtractionPool}.
   * Implementations are created by the factory methods and differ only in their input source.
   */
  @FunctionalInterface
  interface PoolFeeder {
    void feed(ExtractionPool pool) throws IOException;
  }

  private final ExecutorService executor; // null = create a fresh pool per extract() call
  private final ExtractionNotifier notifier;
  private final PoolFeeder feeder;

  private BytecodeModelExtractor(
      ExecutorService executor, ExtractionNotifier notifier, PoolFeeder feeder) {
    this.executor = executor;
    this.notifier = notifier;
    this.feeder = feeder;
  }

  // -------------------------------------------------------------------------
  // Factory methods — FileSystem (shared executor + explicit repository)
  // -------------------------------------------------------------------------

  /** Shared-executor variant. Walks {@code fs}, ignoring paths with the given prefix. */
  public static BytecodeModelExtractor forFileSystem(
      ExecutorService executor, FileSystem fs,
      String ignorePathPrefix, ExtractionNotifier notifier, ModelExtractorSupplierRepository repository) {
    return new BytecodeModelExtractor(executor, notifier,
        fileSystemFeeder(fs, ignorePathPrefix, repository));
  }

  /** Shared-executor variant. Walks {@code fs} with no path filtering. */
  public static BytecodeModelExtractor forFileSystem(
      ExecutorService executor, FileSystem fs,
      ExtractionNotifier notifier, ModelExtractorSupplierRepository repository) {
    return forFileSystem(executor, fs,null, notifier, repository);
  }

  // -------------------------------------------------------------------------
  // Factory methods — FileSystem (standalone — creates own executor per call)
  // These default to a class-file-only registry for backwards compatibility.
  // -------------------------------------------------------------------------

  /** Standalone variant. Walks {@code fs}, ignoring paths with the given prefix. */
  public static BytecodeModelExtractor forFileSystem(
      FileSystem fs, AnalysisMerger merger, String ignorePathPrefix, ExtractionNotifier notifier) {
    return new BytecodeModelExtractor(null, notifier,
        fileSystemFeeder(fs, ignorePathPrefix, defaultRepository(merger)));
  }

  /** Standalone variant. Walks {@code fs}, ignoring paths with the given prefix, silent notifier. */
  public static BytecodeModelExtractor forFileSystem(
      FileSystem fs, AnalysisMerger merger, String ignorePathPrefix) {
    return forFileSystem(fs, merger, ignorePathPrefix, new ExtractionNotifier.Silent());
  }

  /** Standalone variant. Walks {@code fs}, no filtering, silent notifier. */
  public static BytecodeModelExtractor forFileSystem(FileSystem fs, AnalysisMerger merger) {
    return forFileSystem(fs, merger, null, new ExtractionNotifier.Silent());
  }

  // -------------------------------------------------------------------------
  // Factory methods — ZipInputStream (shared executor + explicit repository)
  // -------------------------------------------------------------------------

  /** Shared-executor variant. Reads entries from {@code zis} sequentially. */
  public static BytecodeModelExtractor forZipStream(
      ExecutorService executor, ZipInputStream zis,
      ExtractionNotifier notifier, ModelExtractorSupplierRepository repository) {
    return new BytecodeModelExtractor(executor, notifier, zipStreamFeeder(zis, repository));
  }

  /** Shared-executor variant with a silent notifier. */
  public static BytecodeModelExtractor forZipStream(
      ExecutorService executor, ZipInputStream zis,
      ModelExtractorSupplierRepository repository) {
    return forZipStream(executor, zis, new ExtractionNotifier.Silent(), repository);
  }

  // -------------------------------------------------------------------------
  // Factory methods — ZipInputStream (standalone, class-file-only default)
  // -------------------------------------------------------------------------

  /** Standalone variant. */
  public static BytecodeModelExtractor forZipStream(
      ZipInputStream zis, AnalysisMerger merger, ExtractionNotifier notifier) {
    return new BytecodeModelExtractor(null, notifier,
        zipStreamFeeder(zis, defaultRepository(merger)));
  }

  /** Standalone variant with a silent notifier. */
  public static BytecodeModelExtractor forZipStream(ZipInputStream zis, AnalysisMerger merger) {
    return forZipStream(zis, merger, new ExtractionNotifier.Silent());
  }

  // -------------------------------------------------------------------------
  // Chunk-level extraction helper — for use by reactive streaming callers
  // -------------------------------------------------------------------------

  /**
   * Extracts constants from a pre-selected list of file-system {@code paths} in one
   * {@link ExtractionPool} batch. Intended to be called repeatedly by a streaming consumer
   * (e.g. via {@code Flux.fromIterable(allPaths).buffer(n).concatMap(chunk -> extractPathChunk(...))})
   * so that each chunk's bytes and futures are GC-eligible before the next chunk starts.
   *
   * @param executor   shared thread pool for parallel analysis of this chunk
   * @param paths      the file-system paths to read and analyze
   * @param notifier   progress/error listener
   * @param repository maps file names to extractors
   * @return all {@link UnitConstants} produced by the entries in {@code paths}
   */
  public static Collection<UnitConstants> extractPathChunk(
      ExecutorService executor,
      List<Path> paths,
      ExtractionNotifier notifier,
      ModelExtractorSupplierRepository repository) throws ExtractionException {
    try {
      var pool = new ExtractionPool(executor, notifier);
      for (Path path : paths) {
        String entryName = path.getFileName().toString();
        byte[] bytes = Files.readAllBytes(path);
        repository.resolve(entryName, bytes).ifPresent(supply -> {
          var desc = new UnitDescriptor(supply.sourceKind(), path.toString(), bytes.length);
          pool.submit(supply::extractor, path, desc);
        });
      }
      return pool.collect();
    } catch (IOException e) {
      throw new ExtractionException(e);
    }
  }

  // -------------------------------------------------------------------------
  // ModelExtractor
  // -------------------------------------------------------------------------

  @Override
  public Collection<UnitConstants> extract(UnitDescriptor source) throws ExtractionException {
    boolean ownsExecutor = (executor == null);
    ExecutorService exec = ownsExecutor
        ? Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors())
        : executor;
    try {
      var pool = new ExtractionPool(exec, notifier);
      feeder.feed(pool);
      return pool.collect();
    } catch (IOException e) {
      throw new ExtractionException(e);
    } finally {
      if (ownsExecutor) exec.close();
    }
  }

  // -------------------------------------------------------------------------
  // Internal helpers
  // -------------------------------------------------------------------------

  /**
   * Builds a minimal repository that handles only {@code .class} files.
   * Used by standalone factory methods so they remain backwards-compatible.
   */
  private static ModelExtractorSupplierRepository defaultRepository(AnalysisMerger merger) {
    return ModelExtractorSupplierRepository.builder()
        .register(name -> name.endsWith(".class"), BytecodeSourceKind.CLASS_FILE,
            ClassModelExtractor.supplier(merger))
        .build();
  }

  /**
   * Walks all regular files in {@code fs}, resolves each by filename via {@code repository},
   * and submits matched entries to the pool with a per-entry {@link UnitDescriptor}.
   */
  private static PoolFeeder fileSystemFeeder(
      FileSystem fs, String ignorePathPrefix, ModelExtractorSupplierRepository repository) {
    Predicate<Path> filter = Files::isRegularFile;
    if (ignorePathPrefix != null) {
      filter = filter.and(p -> !p.toString().startsWith(ignorePathPrefix));
    }
    Predicate<Path> finalFilter = filter;
    return (pool) -> {
      try (var walk = Files.walk(fs.getPath("/"))) {
        for (Path path : walk.filter(finalFilter).toList()) {
          String entryName = path.getFileName().toString();
          byte[] bytes = Files.readAllBytes(path);
          repository.resolve(entryName, bytes).ifPresent(supply -> {
            var descriptor = new UnitDescriptor(supply.sourceKind(), path.toString(), bytes.length);
            pool.submit(supply::extractor, path, descriptor);
          });
        }
      }
    };
  }

  /**
   * Reads all entries from {@code zis} sequentially, resolves each by filename via
   * {@code repository}, and submits matched entries to the pool with a per-entry descriptor.
   */
  private static PoolFeeder zipStreamFeeder(
      ZipInputStream zis, ModelExtractorSupplierRepository repository) {
    return (pool) -> {
      for (ZipEntry ze = zis.getNextEntry(); ze != null; ze = zis.getNextEntry()) {
        if (!ze.isDirectory()) {
          String entryName = Path.of(ze.getName()).getFileName().toString();
          String entryPath = ze.getName(); // captured as effectively-final for the lambda
          byte[] bytes = zis.readAllBytes();
          repository.resolve(entryName, bytes).ifPresent(supply -> {
            var descriptor = new UnitDescriptor(supply.sourceKind(), entryPath, bytes.length);
            pool.submit(supply::extractor, Path.of(entryPath), descriptor);
          });
        }
        zis.closeEntry();
      }
    };
  }
}
