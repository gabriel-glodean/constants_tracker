package org.glodean.constants.extractor.bytecode;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import java.lang.classfile.Attributes;
import java.lang.classfile.ClassModel;
import java.lang.classfile.FieldModel;
import java.lang.classfile.constantpool.DoubleEntry;
import java.lang.classfile.constantpool.FloatEntry;
import java.lang.classfile.constantpool.IntegerEntry;
import java.lang.classfile.constantpool.LongEntry;
import java.lang.classfile.constantpool.StringEntry;
import java.lang.reflect.AccessFlag;
import java.util.Collection;
import org.glodean.constants.interpreter.ConstantUsageInterpreter;
import org.glodean.constants.interpreter.FieldStoreContext;
import org.glodean.constants.interpreter.ReceiverKind;
import org.glodean.constants.model.UnitConstant.ConstantUsage;
import org.glodean.constants.model.UnitConstant.UsageLocation;
import org.glodean.constants.model.UnitConstant.UsageType;

/**
 * Extracts constant values stored in the {@code ConstantValue} attribute of {@code static final}
 * fields.
 *
 * <p>The Java compiler emits a {@code ConstantValue} attribute (rather than {@code <clinit>}
 * bytecode) for {@code static final} fields whose initializer is a compile-time constant — a
 * {@code String} literal or a primitive value. These values would otherwise be invisible to the
 * method-level bytecode analysis performed by {@link ByteCodeMethodAnalyzer}.
 *
 * <p>Each discovered value is dispatched to the {@link ConstantUsageInterpreterRegistry}
 * under {@link UsageType#STATIC_FIELD_STORE}. The {@link FieldStoreContext} carries the field
 * name, descriptor, and {@link ReceiverKind#STATIC} so that registered interpreters can apply
 * semantic classification.
 */
final class StaticFinalFieldConstantExtractor {

  private final ConstantUsageInterpreterRegistry registry;

  StaticFinalFieldConstantExtractor(ConstantUsageInterpreterRegistry registry) {
    this.registry = registry;
  }

  /**
   * Scans all fields in {@code model} and returns a multimap of constant value → usages for every
   * {@code static final} field that carries a {@code ConstantValue} attribute.
   *
   * @param model     the class model to scan
   * @param className dot-separated (Java) class name used in {@link UsageLocation}
   * @return multimap of raw constant values to their {@link ConstantUsage} descriptions
   */
  Multimap<Object, ConstantUsage> extract(ClassModel model, String className) {
    Multimap<Object, ConstantUsage> map = HashMultimap.create();

    for (FieldModel fm : model.fields()) {
      // Only fields that are both static and final can have a ConstantValue attribute
      if (!fm.flags().has(AccessFlag.STATIC) || !fm.flags().has(AccessFlag.FINAL)) {
        continue;
      }

      fm.findAttribute(Attributes.constantValue()).ifPresent(attr -> {
        Object value = switch (attr.constant()) {
          case StringEntry  s -> s.stringValue();
          case IntegerEntry i -> i.intValue();
          case LongEntry    l -> l.longValue();
          case FloatEntry   f -> f.floatValue();
          case DoubleEntry  d -> d.doubleValue();
          default -> null;
        };

        if (value == null) {
          return;
        }

        String fieldName = fm.fieldName().stringValue();
        String fieldDescriptor = fm.fieldType().stringValue();

        // bytecodeOffset=0 — no instruction offset for a field attribute
        UsageLocation location =
            new UsageLocation(className, "<field:" + fieldName + ">", fieldDescriptor, 0, null);

        FieldStoreContext ctx =
            new FieldStoreContext(className, fieldName, fieldDescriptor, ReceiverKind.STATIC);

        Collection<ConstantUsageInterpreter> interpreters =
            registry.getInterpreters(UsageType.STATIC_FIELD_STORE);
        for (ConstantUsageInterpreter interp : interpreters) {
          map.put(value, interp.interpret(location, ctx));
        }
      });
    }

    return map;
  }
}

