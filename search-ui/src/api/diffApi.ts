// ── Types matching the backend DTOs ──────────────────────────────────────────

export interface UsageDetail {
  structuralType: string
  semanticTypeKind: string
  semanticTypeName: string
  semanticDisplayName: string
  locationClassName: string
  locationMethodName: string
  locationLineNumber: number | null
  confidence: number
}

export type ChangeKind = 'ADDED' | 'REMOVED' | 'CHANGED'

export interface ConstantDiffEntry {
  value: string
  valueType: string
  fromUsages: UsageDetail[]
  toUsages: UsageDetail[]
  changeKind: ChangeKind
}

export interface UnitDiff {
  path: string
  addedUnit: boolean
  removedUnit: boolean
  changedConstants: ConstantDiffEntry[]
}

export interface ProjectDiffResponse {
  project: string
  fromVersion: number
  toVersion: number
  units: UnitDiff[]
}

// ── API client ────────────────────────────────────────────────────────────────

const API_BASE = ''

export async function getDiff(
  project: string,
  from: number,
  to: number
): Promise<ProjectDiffResponse> {
  const res = await fetch(
    `${API_BASE}/project/${encodeURIComponent(project)}/diff?from=${from}&to=${to}`
  )
  if (!res.ok) {
    if (res.status === 400) throw new Error('Invalid diff parameters.')
    throw new Error(`Diff failed (HTTP ${res.status})`)
  }
  return res.json()
}

