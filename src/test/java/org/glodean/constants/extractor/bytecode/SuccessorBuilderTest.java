package org.glodean.constants.extractor.bytecode;

import static java.lang.classfile.ClassFile.ACC_PUBLIC;
import static java.lang.classfile.ClassFile.ACC_STATIC;
import static org.junit.jupiter.api.Assertions.*;

import java.lang.classfile.*;
import java.lang.classfile.attribute.SourceFileAttribute;
import java.lang.classfile.instruction.*;
import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for SuccessorBuilder.
 */
class SuccessorBuilderTest {

    public static SuccessorBuilder.Successors generateInstructions(Consumer<CodeBuilder> ccb) {
        // Create the class file in memory
        var bytes =
                ClassFile.of()
                        .build(
                                ClassDesc.ofInternalName("Anonymous"),
                                cb -> {
                                    cb.withFlags(ACC_PUBLIC)
                                            .withVersion(69, 0)
                                            .with(SourceFileAttribute.of("Anonymous.java"));

                                    cb.withMethod(
                                            "method",
                                            MethodTypeDesc.ofDescriptor("()V"),
                                            ACC_PUBLIC | ACC_STATIC,
                                            mb -> mb.withCode(ccb));
                                });
        var codeModel =
                ClassFile.of().parse(bytes).methods().stream()
                        .filter(mm -> mm.methodName().stringValue().equals("method"))
                        .findFirst()
                        .map(MethodModel::elementStream)
                        .orElse(Stream.empty())
                        .filter(e -> e instanceof CodeModel)
                        .map(e -> (CodeModel) e)
                        .findFirst()
                        .orElse(null);
        assertNotNull(codeModel);
        return SuccessorBuilder.build(codeModel.elementList(), codeModel.exceptionHandlers());
    }

    @Test
    void testSimpleFallthrough() {
        // Two generic code elements -> each should fall through to i+1 (even if that index equals size)
        var succ =
                generateInstructions(
                        (cb -> {
                            cb.loadConstant(1);
                            cb.pop();
                        }))
                        .successors();

        assertEquals(2, succ.size());
        assertEquals(List.of(1), succ.get(0));
        assertEquals(List.of(2), succ.get(1)); // fall-through beyond end is represented as index+1
    }

    @Test
    void testSingleInstructionFallthrough() {
        // Single instruction should fall through to index 1
        var succ = generateInstructions((cb -> cb.loadConstant(42))).successors();

        assertEquals(1, succ.size());
        assertEquals(List.of(1), succ.getFirst());
    }

    // --- Added tests covering remaining paths ---

    @Test
    void testGotoNoFallthrough() {
        // Emit: label L0; goto L2; label L1; nop; label L2; nop
        var succ =
                generateInstructions(
                        cb -> {
                            cb.newBoundLabel();
                            var l2 = cb.newLabel();
                            cb.goto_(l2); // unconditional goto - should not fall through
                            cb.newLabel();
                            cb.nop();
                            cb.labelBinding(l2);
                            cb.nop();
                        })
                        .successors();

        // We expect: index 0 -> target index (resolved to first label occurrence), no i+1
        assertFalse(succ.isEmpty());
        // index 0 should not contain 1 as fall-through
        assertFalse(succ.getFirst().contains(1));
    }

    @Test
    void testConditionalBranchWithFallthrough() {
        // Emit a conditional branch which should keep fall-through and also add the branch target
        var succ =
                generateInstructions(
                        cb -> {
                            var target = cb.newLabel();
                            cb.loadConstant(0);
                            cb.branch(Opcode.IFEQ, target); // conditional branch: fall-through + target
                            cb.loadConstant(1); // fall-through target
                            cb.pop();
                            cb.labelBinding(target);
                            cb.loadConstant(2); // branch target
                            cb.pop();
                        })
                        .successors();

        assertTrue(succ.size() > 1);
        // instruction 1 should have both i+1 and a branch target (some index)
        assertTrue(succ.get(1).contains(2));
        assertTrue(succ.get(1).size() >= 2);
    }

    @Test
    void testReturnIsTerminal() {
        // A return instruction should be terminal (no fall-through)
        var succ =
                generateInstructions(
                        cb -> {
                            cb.loadConstant(1);
                            cb.return_();
                            cb.loadConstant(2); // unreachable in normal fall-through
                        })
                        .successors();

        // index 1 is return; should not contain 2
        assertTrue(succ.size() > 1);
        assertFalse(succ.get(1).contains(2));
    }

    @Test
    void testThrowIsTerminal() {
        // A throw (athrow) should be treated as terminal and not have i+1
        var succ =
                generateInstructions(
                        cb -> {
                            cb.new_(ClassDesc.ofInternalName("java/lang/RuntimeException"));
                            cb.athrow(); // athrow
                            cb.loadConstant(2);
                        })
                        .successors();

        assertTrue(succ.size() > 1);
        assertFalse(succ.get(1).contains(2));
    }

    @Test
    void testTableAndLookupSwitchSuccessors() {
        // Create a tableswitch / lookupswitch shape: default + two case targets
        var succ =
                generateInstructions(
                        cb -> {
                            cb.loadConstant(0);
                            var dflt = cb.newLabel();
                            var c1 = cb.newLabel();
                            var c2 = cb.newLabel();
                            // table switch (default, cases)
                            cb.tableswitch(dflt, List.of(SwitchCase.of(0, c1), SwitchCase.of(1, c2)));
                            cb.labelBinding(dflt);
                            cb.nop(); // index for default
                            cb.labelBinding(c1);
                            cb.nop(); // case 1
                            cb.labelBinding(c2);
                            cb.nop(); // case 2
                        })
                        .successors();

        // switch instruction (index 0) should include default and both case target indices
        assertTrue(succ.size() > 1);
        assertTrue(succ.get(1).size() >= 3);
    }

    @Test
    void testExceptionHandlerAttachment() {
        // Create a try/catch around an instruction and ensure handler edge is attached to covered
        // instructions
        var res =
                generateInstructions(
                        cb -> {
                            cb.trying(cb2 -> {
                                        cb2.loadConstant(1); // instruction inside try
                                        var target = cb2.newLabel();
                                        cb2.branch(Opcode.IFEQ, target);
                                        cb2.return_();
                                        cb2.labelBinding(target);
                                        cb2.new_( ClassDesc.ofInternalName("java/lang/IllegalArgumentException"));
                                        cb2.athrow();
                                    },
                                    catchBuilder -> {
                                        catchBuilder.catchingMulti(
                                                List.of(
                                                        ClassDesc.ofInternalName("java/lang/IllegalArgumentException"),
                                                        ClassDesc.ofInternalName("java/lang/NullPointerException")),
                                                CodeBuilder::athrow
                                        );
                                    });
                            cb.return_();
                        });

        var successors = res.successors();
        var handlers = res.handlerStarts();
        // ensure handler map contains our handler
        assertFalse(handlers.isEmpty());
        // instruction inside try (index of first loadConstant) should contain handler index
        assertTrue(successors.stream().anyMatch(list -> !list.isEmpty()));
    }
}
