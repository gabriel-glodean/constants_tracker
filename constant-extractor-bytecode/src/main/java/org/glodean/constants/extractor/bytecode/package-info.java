/**
 * Bytecode analysis and constant extraction using Java 25's Class-File API.
 *
 * <p>This package provides the core bytecode analysis engine that discovers constants
 * and their usage patterns. The analysis pipeline consists of:
 *
 * <ol>
 *   <li><b>Control-flow graph construction:</b> {@link org.glodean.constants.extractor.bytecode.SuccessorBuilder}
 *       builds a conservative CFG with exception handlers</li>
 *   <li><b>Dataflow analysis:</b> {@link org.glodean.constants.extractor.bytecode.ByteCodeMethodAnalyzer}
 *       computes abstract states (IN/OUT) using a worklist algorithm</li>
 *   <li><b>Usage extraction:</b> {@link org.glodean.constants.extractor.bytecode.AnalysisMerger}
 *       converts abstract states into constant-to-usage mappings</li>
 * </ol>
 *
 * <p><b>Key components:</b>
 * <ul>
 *   <li>{@link org.glodean.constants.extractor.bytecode.ClassModelExtractor}: Per-class analysis</li>
 *   <li>{@link org.glodean.constants.extractor.bytecode.FileSystemModelExtractor}: Bulk JAR/directory analysis</li>
 *   <li>{@link org.glodean.constants.extractor.bytecode.handlers}: Instruction handler registry</li>
 *   <li>{@link org.glodean.constants.extractor.bytecode.types}: Abstract value type system</li>
 * </ul>
 *
 * <p><b>Analysis characteristics:</b>
 * <ul>
 *   <li>Intra-procedural (per-method) with conservative inter-procedural field handling</li>
 *   <li>Flow-sensitive constant propagation</li>
 *   <li>Context-sensitive allocation-site abstraction for objects</li>
 *   <li>Widening at 32 values per abstract location for termination guarantee</li>
 * </ul>
 *
 * <p><b>Example: Analyzing a single class</b>
 * <pre>{@code
 * byte[] classBytes = Files.readAllBytes(Path.of("MyClass.class"));
 * ClassModel model = ClassFile.of().parse(classBytes);
 * AnalysisMerger merger = new AnalysisMerger(new InternalStringConcatPatternSplitter());
 * ClassModelExtractor extractor = new ClassModelExtractor(model, merger);
 * Collection<UnitConstants> results = extractor.extract();
 * }</pre>
 *
 * <p><b>Example: Analyzing a JAR file</b>
 * <pre>{@code
 * FileSystem jarFS = FileSystems.newFileSystem(jarPath, (ClassLoader)null);
 * AnalysisMerger merger = new AnalysisMerger(new InternalStringConcatPatternSplitter());
 * FileSystemModelExtractor extractor = new FileSystemModelExtractor(
 *     jarFS, merger, "META-INF/", notifier);
 * Collection<UnitConstants> results = extractor.extract();
 * }</pre>
 *
 * @see org.glodean.constants.extractor.ModelExtractor
 * @see org.glodean.constants.model
 */
package org.glodean.constants.extractor.bytecode;

