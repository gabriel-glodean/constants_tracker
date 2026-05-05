/**
 * Solr store implementation — full-text indexing and fuzzy constant search.
 *
 * <p>Solr is written to asynchronously via a transactional outbox pattern: constants
 * are first persisted to PostgreSQL, then drained into Solr by a scheduled processor.
 *
 * <ul>
 *   <li>{@link org.glodean.constants.store.solr.SolrService} — builds and submits
 *       Solr parent/child documents; reads search results.</li>
 *   <li>{@link org.glodean.constants.store.solr.SolrOutboxProcessor} — scheduled
 *       drain loop that moves {@code SolrOutboxEntry} rows from PostgreSQL to Solr;
 *       failed entries are promoted to the dead-letter table.</li>
 *   <li>{@link org.glodean.constants.store.solr.SearchQueryBuilder} — constructs
 *       Solr query strings for fuzzy and exact constant lookups.</li>
 *   <li>{@link org.glodean.constants.store.solr.SolrOutboxPayload} — serialisable
 *       payload stored in the outbox row before indexing.</li>
 *   <li>{@link org.glodean.constants.store.solr.SolrConfiguration} — Spring
 *       {@code @Configuration} that creates the {@code SolrClient} bean from
 *       {@code constants.solr.url}.</li>
 * </ul>
 */
package org.glodean.constants.store.solr;
