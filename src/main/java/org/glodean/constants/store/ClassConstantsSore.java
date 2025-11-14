package org.glodean.constants.store;

import java.util.Collection;
import java.util.Map;
import org.glodean.constants.model.ClassConstant;
import org.glodean.constants.model.ClassConstants;
import reactor.core.publisher.Mono;

public interface ClassConstantsSore {
  Mono<ClassConstants> store(ClassConstants constants, String project, int version);

  Mono<ClassConstants> store(ClassConstants constants, String project);

  Mono<Map<Object, Collection<ClassConstant.UsageType>>> find(String key);
}
