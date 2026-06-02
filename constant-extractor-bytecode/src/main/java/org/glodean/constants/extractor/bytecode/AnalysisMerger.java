package org.glodean.constants.extractor.bytecode;

import static java.lang.classfile.Opcode.*;
import static org.glodean.constants.extractor.bytecode.Utils.toJavaDescriptor;
import static org.glodean.constants.extractor.bytecode.Utils.toJavaName;
import static org.glodean.constants.model.UnitConstant.UsageType.*;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Multimap;

import java.lang.classfile.CodeElement;
import java.lang.classfile.Opcode;
import java.lang.classfile.instruction.*;
import java.lang.classfile.instruction.LineNumber;
import java.lang.constant.ClassDesc;
import java.util.*;
import java.util.function.Function;

import org.glodean.constants.interpreter.ArithmeticOperandContext;
import org.glodean.constants.interpreter.ConstantUsageInterpreter;
import org.glodean.constants.interpreter.FieldStoreContext;
import org.glodean.constants.interpreter.MethodCallContext;
import org.glodean.constants.interpreter.ReceiverKind;
import org.glodean.constants.interpreter.StringConcatenationContext;
import org.glodean.constants.extractor.bytecode.types.Constant;
import org.glodean.constants.extractor.bytecode.types.ConstantPropagation;
import org.glodean.constants.extractor.bytecode.types.ObjectReference;
import org.glodean.constants.extractor.bytecode.types.PointsToSet;
import org.glodean.constants.extractor.bytecode.types.State;
import org.glodean.constants.model.UnitConstant;
import org.glodean.constants.model.UnitConstant.ConstantUsage;
import org.glodean.constants.model.UnitConstant.UsageLocation;

/**
 * Merges per-instruction abstract states produced by the analyzer into a mapping of discovered
 * constant values to their {@link ConstantUsage} descriptions.
 *
 * <p>The class accepts a pluggable {@code patternSplitter} function used to extract literal parts
 * from invokedynamic string-concat patterns (the format depends on the JVM implementation).
 *
 * <p>For each instruction, the merger:
 * <ol>
 *   <li>Builds the appropriate {@link ConstantUsageInterpreter.InterpretationContext} subtype
 *       from the instruction's operands and the abstract state.</li>
 *   <li>Determines the {@link ReceiverKind} by inspecting the receiver slot in the state.</li>
 *   <li>Retrieves every registered interpreter for the matched
 *       {@link org.glodean.constants.model.UnitConstant.UsageType} from the {@link ConstantUsageInterpreterRegistry}.</li>
 *   <li>Calls each interpreter and records the resulting {@link ConstantUsage}.</li>
 * </ol>
 *
 * @param patternSplitter          function to extract literal parts from invokedynamic
 *                                 string-concat patterns
 * @param usageInterpreterRegistry registry of interpreters for constant usage classification
 */
