package org.glodean.constants.web.endpoints;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import com.google.common.base.Strings;
import org.glodean.constants.dto.MetadataOptionResponse;
import org.glodean.constants.dto.MetadataResponse;
import org.glodean.constants.model.UnitConstant;
import org.glodean.constants.model.UnitConstant.CoreSemanticType;
import org.glodean.constants.model.UnitConstant.CustomSemanticType;
import org.glodean.constants.store.SemanticTypeStore;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/metadata")
public class MetadataController {

  private static final Set<String> DEFAULT_CONSTANT_TYPES = Set.of(
	  "String",
	  "Integer",
	  "Long",
	  "Float",
	  "Double",
	  "Boolean",
	  "Byte",
	  "Short",
	  "Character",
	  "Null",
	  "DynamicConstant",
	  "ClassDescriptor",
	  "ArrayDesc",
	  "MethodHandle");

  private final SemanticTypeStore semanticTypeStore;

  public MetadataController(SemanticTypeStore semanticTypeStore) {
	this.semanticTypeStore = semanticTypeStore;
  }

  @GetMapping
  public Mono<MetadataResponse> metadata() {
  return types().map(types -> new MetadataResponse(types, structuralTypes(), semanticTypes()));
  }

  @GetMapping("/types")
  public Mono<List<MetadataOptionResponse>> typesMetadata() {
  return types();
  }

  @GetMapping("/structural-types")
  public Mono<List<MetadataOptionResponse>> structuralTypesMetadata() {
	return Mono.just(structuralTypes());
  }

  @GetMapping("/semantic-types")
  public Mono<List<MetadataOptionResponse>> semanticTypesMetadata() {
	return Mono.just(semanticTypes());
  }

  private Mono<List<MetadataOptionResponse>> types() {
	return Mono.just(
		DEFAULT_CONSTANT_TYPES.stream()
			.map(type -> new MetadataOptionResponse(type, type))
			.sorted(Comparator.comparing(MetadataOptionResponse::name, String.CASE_INSENSITIVE_ORDER))
			.toList());
  }

  private List<MetadataOptionResponse> structuralTypes() {
	return Arrays.stream(UnitConstant.UsageType.values())
		.map(type -> new MetadataOptionResponse(type.name(), humanize(type.name())))
		.toList();
  }

  private List<MetadataOptionResponse> semanticTypes() {
	return semanticTypeStore.getSupportedSemanticTypes().stream()
		.map(this::toSemanticTypeOption)
		.sorted(Comparator.comparing(MetadataOptionResponse::displayName, String.CASE_INSENSITIVE_ORDER)
			.thenComparing(MetadataOptionResponse::name, String.CASE_INSENSITIVE_ORDER))
		.toList();
  }

  private MetadataOptionResponse toSemanticTypeOption(UnitConstant.SemanticType semanticType) {
	String name;
	if (semanticType instanceof CoreSemanticType coreSemanticType) {
	  name = coreSemanticType.name();
	} else if (semanticType instanceof CustomSemanticType customSemanticType) {
	  name = customSemanticType.category();
	} else {
	  throw new IllegalArgumentException("Unsupported semantic type: " + semanticType.getClass());
	}
	return new MetadataOptionResponse(name, Strings.nullToEmpty(semanticType.displayName()));
  }

  private static String humanize(String value) {
	return Arrays.stream(value.toLowerCase(Locale.ROOT).split("_"))
		.map(part -> Character.toUpperCase(part.charAt(0)) + part.substring(1))
		.reduce((left, right) -> left + " " + right)
		.orElse(value);
  }
}
