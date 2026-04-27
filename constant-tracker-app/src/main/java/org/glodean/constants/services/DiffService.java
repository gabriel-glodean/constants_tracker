package org.glodean.constants.services;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.glodean.constants.dto.ConstantDiffEntry;
import org.glodean.constants.dto.ProjectDiffResponse;
import org.glodean.constants.dto.UnitDiff;
import org.glodean.constants.dto.UsageDetail;
import org.glodean.constants.store.postgres.entity.ConstantDiffRow;
import org.glodean.constants.store.postgres.entity.DiffRepository;
import org.glodean.constants.store.postgres.repository.ProjectVersionRepository;
import org.glodean.constants.store.postgres.repository.UnitDescriptorRepository;
import org.glodean.constants.store.postgres.repository.UnitSnapshotRepository;
import org.glodean.constants.store.postgres.repository.VersionDeletionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * Computes a constant-level diff between two project versions, fully respecting the inheritance
 * chain and deletion records.
 *
 * <h2>Algorithm</h2>
 * <ol>
 *   <li>Resolve the <em>effective</em> {@code Map<path, snapshotId>} for each version by walking
 *       the parent chain and honouring {@code version_deletions}.</li>
 *   <li>Skip paths whose {@code snapshotId} is identical in both versions (unchanged).</li>
 *   <li>Fire one batched 2-table SQL query per side ({@code unit_constants JOIN constant_usages}).
 *       Path is recovered in-memory via an inverted {@code snapshotId → path} map.</li>
 *   <li>Group rows by path + constant value and compute added / removed / changed entries.</li>
 * </ol>
 */
@Service
public class DiffService {

  private static final Logger logger = LogManager.getLogger(DiffService.class);

  private final ProjectVersionRepository versionRepo;
  private final UnitDescriptorRepository descriptorRepo;
  private final UnitSnapshotRepository snapshotRepo;
  private final VersionDeletionRepository deletionRepo;
  private final DiffRepository diffRepo;

  public DiffService(
      @Autowired ProjectVersionRepository versionRepo,
      @Autowired UnitDescriptorRepository descriptorRepo,
      @Autowired UnitSnapshotRepository snapshotRepo,
      @Autowired VersionDeletionRepository deletionRepo,
      @Autowired DiffRepository diffRepo) {
    this.versionRepo = versionRepo;
    this.descriptorRepo = descriptorRepo;
    this.snapshotRepo = snapshotRepo;
    this.deletionRepo = deletionRepo;
    this.diffRepo = diffRepo;
  }

