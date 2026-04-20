// ── Types matching the backend DTOs ─────────────────────────────────────

export interface ProjectVersion {
  id: number
  project: string
  version: number
  parentVersion: number | null
  status: 'OPEN' | 'FINALIZED'
  createdAt: string
  finalizedAt: string | null
}

// ── API client ──────────────────────────────────────────────────────────

const API_BASE = ''

export async function getVersion(
  project: string,
  version: number
): Promise<ProjectVersion> {
  const res = await fetch(
    `${API_BASE}/project/${encodeURIComponent(project)}/version/${version}`
  )
  if (!res.ok) {
    if (res.status === 404) throw new Error('Version not found.')
    throw new Error(`Failed to get version (HTTP ${res.status})`)
  }
  return res.json()
}

export async function finalizeVersion(
  project: string,
  version: number
): Promise<ProjectVersion> {
  const res = await fetch(
    `${API_BASE}/project/${encodeURIComponent(project)}/version/${version}/finalize`,
    { method: 'POST' }
  )
  if (!res.ok) {
    if (res.status === 404) throw new Error('Version not found.')
    if (res.status === 409) throw new Error('Version is already finalized.')
    throw new Error(`Finalize failed (HTTP ${res.status})`)
  }
  return res.json()
}

export async function syncRemovals(
  project: string,
  version: number
): Promise<string[]> {
  const res = await fetch(
    `${API_BASE}/project/${encodeURIComponent(project)}/version/${version}/sync`,
    { method: 'POST' }
  )
  if (!res.ok) {
    if (res.status === 409) throw new Error('Version is finalized; cannot sync.')
    throw new Error(`Sync failed (HTTP ${res.status})`)
  }
  return res.json()
}

export async function deleteUnit(
  project: string,
  version: number,
  className: string
): Promise<void> {
  const qs = new URLSearchParams({
    project,
    version: String(version),
    className,
  })
  const res = await fetch(`${API_BASE}/class?${qs.toString()}`, {
    method: 'DELETE',
  })
  if (!res.ok) {
    if (res.status === 409) throw new Error('Version is finalized; cannot delete.')
    throw new Error(`Delete failed (HTTP ${res.status})`)
  }
}

