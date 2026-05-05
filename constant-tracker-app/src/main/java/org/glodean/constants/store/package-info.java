/**
 * Storage abstraction layer for persisting and retrieving constant snapshots.
 *
 * <p>Defines the public SPI that all backend implementations must satisfy:
 * <ul>
 *   <li>{@link org.glodean.constants.store.UnitConstantsStore} — reactive interface
 *       for storing and querying {@code UnitConstants} snapshots.</li>
 *   <li>{@link org.glodean.constants.store.VersionIncrementer} — strategy interface
 *       for assigning monotonically increasing version numbers per project.</li>
 *   <li>{@link org.glodean.constants.store.CompositeUnitConstantsStore} — fan-out
 *       implementation that writes to multiple backends simultaneously.</li>
 *   <li>{@link org.glodean.constants.store.Constants} — shared string constants
 *       (field names, collection identifiers) used across store implementations.</li>
 * </ul>
 *
 * <p>Concrete implementations live in sub-packages:
 * {@code store.postgres}, {@code store.solr}, and {@code store.redis}.
 */
package org.glodean.constants.store;
