export interface JarJob {
  project: string;
  version: number;
  jarName: string;
  status: 'STARTED' | 'COMPLETED' | 'FAILED';
  startedAt: string;
  lastUpdatedAt: string;
  nestedTotal: number;
  nestedProcessed: number;
  nestedFailed: number;
  errorMessage: string | null;
}

export async function getJarJobs(
  project: string,
  version: number,
  jarName?: string,
): Promise<JarJob[]> {
  const params: Record<string, string> = { project, version: String(version) };
  if (jarName && jarName.trim()) params.jarName = jarName.trim();
  const query = new URLSearchParams(params).toString();
  const res = await fetch(`/jar/jobs?${query}`, { method: 'GET' });
  if (!res.ok) {
    if (res.status === 404) return [];
    throw new Error(`Jar jobs fetch failed (HTTP ${res.status})`);
  }
  return res.json();
}

