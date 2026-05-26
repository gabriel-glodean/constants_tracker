package org.glodean.constants.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ConstantValueTypeMapperTest {

  @Test
  void mapRawTypeNameMapsJvmSpecificNames() {
    assertThat(ConstantValueTypeMapper.mapRawTypeName("AnonymousDynamicConstantDesc"))
        .isEqualTo("DynamicConstant");
    assertThat(ConstantValueTypeMapper.mapRawTypeName("ClassOrInterfaceDescImpl"))
        .isEqualTo("ClassDescriptor");
    assertThat(ConstantValueTypeMapper.mapRawTypeName("ArrayClassDescImpl"))
        .isEqualTo("ArrayDesc");
  }

  @Test
  void mapRawTypeNameMapsNullToNull() {
    assertThat(ConstantValueTypeMapper.mapRawTypeName("null")).isEqualTo("Null");
    assertThat(ConstantValueTypeMapper.mapRawTypeName(null)).isEqualTo("Null");
  }

  @Test
  void mapUsesRuntimeTypeOrNullFallback() {
    assertThat(ConstantValueTypeMapper.map("abc")).isEqualTo("String");
    assertThat(ConstantValueTypeMapper.map(123)).isEqualTo("Integer");
    assertThat(ConstantValueTypeMapper.map(null)).isEqualTo("Null");
  }
}
