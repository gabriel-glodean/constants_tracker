package org.glodean.constants.extractor.bytecode;

import com.google.common.collect.Iterables;
import org.glodean.constants.model.ClassConstant;
import org.glodean.constants.model.ClassConstants;
import org.glodean.constants.samples.Greeter;
import org.glodean.constants.samples.SimpleIteration;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

import static org.glodean.constants.extractor.bytecode.TestUtils.convertClassToModel;
import static org.glodean.constants.model.ClassConstant.UsageType.*;
import static org.junit.jupiter.api.Assertions.*;

class ClassModelExtractorTest {

    @Test
    void extractSimpleIntegers() throws IOException {
        var model = Iterables.getFirst(new ClassModelExtractor(convertClassToModel(SimpleIteration.class)).extract(), null);
        assertNotNull(model);
        var expected = new ClassConstants("org/glodean/constants/samples/SimpleIteration",
                Set.of(
                        new ClassConstant(0, EnumSet.of(PROPAGATION_IN_ARITHMETIC_OPERATIONS)),
                        new ClassConstant(1, EnumSet.of(PROPAGATION_IN_ARITHMETIC_OPERATIONS))
                ));
        assertEquals(expected, model);
    }

    @Test
    void extractGreeter() throws IOException {
        var model = Iterables.getFirst(new ClassModelExtractor(convertClassToModel(Greeter.class)).extract(), null);
        assertNotNull(model);
        var expected = new ClassConstants("org/glodean/constants/samples/Greeter",
                Set.of(
                        new ClassConstant("Default", EnumSet.of(METHOD_INVOCATION_PARAMETER)),
                        new ClassConstant(Greeter.FORMAT, EnumSet.of(METHOD_INVOCATION_TARGET)),
                        new ClassConstant(Greeter.wackyFormat, EnumSet.of(STATIC_FIELD_STORE))
                ));
        assertEquals(expected, model);
    }
}