  /**
   * Computes the full constant-level diff between {@code fromVersion} and {@code toVersion}
   * for the given project.
   *
   * @throws IllegalArgumentException if either version does not exist for the project
   */
  public Mono<ProjectDiffResponse> diff(String project, int fromVersion, int toVersion) {
    Mono<Map<String, Long>> fromMono = resolveEffectiveSnapshots(project, fromVersion);
    Mono<Map<String, Long>> toMono   = resolveEffectiveSnapshots(project, toVersion);

    return Mono.zip(fromMono, toMono)
        .flatMap(tuple -> {
          Map<String, Long> fromMap = tuple.getT1();
          Map<String, Long> toMap   = tuple.getT2();

          Set<String> allPaths = new LinkedHashSet<>();
          allPaths.addAll(fromMap.keySet());
          allPaths.addAll(toMap.keySet());

          Set<Long> fromSnapshotIds = new HashSet<>();
          Set<Long> toSnapshotIds   = new HashSet<>();
          Set<String> purelyAdded   = new HashSet<>();
          Set<String> purelyRemoved = new HashSet<>();

          for (String path : allPaths) {
            Long fromId = fromMap.get(path);
            Long toId   = toMap.get(path);
            if (fromId == null) {
              purelyAdded.add(path);
              toSnapshotIds.add(toId);
            } else if (toId == null) {
              purelyRemoved.add(path);
              fromSnapshotIds.add(fromId);
            } else if (!fromId.equals(toId)) {
              // Same path, different snapshot → need to compare constants
              fromSnapshotIds.add(fromId);
              toSnapshotIds.add(toId);
            }
            // else same snapshotId → identical, skip entirely
          }

          logger.atInfo().log(
              "Diff {}: from={} to={} | added={} removed={} changed-from={} changed-to={}",
              project, fromVersion, toVersion,
              purelyAdded.size(), purelyRemoved.size(),
              fromSnapshotIds.size(), toSnapshotIds.size());

          // Build snapshotId → path lookup (covers both sides)
          Map<Long, String> snapshotToPath = new HashMap<>();
          fromMap.forEach((p, sid) -> snapshotToPath.put(sid, p));
          toMap.forEach((p, sid) -> snapshotToPath.put(sid, p));

          Mono<Map<String, Map<String, List<UsageDetail>>>> fromConstsMono =
              diffRepo.loadForSnapshots(fromSnapshotIds)
                  .collectList()
                  .map(rows -> groupByPathAndValue(rows, snapshotToPath));
          Mono<Map<String, Map<String, List<UsageDetail>>>> toConstsMono =
              diffRepo.loadForSnapshots(toSnapshotIds)
                  .collectList()
                  .map(rows -> groupByPathAndValue(rows, snapshotToPath));

          return Mono.zip(fromConstsMono, toConstsMono)
              .map(ct -> {
                Map<String, Map<String, List<UsageDetail>>> fromC = ct.getT1();
                Map<String, Map<String, List<UsageDetail>>> toC   = ct.getT2();

                List<UnitDiff> units = new ArrayList<>();

                for (String path : purelyRemoved) {
                  var entries = buildRemovedEntries(fromC.getOrDefault(path, Map.of()));
                  units.add(new UnitDiff(path, false, true, entries));
                }

                for (String path : purelyAdded) {
                  var entries = buildAddedEntries(toC.getOrDefault(path, Map.of()));
                  units.add(new UnitDiff(path, true, false, entries));
                }

                for (String path : allPaths) {
                  if (purelyAdded.contains(path) || purelyRemoved.contains(path)) continue;
                  Long fromId = fromMap.get(path);
                  Long toId   = toMap.get(path);
                  if (fromId == null || toId == null || fromId.equals(toId)) continue;

                  var entries = compareConstants(
                      fromC.getOrDefault(path, Map.of()),
                      toC.getOrDefault(path, Map.of()));
                  if (!entries.isEmpty()) {
                    units.add(new UnitDiff(path, false, false, entries));
                  }
                }

                return new ProjectDiffResponse(project, fromVersion, toVersion, units);
              });
        });
  }

  // ── Effective snapshot resolution ────────────────────────────────────────

  /**
   * Produces the effective {@code path → snapshotId} map for a version by walking the
   * parent chain and excluding any paths present in {@code version_deletions}.
   */
  private Mono<Map<String, Long>> resolveEffectiveSnapshots(String project, int version) {
    return versionRepo.findByProjectAndVersion(project, version)
        .switchIfEmpty(Mono.error(new IllegalArgumentException(
            "Version " + version + " does not exist for project " + project)))
        .flatMap(_ -> collectSnapshotMap(project, version, new HashSet<>()));
  }

  /**
   * Recursively collects the effective {@code path → snapshotId} map for one version level.
   *
   * @param deletedPaths paths already marked as deleted by a child version — must not be
   *                     re-introduced by ancestor versions
   */
  private Mono<Map<String, Long>> collectSnapshotMap(
      String project, int version, Set<String> deletedPaths) {

    // Collect deletions declared at this version, then merge with those from child versions
    Mono<Set<String>> thisDeletionsMono = deletionRepo
        .findAllByProjectAndVersion(project, version)
        .map(e -> e.unitPath())
        .collect(Collectors.toSet());

    // Load own descriptors and resolve their snapshot IDs
    Mono<Map<String, Long>> ownMono = descriptorRepo
        .findAllByProjectAndVersion(project, version)
        .flatMap(desc ->
            snapshotRepo.findByDescriptorIdAndUnitName(desc.id(), desc.path())
                .map(snap -> Map.entry(desc.path(), snap.id())))
        .collectMap(Map.Entry::getKey, Map.Entry::getValue);

    return Mono.zip(thisDeletionsMono, ownMono)
        .flatMap(tuple -> {
          Set<String> allDeleted = new HashSet<>(deletedPaths);
          allDeleted.addAll(tuple.getT1());
          Map<String, Long> own = tuple.getT2();

          return versionRepo.findByProjectAndVersion(project, version)
              .flatMap(v -> {
                Integer parentVersion = v.parentVersion();
                if (parentVersion == null) {
                  Map<String, Long> result = new LinkedHashMap<>(own);
                  result.keySet().removeAll(allDeleted);
                  return Mono.just(result);
                }
                return collectSnapshotMap(project, parentVersion, allDeleted)
                    .map(parentMap -> {
                      // Own entries always win over inherited ones
                      Map<String, Long> merged = new LinkedHashMap<>(parentMap);
                      merged.putAll(own);
                      merged.keySet().removeAll(allDeleted);
                      return merged;
                    });
              });
        });
  }

