package org.glodean.constants.extractor.bytecode.types;

import java.lang.constant.ClassDesc;
import java.util.Objects;

public record PrimitiveValue(ClassDesc descriptor, String site)  implements ConstantPropagatingEntity {

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        PrimitiveValue that = (PrimitiveValue) o;
        return Objects.equals(descriptor, that.descriptor) && Objects.equals(site, that.site);
    }

    @Override
    public int hashCode() {
        return Objects.hash(descriptor, site);
    }

    @Override
    public String toString() {
        return descriptor.descriptorString() + "@" + site;
    }

    @Override
    public ConstantPropagatingEntity propagate(ConstantPropagatingEntity constant) {
        return constant;
    }
}
