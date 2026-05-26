package org.glodean.constants.extractor;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import org.glodean.constants.model.SourceKind;

/**
 * Repository of {@link ModelExtractor} suppliers, keyed by a file-name predicate.
 *
 * <p>Each registration maps a file-name test to a {@link SourceKind} and a factory that
 * produces a {@link ModelExtractor} given raw file bytes. Extensions are matched
 * case-insensitively; the first matching registration wins.
 *
 * <p>Typical usage (via {@link Builder}):
 * <pre>{@code
 * ModelExtractorSupplierRepository repo = ModelExtractorSupplierRepository.builder()
 *     .register(n -> n.endsWith(".yml") || n.endsWith(".yaml"),
 *               ConfigFileSourceKind.YAML, YamlConstantsExtractor::new)
 *     .register(n -> n.endsWith(".properties"),
 *               ConfigFileSourceKind.PROPERTIES, PropertiesConstantsExtractor::new)
 *     .build();
 *
 * repo.resolve("application.yml", bytes)
 *     .ifPresent(supply -> supply.extractor().extract(supply.descriptorFor("application.yml", bytes.length)));
 * }</pre>
 */
public final class ModelExtractorSupplierRepository {

    /**
     * The result of a successful {@link #resolve} call: a ready-to-use extractor
     * paired with its {@link SourceKind} so callers can build a {@link org.glodean.constants.model.UnitDescriptor}.
     */
    public record Supply(ModelExtractor extractor, SourceKind sourceKind) {}

    private record Entry(
            Predicate<String> test,
            SourceKind sourceKind,
            Function<byte[], ModelExtractor> factory) {}

    private final List<Entry> entries;

    private ModelExtractorSupplierRepository(List<Entry> entries) {
        this.entries = List.copyOf(entries);
    }

    /**
     * Finds the first registered supplier whose predicate matches {@code key}
     * (compared lower-case) and instantiates a {@link ModelExtractor} from {@code content}.
     * Always pass the file name as the lookup key (e.g. {@code "Foo.class"},
     * {@code "application.yml"}).
     *
     * @param key     file name to look up (matched lower-cased against registered predicates)
     * @param content raw file bytes passed to the supplier factory
     * @return a {@link Supply} containing the extractor and its source kind,
     *         or {@link Optional#empty()} if no registration matches
     */
    public Optional<Supply> resolve(String key, byte[] content) {
        String lower = key.toLowerCase();
        return entries.stream()
                .filter(e -> e.test().test(lower))
                .findFirst()
                .map(e -> new Supply(e.factory().apply(content), e.sourceKind()));
    }

    /**
     * Returns the distinct source kinds that currently have a registered extractor supplier.
     */
    public Set<SourceKind> supportedSourceKinds() {
        return entries.stream()
                .map(Entry::sourceKind)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
    }


    /** Returns a new {@link Builder}. */
    public static Builder builder() {
        return new Builder();
    }

    /** Fluent builder for {@link ModelExtractorSupplierRepository}. */
    public static final class Builder {

        private final List<Entry> entries = new ArrayList<>();

        private Builder() {}

        /**
         * Registers a supplier for files whose lower-cased name satisfies {@code fileNameTest}.
         *
         * @param fileNameTest predicate evaluated against the lower-cased file name
         * @param sourceKind   the {@link SourceKind} to associate with matched files
         * @param factory      factory that creates a {@link ModelExtractor} from raw bytes
         */
        public Builder register(
                Predicate<String> fileNameTest,
                SourceKind sourceKind,
                Function<byte[], ModelExtractor> factory) {
            entries.add(new Entry(fileNameTest, sourceKind, factory));
            return this;
        }


        /** Builds an immutable {@link ModelExtractorSupplierRepository}. */
        public ModelExtractorSupplierRepository build() {
            return new ModelExtractorSupplierRepository(entries);
        }
    }
}
