package org.glodean.constants.extractor.bytecode.handlers;

import java.lang.constant.ClassDesc;
import org.glodean.constants.extractor.bytecode.types.ObjectReference;
import org.glodean.constants.extractor.bytecode.types.PointsToSet;
import org.glodean.constants.extractor.bytecode.types.State;

public class ExceptionHandlerLabelHandler {
  public void handle(ClassDesc catchType, State state, String tag) {
    state.stack.clear();
    state.stack.addLast(PointsToSet.of(new ObjectReference(catchType, tag)));
  }
}
