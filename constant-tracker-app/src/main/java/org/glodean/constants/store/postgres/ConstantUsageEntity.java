package org.glodean.constants.store.postgres;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

/** R2DBC entity for a single constant usage observation in the {@code constant_usages} table. */
@Table("constant_usages")
public record ConstantUsageEntity(
    @Id Long id,
    Long constantId,
    String structuralType,
    String semanticTypeKind, // 'CORE' or 'CUSTOM'
    String semanticTypeName, // CoreSemanticType.name() or CustomSemanticType.category()
    String semanticDisplayName, // CUSTOM only
    String semanticDescription, // CUSTOM only
    String locationClassName,
    String locationMethodName,
    String locationMethodDescriptor,
    Integer locationBytecodeOffset,
    Integer locationLineNumber,
    double confidence,
    String metadata) {}