  // ── In-memory diff helpers ────────────────────────────────────────────────

  /** Groups flat DB rows into {@code path → (constantValue → [usageDetails])}. */
  private static Map<String, Map<String, List<UsageDetail>>> groupByPathAndValue(
      List<ConstantDiffRow> rows, Map<Long, String> snapshotToPath) {
    Map<String, Map<String, List<UsageDetail>>> result = new LinkedHashMap<>();
    for (ConstantDiffRow row : rows) {
      String path = snapshotToPath.get(row.snapshotId());
      if (path == null) continue; // should never happen
      result
          .computeIfAbsent(path, _ -> new LinkedHashMap<>())
          .computeIfAbsent(row.constantValue(), _ -> new ArrayList<>())
          .add(toUsageDetail(row));
    }
    return result;
  }

  private static UsageDetail toUsageDetail(ConstantDiffRow row) {
    return new UsageDetail(
        row.structuralType(),
        row.semanticTypeKind(),
        row.semanticTypeName(),
        row.semanticDisplayName(),
        row.locationClassName(),
        row.locationMethodName(),
        row.locationLineNumber(),
        row.confidence());
  }

  private static List<ConstantDiffEntry> buildAddedEntries(
      Map<String, List<UsageDetail>> toConstants) {
    return toConstants.entrySet().stream()
        .map(e -> new ConstantDiffEntry(e.getKey(), null, List.of(), e.getValue()))
        .toList();
  }

  private static List<ConstantDiffEntry> buildRemovedEntries(
      Map<String, List<UsageDetail>> fromConstants) {
    return fromConstants.entrySet().stream()
        .map(e -> new ConstantDiffEntry(e.getKey(), null, e.getValue(), List.of()))
        .toList();
  }

  private static List<ConstantDiffEntry> compareConstants(
      Map<String, List<UsageDetail>> fromC, Map<String, List<UsageDetail>> toC) {
    Set<String> allValues = new LinkedHashSet<>();
    allValues.addAll(fromC.keySet());
    allValues.addAll(toC.keySet());

    List<ConstantDiffEntry> entries = new ArrayList<>();
    for (String value : allValues) {
      List<UsageDetail> from = canonicallySorted(fromC.getOrDefault(value, List.of()));
      List<UsageDetail> to   = canonicallySorted(toC.getOrDefault(value, List.of()));
      if (!from.equals(to)) {
        entries.add(new ConstantDiffEntry(value, null, from, to));
      }
    }
    return entries;
  }

  /**
   * Returns a canonically sorted copy of the given usage list so that two logically identical
   * sets of usages compare as equal regardless of the order in which the DB returned them.
   *
   * <p>Sort key: structuralType → locationClassName → locationMethodName →
   * locationLineNumber (nulls last) → confidence (descending).
   */
  static List<UsageDetail> canonicallySorted(List<UsageDetail> usages) {
    Comparator<UsageDetail> cmp = Comparator
        .comparing((UsageDetail u) -> u.structuralType() == null ? "" : u.structuralType())
        .thenComparing(u -> u.locationClassName() == null ? "" : u.locationClassName())
        .thenComparing(u -> u.locationMethodName() == null ? "" : u.locationMethodName())
        .thenComparingInt(u -> u.locationLineNumber() == null ? Integer.MAX_VALUE : u.locationLineNumber())
        .thenComparingDouble(u -> -u.confidence());
    return usages.stream().sorted(cmp).toList();
  }
}
