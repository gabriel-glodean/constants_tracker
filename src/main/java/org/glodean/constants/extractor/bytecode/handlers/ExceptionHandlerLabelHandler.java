package org.glodean.constants.extractor.bytecode.handlers;

import java.lang.constant.ClassDesc;
import java.util.Set;
import org.glodean.constants.extractor.bytecode.types.ObjectReference;
import org.glodean.constants.extractor.bytecode.types.PointsToSet;
import org.glodean.constants.extractor.bytecode.types.State;

public class ExceptionHandlerLabelHandler {
  public void handle(Set<ClassDesc> catchTypes, State state, String tag) {
    state.stack.clear();
    ClassDesc catchType =
        catchTypes.size() == 1
            ? catchTypes.iterator().next()
            : ClassDesc.ofInternalName("java/lang/Throwable");
    state.stack.addLast(PointsToSet.of(new ObjectReference(catchType, tag)));
  }
}
