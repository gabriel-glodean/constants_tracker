/**
 * Request and response DTOs (Data Transfer Objects) for the HTTP API layer.
 *
 * <p>All types are immutable Java records. They are used exclusively at the
 * controller boundary and must not leak into the service or store layers.
 *
 * <ul>
 *   <li><b>Auth</b> — {@link org.glodean.constants.dto.LoginRequest},
 *       {@link org.glodean.constants.dto.TokenResponse},
 *       {@link org.glodean.constants.dto.AuthStatusResponse}</li>
 *   <li><b>Constants</b> — {@link org.glodean.constants.dto.GetUnitConstantsReply}</li>
 *   <li><b>Search</b> — {@link org.glodean.constants.dto.FuzzySearchResponse},
 *       {@link org.glodean.constants.dto.FuzzySearchHit}</li>
 *   <li><b>Diff</b> — {@link org.glodean.constants.dto.ProjectDiffResponse},
 *       {@link org.glodean.constants.dto.UnitDiff},
 *       {@link org.glodean.constants.dto.ConstantDiffEntry}</li>
 *   <li><b>Detail</b> — {@link org.glodean.constants.dto.UsageDetail}</li>
 * </ul>
 */
package org.glodean.constants.dto;
