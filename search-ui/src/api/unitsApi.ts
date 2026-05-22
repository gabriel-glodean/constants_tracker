export interface UnitEntry {
  name: string;
  constants: number;
}

export interface UnitGroup {
  unitPath: string;
  units: UnitEntry[];
}

export async function getUnits(project: string, version: number): Promise<UnitGroup[]> {
  const query = new URLSearchParams({ project, version: String(version) }).toString();
  const res = await fetch(`/units?${query}`, { method: 'GET' });
  if (!res.ok) {
    if (res.status === 404) return [];
    throw new Error(`Units lookup failed (HTTP ${res.status})`);
  }
  return res.json();
}

