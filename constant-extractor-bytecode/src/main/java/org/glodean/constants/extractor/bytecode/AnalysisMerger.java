package org.glodean.constants.extractor.bytecode;

import static java.lang.classfile.Opcode.*;
import static org.glodean.constants.extractor.bytecode.Utils.toInternalName;
import static org.glodean.constants.extractor.bytecode.Utils.toJavaDescriptor;
import static org.glodean.constants.extractor.bytecode.Utils.toJavaName;
import static org.glodean.constants.model.UnitConstant.UsageType.*;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Multimap;

import java.lang.classfile.CodeElement;
import java.lang.classfile.Opcode;
import java.lang.classfile.instruction.*;
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
 */
public record AnalysisMerger(Function<String, Set<String>> patternSplitter,
                             ConstantUsageInterpreterRegistry usageInterpreterRegistry) {
    /**
     * Creates an {@code AnalysisMerger} backed by a default (all-no-op) registry.
     *
     * <p>Useful in contexts where no semantic interpreters have been registered yet.
         * Every discovered constant will be recorded with
         * {@link org.glodean.constants.model.UnitConstant.CoreSemanticType#UNKNOWN} and
     * zero confidence.
     *
     * @param patternSplitter function that splits a string-concat pattern into a set of
     *                        literal substrings
     */
    public AnalysisMerger(Function<String, Set<String>> patternSplitter) {
        this(patternSplitter, ConstantUsageInterpreterRegistry.builder().build());
    }

    /**
     * Creates an {@code AnalysisMerger}.
     *
     * @param patternSplitter          function that splits a string-concat pattern into a set of
     *                                 literal substrings
     * @param usageInterpreterRegistry registry supplying interpreters per usage type
     */
    public AnalysisMerger {
        Objects.requireNonNull(patternSplitter, "patternSplitter cannot be null");
        Objects.requireNonNull(usageInterpreterRegistry, "usageInterpreterRegistry cannot be null");
    }

    /**
     * Merges bytecode-level state information into a multimap of constant value to
     * {@link ConstantUsage}.
     *
     * <p>Each instruction is paired with its IN state. For instructions that consume constants
     * (field stores, method calls, arithmetic, string concatenation) the merger builds the
     * matching context, resolves interpreters from the registry, and calls
     * {@link ConstantUsageInterpreter#interpret(UsageLocation, ConstantUsageInterpreter.InterpretationContext)}
     * for every registered interpreter.
     *
     * <p>Instructions whose IN state is {@code null} (unreachable code) are silently skipped.
     *
     * @param className        slash-separated internal class name (e.g., {@code "com/example/Foo"})
     * @param methodName       method name (e.g., {@code "process"} or {@code "<init>"})
     * @param methodDescriptor JVM method descriptor (e.g., {@code "(Ljava/lang/String;)V"})
     * @param code             list of code elements in instruction order
     * @param in               corresponding IN states for each instruction (must match code length)
     * @return multimap of constant values to their usage descriptions
     */
    public Multimap<Object, ConstantUsage> merge(
            String className,
            String methodName,
            String methodDescriptor,
            List<CodeElement> code,
            List<State> in) {
        Multimap<Object, ConstantUsage> map = HashMultimap.create();
        for (int i = 0; i < code.size(); i++) {
            State state = i < in.size() ? in.get(i) : null;
            if (state == null) continue;
            handle(code.get(i), state, map, className, methodName, methodDescriptor, i);
        }
        return map;
    }

    // -------------------------------------------------------------------------
    // Opcodes that operate on a single operand (right-hand side only)
    // -------------------------------------------------------------------------
    private static final EnumSet<Opcode> UNARY_OPCODES =
            EnumSet.of(
                    INEG, LNEG, FNEG, DNEG,
                    ISHL, LSHL, ISHR, LSHR,
                    IUSHR, LUSHR,
                    IAND, LAND,
                    IOR, LOR,
                    IXOR, LXOR);

    // -------------------------------------------------------------------------
    // Outer dispatch: build context and delegate to the inner handler
    // -------------------------------------------------------------------------
    private void handle(
            CodeElement instr,
            State state,
            Multimap<Object, ConstantUsage> map,
            String className,
            String methodName,
            String methodDescriptor,
            int instrIndex) {
        if (instr == null) return;
        UsageLocation location = new UsageLocation(className, methodName, methodDescriptor, instrIndex, null);
        switch (instr) {

            // -- instance field store (putfield) -----------------------------------
            case FieldInstruction fi when fi.opcode() == PUTFIELD -> {
                ReceiverKind rk = receiverKindOf(stackAt(state, 2));
                handle(stackAt(state, 1), map, FIELD_STORE,
                        new FieldStoreContext(
                                toJavaName(fi.owner().asSymbol()),
                                fi.name().stringValue(),
                                toJavaName(fi.typeSymbol()),
                                rk),
                        location);
            }

            // -- static field store (putstatic) ------------------------------------
            case FieldInstruction fi when fi.opcode() == PUTSTATIC -> handle(stackAt(state, 1), map, STATIC_FIELD_STORE,
                    new FieldStoreContext(
                            toJavaName(fi.owner().asSymbol()),
                            fi.name().stringValue(),
                            toJavaName(fi.typeSymbol()),
                            ReceiverKind.STATIC),
                    location);

            // -- regular method invocation -----------------------------------------
            case InvokeInstruction ii -> {
                int paramCount = ii.typeSymbol().parameterCount();
                ReceiverKind rk = ii.opcode() == INVOKESTATIC
                        ? ReceiverKind.STATIC
                        : receiverKindOf(stackAt(state, paramCount + 1));
                MethodCallContext ctx = new MethodCallContext(
                        ii.owner().name().stringValue(),
                        ii.name().stringValue(),
                        toJavaDescriptor(ii.typeSymbol()),
                        rk);
                for (int idx = 1; idx <= paramCount; idx++) {
                    handle(stackAt(state, idx), map, METHOD_INVOCATION_PARAMETER, ctx, location);
                }
                if (ii.opcode() != INVOKESTATIC) {
                    handle(stackAt(state, paramCount + 1), map, METHOD_INVOCATION_TARGET, ctx, location);
                }
            }

            // -- invokedynamic: string concatenation --------------------------------
            case InvokeDynamicInstruction idi when
                    idi.name().stringValue().equals("makeConcatWithConstants")
                            && idi.bootstrapMethod().owner()
                            .equals(ClassDesc.ofInternalName("java/lang/invoke/StringConcatFactory")) -> {
                String pattern = (String) Iterables.getFirst(idi.bootstrapArgs(), "");
                StringConcatenationContext literalCtx =
                        new StringConcatenationContext(StringConcatenationContext.ConstantSource.LITERAL);
                StringConcatenationContext resolvedCtx =
                        new StringConcatenationContext(StringConcatenationContext.ConstantSource.RESOLVED_CONSTANT);
                // literals baked into the invokedynamic recipe
                Collection<ConstantUsageInterpreter> interps =
                        usageInterpreterRegistry.getInterpreters(STRING_CONCATENATION_MEMBER);
                for (String literal : patternSplitter.apply(pattern)) {
                    for (var interp : interps) {
                        map.put(literal, interp.interpret(location, literalCtx));
                    }
                }
                // stack arguments resolved to constants
                for (int idx = 1; idx <= idi.typeSymbol().parameterCount(); idx++) {
                    handle(stackAt(state, idx), map, STRING_CONCATENATION_MEMBER, resolvedCtx, location);
                }
            }

            // -- invokedynamic: other (lambdas, etc.) ------------------------------
            case InvokeDynamicInstruction idi -> {
                MethodCallContext ctx = new MethodCallContext(
                        toInternalName(idi.bootstrapMethod().owner()),
                        idi.name().stringValue(),
                        toJavaDescriptor(idi.typeSymbol()),
                        ReceiverKind.STATIC);
                for (int idx = 1; idx <= idi.typeSymbol().parameterCount(); idx++) {
                    handle(stackAt(state, idx), map, METHOD_INVOCATION_PARAMETER, ctx, location);
                }
            }

            // -- iinc: increment a local variable ----------------------------------
            case IncrementInstruction ii -> {
                PointsToSet slot = ii.slot() < state.locals.size() ? state.locals.get(ii.slot()) : null;
                if (slot != null) {
                    handle(slot, map, ARITHMETIC_OPERAND, new ArithmeticOperandContext("+="), location);
                }
            }

            // -- arithmetic / bitwise operators ------------------------------------
            case OperatorInstruction oi when oi.opcode() != ARRAYLENGTH -> {
                ArithmeticOperandContext ctx = new ArithmeticOperandContext(toJavaOperator(oi.opcode()));
                handle(stackAt(state, 1), map, ARITHMETIC_OPERAND, ctx, location);
                if (!UNARY_OPCODES.contains(oi.opcode())) {
                    handle(stackAt(state, 2), map, ARITHMETIC_OPERAND, ctx, location);
                }
            }

            default -> {
            }
        }
    }

    // -------------------------------------------------------------------------
    // Inner handler: iterate entities, call every matching interpreter
    // -------------------------------------------------------------------------
    private void handle(
            PointsToSet slot,
            Multimap<Object, ConstantUsage> map,
            UnitConstant.UsageType usageType,
            ConstantUsageInterpreter.InterpretationContext context,
            UsageLocation location) {
        if (slot == null || slot.isEmpty()) return;
        Collection<ConstantUsageInterpreter> interpreters =
                usageInterpreterRegistry.getInterpreters(usageType);
        if (interpreters.isEmpty()) return;
        for (var entity : slot) {
            if (entity instanceof Constant<?> c) {
                for (var interp : interpreters) {
                    map.put(c.value(), interp.interpret(location, context));
                }
            }
            if (entity instanceof ConstantPropagation(Set<Number> values, _)) {
                values.forEach(v -> {
                    for (var interp : interpreters) {
                        map.put(v, interp.interpret(location, context));
                    }
                });
            }
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Returns the points-to set at {@code fromTop} positions below the top of the stack,
     * or {@code null} if the stack is too shallow.
     *
     * @param state   the current abstract state
     * @param fromTop 1 = top of stack, 2 = second from top, etc.
     */
    private static PointsToSet stackAt(State state, int fromTop) {
        int idx = state.stack.size() - fromTop;
        return (idx >= 0 && idx < state.stack.size()) ? state.stack.get(idx) : null;
    }

    /**
     * Determines the {@link ReceiverKind} for a given receiver slot by checking whether
     * any entity in the slot is the {@code this} reference of the enclosing method.
     *
     * @param slot the points-to set for the receiver; may be {@code null}
     * @return {@link ReceiverKind#THIS} if the slot contains only the {@code this} reference,
     * {@link ReceiverKind#EXTERNAL_OBJECT} otherwise
     */
    private static ReceiverKind receiverKindOf(PointsToSet slot) {
        if (slot == null || slot.isEmpty()) return ReceiverKind.EXTERNAL_OBJECT;
        boolean isThis = slot.stream()
                .anyMatch(e -> e instanceof ObjectReference or && or.site().endsWith("::<this>"));
        return isThis ? ReceiverKind.THIS : ReceiverKind.EXTERNAL_OBJECT;
    }

    /**
     * Maps a JVM arithmetic, bitwise, or shift opcode to its Java operator symbol.
     *
     * <p>Examples:
     * <ul>
     *   <li>{@code IADD}, {@code DADD} → {@code "+"}</li>
     *   <li>{@code IMUL}, {@code FMUL} → {@code "*"}</li>
     *   <li>{@code ISHL}, {@code LSHL} → {@code "<<"}</li>
     *   <li>{@code IUSHR}             → {@code ">>>"}</li>
     *   <li>{@code IXOR}, {@code LXOR} → {@code "^"}</li>
     * </ul>
     *
     * @param opcode the JVM opcode
     * @return the corresponding Java operator symbol
     */
    private static String toJavaOperator(Opcode opcode) {
        return switch (opcode) {
            case IADD, LADD, FADD, DADD -> "+";
            case ISUB, LSUB, FSUB, DSUB, INEG, LNEG, FNEG, DNEG -> "-";
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
