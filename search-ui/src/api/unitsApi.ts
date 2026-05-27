export interface UnitEntry {
  name: string;
  constants: number;
}

export interface UnitGroup {
  unitPath: string;
  units: UnitEntry[];
}

export interface UnitsLookupFilters {
  types?: string[];
  semanticTypes?: string[];
  usageTypes?: string[];
}

export interface UnitsLookupOptions {
  fetcher?: typeof fetch;
  filters?: UnitsLookupFilters;
}

export async function getUnits(
  project: string,
  version: number,
  options?: UnitsLookupOptions,
): Promise<UnitGroup[]> {
  const fetcher = options?.fetcher ?? globalThis.fetch
  const query = new URLSearchParams({ project, version: String(version) })
  for (const type of options?.filters?.types ?? []) query.append('type', type)
  for (const semanticType of options?.filters?.semanticTypes ?? []) query.append('semanticType', semanticType)
  for (const usageType of options?.filters?.usageTypes ?? []) query.append('usageType', usageType)
  const res = await fetcher(`/units?${query.toString()}`, { method: 'GET' })
  if (!res.ok) {
    if (res.status === 404) return [];
    throw new Error(`Units lookup failed (HTTP ${res.status})`);
  }
  return res.json();
}

