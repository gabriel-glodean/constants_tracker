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
import org.glodean.constants.store.postgres.entity.ProjectVersionEntity;
import org.glodean.constants.store.postgres.entity.VersionDeletionEntity;
import org.glodean.constants.store.postgres.repository.ProjectVersionRepository;
import org.glodean.constants.store.postgres.repository.UnitDescriptorRepository;
import org.glodean.constants.store.postgres.repository.UnitSnapshotRepository;
import org.glodean.constants.store.postgres.repository.VersionDeletionRepository;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import static org.glodean.constants.util.LogSanitizer.sanitize;

/**
 * Computes a constant-level diff between two project versions, fully respecting the inheritance
 * chain and deletion records.
 *
 * <h2>Algorithm</h2>
 * <ol>
 *   <li>Resolve the <em>effective</em> {@code Map<path, snapshotId>} for each version by walking
 *       the parent chain and honouring {@code version_deletions}.</li>
 *   <li>Categorize paths into purely added, purely removed, and changed sets.
 *       Paths whose {@code snapshotId} is identical in both versions are skipped entirely.</li>
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
      ProjectVersionRepository versionRepo,
      UnitDescriptorRepository descriptorRepo,
      UnitSnapshotRepository snapshotRepo,
      VersionDeletionRepository deletionRepo,
      DiffRepository diffRepo) {
    this.versionRepo = versionRepo;
    this.descriptorRepo = descriptorRepo;
    this.snapshotRepo = snapshotRepo;
    this.deletionRepo = deletionRepo;
    this.diffRepo = diffRepo;
  }

  // ── Public API ────────────────────────────────────────────────────────────

  /**
   * Computes the full constant-level diff between {@code fromVersion} and {@code toVersion}
   * for the given project.
   *
   * @throws IllegalArgumentException if either version does not exist for the project
   */
  public Mono<ProjectDiffResponse> diff(String project, int fromVersion, int toVersion) {
    Mono<Map<String, Long>> fromSnapshotsMono = resolveEffectiveSnapshots(project, fromVersion);
    Mono<Map<String, Long>> toSnapshotsMono   = resolveEffectiveSnapshots(project, toVersion);

    return Mono.zip(fromSnapshotsMono, toSnapshotsMono)
        .flatMap(tuple -> {
          Map<String, Long> fromMap = tuple.getT1();
          Map<String, Long> toMap   = tuple.getT2();

          PathCategories cats = categorize(fromMap, toMap);
          logCategorySummary(project, fromVersion, toVersion, cats);

          Map<Long, String> snapshotToPath = invertedIndex(fromMap, toMap);

          Mono<Map<String, Map<String, List<UsageDetail>>>> fromConstsMono =
              loadConstantsForSnapshots(cats.fromSnapshotIds(), snapshotToPath);
          Mono<Map<String, Map<String, List<UsageDetail>>>> toConstsMono =
              loadConstantsForSnapshots(cats.toSnapshotIds(), snapshotToPath);

          return Mono.zip(fromConstsMono, toConstsMono)
              .map(constants -> {
                List<UnitDiff> units = buildUnitDiffs(
                    cats, constants.getT1(), constants.getT2());
                return new ProjectDiffResponse(project, fromVersion, toVersion, units);
              });
        });
  }

  // ── Effective snapshot resolution ─────────────────────────────────────────

  /**
   * Produces the effective {@code path → snapshotId} map for a version by walking the
   * parent chain and excluding any paths present in {@code version_deletions}.
   */
  private Mono<Map<String, Long>> resolveEffectiveSnapshots(String project, int version) {
    return versionRepo.findByProjectAndVersion(project, version)
        .switchIfEmpty(Mono.error(new IllegalArgumentException(
            "Version " + version + " does not exist for project " + project)))
        .flatMap(entity -> collectSnapshotMap(entity, new HashSet<>()));
  }

  /**
   * Recursively collects the effective {@code path → snapshotId} map for one version level.
   * Accepts an already-loaded {@code versionEntity} to avoid a redundant DB round-trip on
   * the first call and on each recursive step.
   *
   * @param deletedPaths paths already marked as deleted by a child version — must not be
   *                     re-introduced by ancestor versions
   */
  private Mono<Map<String, Long>> collectSnapshotMap(
      ProjectVersionEntity versionEntity, Set<String> deletedPaths) {

    String project = versionEntity.project();
    int    version = versionEntity.version();

    Mono<Set<String>> ownDeletionsMono = deletionRepo
        .findAllByProjectAndVersion(project, version)
        .map(VersionDeletionEntity::unitPath)
        .collect(Collectors.toSet());

    Mono<Map<String, Long>> ownSnapshotsMono = descriptorRepo
        .findAllByProjectAndVersion(project, version)
        .flatMap(desc ->
            snapshotRepo.findByDescriptorIdAndUnitName(desc.id(), desc.path())
                .map(snap -> Map.entry(desc.path(), snap.id())))
        .collectMap(Map.Entry::getKey, Map.Entry::getValue);

    return Mono.zip(ownDeletionsMono, ownSnapshotsMono)
        .flatMap(tuple -> {
          Set<String>    allDeleted = mergedDeletions(deletedPaths, tuple.getT1());
          Map<String, Long> own     = tuple.getT2();

          Integer parentVersion = versionEntity.parentVersion();
          if (parentVersion == null) {
            return Mono.just(applyDeletions(own, allDeleted));
          }

          return versionRepo.findByProjectAndVersion(project, parentVersion)
              .flatMap(parentEntity -> collectSnapshotMap(parentEntity, allDeleted))
              .map(parentMap -> {
                // Own entries always win over inherited ones
                Map<String, Long> merged = new LinkedHashMap<>(parentMap);
                merged.putAll(own);
                merged.keySet().removeAll(allDeleted);
                return merged;
              });
        });
  }

  // ── Path categorization ───────────────────────────────────────────────────

  /**
   * Holds the result of categorizing paths across two effective snapshot maps.
   *
   * @param purelyAdded    paths that exist only in the {@code to} version
   * @param purelyRemoved  paths that exist only in the {@code from} version
   * @param changedPaths   paths present in both versions but with different snapshot IDs
   * @param fromSnapshotIds snapshot IDs to load from the {@code from} side (removed + changed)
   * @param toSnapshotIds   snapshot IDs to load from the {@code to} side (added + changed)
   */
  private record PathCategories(
      Set<String> purelyAdded,
      Set<String> purelyRemoved,
      Set<String> changedPaths,
      Set<Long>   fromSnapshotIds,
      Set<Long>   toSnapshotIds) {}

  private static PathCategories categorize(Map<String, Long> fromMap, Map<String, Long> toMap) {
    Set<String> allPaths = new LinkedHashSet<>();
    allPaths.addAll(fromMap.keySet());
    allPaths.addAll(toMap.keySet());

    Set<String> purelyAdded    = new HashSet<>();
    Set<String> purelyRemoved  = new HashSet<>();
    Set<String> changedPaths   = new HashSet<>();
    Set<Long>   fromSnapshotIds = new HashSet<>();
    Set<Long>   toSnapshotIds   = new HashSet<>();

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
        changedPaths.add(path);
        fromSnapshotIds.add(fromId);
        toSnapshotIds.add(toId);
      }
      // identical snapshotId → unchanged, skip entirely
    }

    return new PathCategories(purelyAdded, purelyRemoved, changedPaths, fromSnapshotIds, toSnapshotIds);
  }

  // ── Unit diff assembly ────────────────────────────────────────────────────

  private static List<UnitDiff> buildUnitDiffs(
      PathCategories cats,
      Map<String, Map<String, List<UsageDetail>>> fromConsts,
      Map<String, Map<String, List<UsageDetail>>> toConsts) {

    List<UnitDiff> units = new ArrayList<>();

    for (String path : cats.purelyRemoved()) {
      units.add(new UnitDiff(path, false, true,
          buildRemovedEntries(fromConsts.getOrDefault(path, Map.of()))));
    }

    for (String path : cats.purelyAdded()) {
      units.add(new UnitDiff(path, true, false,
          buildAddedEntries(toConsts.getOrDefault(path, Map.of()))));
    }

    for (String path : cats.changedPaths()) {
      List<ConstantDiffEntry> entries = compareConstants(
          fromConsts.getOrDefault(path, Map.of()),
          toConsts.getOrDefault(path, Map.of()));
      if (!entries.isEmpty()) {
        units.add(new UnitDiff(path, false, false, entries));
      }
    }

    return units;
  }

  // ── Reactive data loading ─────────────────────────────────────────────────

  private Mono<Map<String, Map<String, List<UsageDetail>>>> loadConstantsForSnapshots(
      Set<Long> snapshotIds, Map<Long, String> snapshotToPath) {
    return diffRepo.loadForSnapshots(snapshotIds)
        .collectList()
        .map(rows -> groupByPathAndValue(rows, snapshotToPath));
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
          .computeIfAbsent(path, ignored -> new LinkedHashMap<>())
          .computeIfAbsent(row.constantValue(), ignored -> new ArrayList<>())
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
      List<UsageDetail> fromUsages = canonicallySorted(fromC.getOrDefault(value, List.of()));
      List<UsageDetail> toUsages   = canonicallySorted(toC.getOrDefault(value, List.of()));
      if (!fromUsages.equals(toUsages)) {
        entries.add(new ConstantDiffEntry(value, null, fromUsages, toUsages));
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
        .comparing((UsageDetail u) -> u.structuralType()    == null ? "" : u.structuralType())
        .thenComparing(u           -> u.locationClassName()  == null ? "" : u.locationClassName())
        .thenComparing(u           -> u.locationMethodName() == null ? "" : u.locationMethodName())
        .thenComparingInt(u        -> u.locationLineNumber() == null ? Integer.MAX_VALUE : u.locationLineNumber())
        .thenComparingDouble(u     -> -u.confidence());
    return usages.stream().sorted(cmp).toList();
  }

  // ── Utility methods ───────────────────────────────────────────────────────

  /** Builds a reverse {@code snapshotId → path} lookup covering both sides of the diff. */
  private static Map<Long, String> invertedIndex(Map<String, Long> fromMap, Map<String, Long> toMap) {
    Map<Long, String> index = new HashMap<>();
    fromMap.forEach((path, sid) -> index.put(sid, path));
    toMap.forEach((path, sid)   -> index.put(sid, path));
    return index;
  }

  private static Set<String> mergedDeletions(Set<String> inherited, Set<String> ownDeletions) {
    Set<String> merged = new HashSet<>(inherited);
    merged.addAll(ownDeletions);
    return merged;
  }

  private static Map<String, Long> applyDeletions(Map<String, Long> snapshots, Set<String> deletions) {
    Map<String, Long> result = new LinkedHashMap<>(snapshots);
    result.keySet().removeAll(deletions);
    return result;
  }

  private void logCategorySummary(String project, int fromVersion, int toVersion, PathCategories cats) {
    logger.atInfo().log(
        "Diff {}: from={} to={} | added={} removed={} changed={}",
        sanitize(project), fromVersion, toVersion,
        cats.purelyAdded().size(), cats.purelyRemoved().size(), cats.changedPaths().size());
  }
}
