/**
 * REST API endpoints for uploading and querying constant analysis results.
 *
 * <p>This package provides HTTP endpoints that accept Java bytecode (class or JAR files)
 * and return constant usage analysis results. The API is designed for:
 * <ul>
 *   <li>CI/CD integration (analyze builds automatically)</li>
 *   <li>JDK evolution tracking (compare constants across Java versions)</li>
 *   <li>Migration planning (identify externalization candidates)</li>
 * </ul>
 *
 * <p><b>Main endpoints:</b>
 * <ul>
 *   <li>{@code POST /class?project=X} - Upload class with auto-versioning</li>
 *   <li>{@code PUT /class?project=X&version=Y} - Upload class with explicit version</li>
 *   <li>{@code POST /jar?project=X} - Upload JAR (all classes analyzed)</li>
 *   <li>{@code POST /class/search} - Query stored constants</li>
 * </ul>
 *
 * <p><b>Example: Upload and query</b>
 * <pre>
 * # Upload Java 8's String class
 * curl -X PUT "http://localhost:8080/class?project=jdk&version=8" \
 *      -H "Content-Type: application/octet-stream" \
 *      --data-binary @rt.jar!/java/lang/String.class
 *
 * # Search for String constants
 * curl -X POST "http://localhost:8080/class/search" \
 *      -H "Content-Type: application/json" \
 *      -d '{"key": "java/lang/String"}'
 * </pre>
 *
 * @see org.glodean.constants.web.endpoints.ClassBinariesController
 */
package org.glodean.constants.web.endpoints;