public record AnalysisMerger(
        Function<String, Set<String>> patternSplitter,
        ConstantUsageInterpreterRegistry usageInterpreterRegistry) {

    // Named distances from the stack top — avoids magic numbers in the switch arms.
    private static final int TOP = 1;  // most recently pushed value
    private static final int SECOND = 2;  // one below top
    private static final int THIRD = 3;  // two below top

    private static final String STRING_CONCAT_FACTORY = "java/lang/invoke/StringConcatFactory";

    private static final EnumSet<Opcode> UNARY_ARITHMETIC_OPCODES = EnumSet.of(
            INEG, LNEG, FNEG, DNEG,
            ISHL, LSHL, ISHR, LSHR,
            IUSHR, LUSHR,
            IAND, LAND,
            IOR, LOR,
            IXOR, LXOR);

    // -------------------------------------------------------------------------
    // Constructors
    // -------------------------------------------------------------------------

    /**
     * Creates an {@code AnalysisMerger} backed by a default (all-no-op) registry.
     *
     * <p>Useful in contexts where no semantic interpreters have been registered yet.
     * Every discovered constant will be recorded with
     * {@link org.glodean.constants.model.UnitConstant.CoreSemanticType#UNKNOWN} and zero confidence.
     *
     * @param patternSplitter function that splits a string-concat pattern into a set of
     *                        literal substrings
     */
    public AnalysisMerger(Function<String, Set<String>> patternSplitter) {
        this(patternSplitter, ConstantUsageInterpreterRegistry.builder().build());
    }

    public AnalysisMerger {
        Objects.requireNonNull(patternSplitter, "patternSplitter cannot be null");
        Objects.requireNonNull(usageInterpreterRegistry, "usageInterpreterRegistry cannot be null");
    }

    // -------------------------------------------------------------------------
    // Public entry point
    // -------------------------------------------------------------------------

    /**
     * Merges bytecode-level state information into a multimap of constant value →
     * {@link ConstantUsage}.
     *
     * <p>Instructions whose IN state is {@code null} (unreachable code) are silently skipped.
     *
     * @param className        dot-separated Java class name (e.g. {@code "com.example.Foo"})
     * @param methodName       method name (e.g. {@code "process"} or {@code "<init>"})
     * @param methodDescriptor Java method descriptor in compact dot-notation
     *                         (e.g. {@code "(java.lang.String)void"})
     * @param code             list of code elements in instruction order
     * @param in               corresponding IN states (must match {@code code} length)
     * @return multimap of constant values to their usage descriptions
     */
    public Multimap<Object, ConstantUsage> merge(
            String className,
            String methodName,
            String methodDescriptor,
            List<CodeElement> code,
            List<State> in) {

        Multimap<Object, ConstantUsage> usages = HashMultimap.create();
        int currentLineNumber = -1;
        int index = 0;
        for (var entry : code) {
            if (entry instanceof LineNumber ln) {
                currentLineNumber = ln.line();
                index++;
                continue;
            }

            State state = index < in.size() ? in.get(index) : null;
            if (state == null) {
                index++;
                continue; // unreachable code — skip
            }
            UsageLocation location =
                    locationOf(className, methodName, methodDescriptor, index, currentLineNumber);
            dispatch(entry, state, usages, location);
            index++;
        }
        return usages;
    }

    // -------------------------------------------------------------------------
    // Instruction dispatch
    // -------------------------------------------------------------------------

    private void dispatch(
            CodeElement instr,
            State state,
            Multimap<Object, ConstantUsage> usages,
            UsageLocation location) {

        if (instr == null) return;

        switch (instr) {
            case FieldInstruction fi when fi.opcode() == PUTFIELD ->
                    classifySlot(stackAt(state, TOP), usages, FIELD_STORE,
                            fieldContext(fi, receiverKindOf(stackAt(state, THIRD))),
                            location);

            case FieldInstruction fi when fi.opcode() == PUTSTATIC ->
                    classifySlot(stackAt(state, TOP), usages, STATIC_FIELD_STORE,
                            fieldContext(fi, ReceiverKind.STATIC),
                            location);

            case InvokeInstruction ii -> classifyMethodInvocation(ii, state, usages, location);

            case InvokeDynamicInstruction idi when isStringConcatFactory(idi) ->
                    classifyStringConcatenation(idi, state, usages, location);

            case InvokeDynamicInstruction idi -> classifyDynamicInvocation(idi, state, usages, location);

            case IncrementInstruction ii -> classifyIncrement(ii, state, usages, location);

            case OperatorInstruction oi when oi.opcode() != ARRAYLENGTH ->
                    classifyArithmetic(oi, state, usages, location);

            default -> { /* not a constant-bearing instruction */ }
        }
    }

    // -------------------------------------------------------------------------
    // Per-instruction classifiers
    // -------------------------------------------------------------------------

    private void classifyMethodInvocation(
            InvokeInstruction ii,
            State state,
            Multimap<Object, ConstantUsage> usages,
            UsageLocation location) {

        int paramCount = ii.typeSymbol().parameterCount();
        MethodCallContext ctx;
        if (ii.opcode() != INVOKESTATIC) {
            PointsToSet receiverSlot = stackAt(state, paramCount + 1);
            ctx = new MethodCallContext(
                    toJavaName(ii.owner().asSymbol()),
                    ii.name().stringValue(),
                    toJavaDescriptor(ii.typeSymbol()),
                    receiverKindOf(receiverSlot));
            classifySlot(receiverSlot, usages, METHOD_INVOCATION_TARGET, ctx, location);
        } else {
            ctx = new MethodCallContext(
                    toJavaName(ii.owner().asSymbol()),
                    ii.name().stringValue(),
                    toJavaDescriptor(ii.typeSymbol()),
                    ReceiverKind.STATIC);
        }
        for (int paramPos = 1; paramPos <= paramCount; paramPos++) {
            classifySlot(stackAt(state, paramPos), usages, METHOD_INVOCATION_PARAMETER, ctx, location);
        }
    }

    private void classifyStringConcatenation(
            InvokeDynamicInstruction idi,
            State state,
            Multimap<Object, ConstantUsage> usages,
            UsageLocation location) {

        String pattern = (String) Iterables.getFirst(idi.bootstrapArgs(), "");

        // Classify string literals baked into the invokedynamic recipe.
        patternSplitter.apply(pattern).forEach(literal ->
                usageInterpreterRegistry.interpret(literal, STRING_CONCATENATION_MEMBER, usages, location, StringConcatenationContext.LITERAL));
        // Classify stack arguments that resolved to constants.
        int paramCount = idi.typeSymbol().parameterCount();
        for (int paramPos = 1; paramPos <= paramCount; paramPos++) {
            classifySlot(stackAt(state, paramPos), usages, STRING_CONCATENATION_MEMBER, StringConcatenationContext.RESOLVED_CONSTANT, location);
        }
    }

    private void classifyDynamicInvocation(
            InvokeDynamicInstruction idi,
            State state,
            Multimap<Object, ConstantUsage> usages,
            UsageLocation location) {

        MethodCallContext ctx = new MethodCallContext(
                toJavaName(idi.bootstrapMethod().owner()),
                idi.name().stringValue(),
                toJavaDescriptor(idi.typeSymbol()),
                ReceiverKind.STATIC);

        int paramCount = idi.typeSymbol().parameterCount();
        for (int paramPos = 1; paramPos <= paramCount; paramPos++) {
            classifySlot(stackAt(state, paramPos), usages, METHOD_INVOCATION_PARAMETER, ctx, location);
        }
    }

    private void classifyIncrement(
            IncrementInstruction ii,
            State state,
            Multimap<Object, ConstantUsage> usages,
            UsageLocation location) {

        PointsToSet slot = ii.slot() < state.locals.size() ? state.locals.get(ii.slot()) : null;
        if (slot != null) {
            classifySlot(slot, usages, ARITHMETIC_OPERAND, new ArithmeticOperandContext("+="), location);
        }
    }

    private void classifyArithmetic(
            OperatorInstruction oi,
            State state,
            Multimap<Object, ConstantUsage> usages,
            UsageLocation location) {

        ArithmeticOperandContext ctx = new ArithmeticOperandContext(toJavaOperator(oi.opcode()));
        classifySlot(stackAt(state, TOP), usages, ARITHMETIC_OPERAND, ctx, location);
        if (!UNARY_ARITHMETIC_OPCODES.contains(oi.opcode())) {
            classifySlot(stackAt(state, SECOND), usages, ARITHMETIC_OPERAND, ctx, location);
        }
    }

    // -------------------------------------------------------------------------
    // Core classifier — runs all matching interpreters against a points-to set
    // -------------------------------------------------------------------------

    /**
     * Runs every registered interpreter for {@code usageType} against each constant entity in
     * {@code slot}. Zero-confidence results are dropped when at least one interpreter matched,
     * so a SQL constant passed to a JDBC call is not also recorded as {@code UNKNOWN}.
     */
    private void classifySlot(
            PointsToSet slot,
            Multimap<Object, ConstantUsage> usages,
            UnitConstant.UsageType usageType,
            ConstantUsageInterpreter.InterpretationContext context,
            UsageLocation location) {

        if (slot == null || slot.isEmpty()) return;
        for (var entity : slot) {
            switch (entity) {
                case Constant<?> c ->
                        usageInterpreterRegistry.interpret(c.value(), usageType, usages, location, context);
                case ConstantPropagation cp ->
                        cp.values().forEach(v -> usageInterpreterRegistry.interpret(v, usageType, usages, location, context));
                default -> { /* not a constant value — skip */ }
            }
        }
    }


    // -------------------------------------------------------------------------
    // Context builders
    // -------------------------------------------------------------------------

    private static FieldStoreContext fieldContext(FieldInstruction fi, ReceiverKind receiverKind) {
        return new FieldStoreContext(
                toJavaName(fi.owner().asSymbol()),
                fi.name().stringValue(),
                toJavaName(fi.typeSymbol()),
                receiverKind);
    }

    private static boolean isStringConcatFactory(InvokeDynamicInstruction idi) {
        return idi.name().stringValue().equals("makeConcatWithConstants")
                && idi.bootstrapMethod().owner().equals(ClassDesc.ofInternalName(STRING_CONCAT_FACTORY));
    }

    // -------------------------------------------------------------------------
    // Location / line-number helpers
    // -------------------------------------------------------------------------

    private static UsageLocation locationOf(
            String className, String methodName, String methodDescriptor,
            int instrIndex, int lineNumber) {
        return new UsageLocation(
                className, methodName, methodDescriptor,
                instrIndex, lineNumber >= 0 ? lineNumber : null);
    }

    // -------------------------------------------------------------------------
    // Stack / receiver helpers
    // -------------------------------------------------------------------------

    /**
     * Returns the points-to set at {@code fromTop} positions below the stack top,
     * or {@code null} if the stack is too shallow.
     *
     * @param state   the current abstract state
     * @param fromTop 1 = top of stack, 2 = second from top, etc.
     */
    private static PointsToSet stackAt(State state, int fromTop) {
        int idx = state.stack.size() - fromTop;
        return (idx >= 0) ? state.stack.get(idx) : null;
    }

    /**
     * Returns {@link ReceiverKind#THIS} if the slot contains the {@code this} reference of the
     * enclosing method, {@link ReceiverKind#EXTERNAL_OBJECT} otherwise.
     */
    private static ReceiverKind receiverKindOf(PointsToSet slot) {
        if (slot == null || slot.isEmpty()) return ReceiverKind.EXTERNAL_OBJECT;
        boolean isThis = slot.stream()
                .anyMatch(e -> e instanceof ObjectReference or && or.site().endsWith("::<this>"));
        return isThis ? ReceiverKind.THIS : ReceiverKind.EXTERNAL_OBJECT;
    }

    // -------------------------------------------------------------------------
    // Operator symbol mapping
    // -------------------------------------------------------------------------

    /**
     * Maps a JVM arithmetic, bitwise, or shift opcode to its Java operator symbol.
     *
     * <p>Examples:
     * <ul>
     *   <li>{@code IADD}, {@code DADD}  → {@code "+"}</li>
     *   <li>{@code IMUL}, {@code FMUL}  → {@code "*"}</li>
     *   <li>{@code ISHL}, {@code LSHL}  → {@code "<<"}</li>
     *   <li>{@code IUSHR}               → {@code ">>>"}</li>
     *   <li>{@code IXOR}, {@code LXOR}  → {@code "^"}</li>
     * </ul>
     */
    private static String toJavaOperator(Opcode opcode) {
        return switch (opcode) {
            case IADD, LADD, FADD, DADD -> "+";
            case ISUB, LSUB, FSUB, DSUB,
                 INEG, LNEG, FNEG, DNEG -> "-";
            case IMUL, LMUL, FMUL, DMUL -> "*";
            case IDIV, LDIV, FDIV, DDIV -> "/";
            case IREM, LREM, FREM, DREM -> "%";
            case ISHL, LSHL -> "<<";
            case ISHR, LSHR -> ">>";
            case IUSHR, LUSHR -> ">>>";
            case IAND, LAND -> "&";
            case IOR, LOR -> "|";
            case IXOR, LXOR -> "^";
            default -> opcode.name().toLowerCase();
        };
    }
}
