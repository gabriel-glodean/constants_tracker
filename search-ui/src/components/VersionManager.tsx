import { useState } from 'react'
import {
  getVersion,
  finalizeVersion,
  syncRemovals,
  deleteUnit,
  type ProjectVersion,
} from '@/api/versionApi'
import {
  Search,
  AlertCircle,
  CheckCircle2,
  Lock,
  RefreshCw,
  Trash2,
  GitBranch,
  Info,
} from 'lucide-react'

interface VersionManagerProps {
  authFetch?: typeof fetch
}

export function VersionManager({ authFetch }: VersionManagerProps = {}) {
  // ── lookup state ──
  const [project, setProject] = useState('')
  const [versionNum, setVersionNum] = useState('')
  const [versionData, setVersionData] = useState<ProjectVersion | null>(null)

  // ── delete state ──
  const [deleteClass, setDeleteClass] = useState('')

  // ── sync result ──
  const [syncResult, setSyncResult] = useState<string[] | null>(null)

  // ── shared ──
  const [loading, setLoading] = useState(false)
  const [status, setStatus] = useState<'idle' | 'success' | 'error'>('idle')
  const [message, setMessage] = useState('')

  function clearStatus() {
    setStatus('idle')
    setMessage('')
    setSyncResult(null)
  }

  async function handleLookup(e: React.FormEvent) {
    e.preventDefault()
    if (!project.trim() || !versionNum) return
    clearStatus()
    setVersionData(null)
    setLoading(true)
    try {
      const data = await getVersion(project.trim(), Number(versionNum), { fetcher: authFetch })
      setVersionData(data)
    } catch (err) {
      setStatus('error')
      setMessage(err instanceof Error ? err.message : 'Lookup failed')
    } finally {
      setLoading(false)
    }
  }

  async function handleFinalize() {
    if (!versionData) return
    if (!window.confirm(
      `Close version ${versionData.version} of "${versionData.project}"?\n\nThis will finalize the version, preventing further uploads or deletions.`
    )) return
    clearStatus()
    setLoading(true)
    try {
      const updated = await finalizeVersion(versionData.project, versionData.version, { fetcher: authFetch })
      setVersionData(updated)
      setStatus('success')
      setMessage(`Version ${updated.version} closed successfully.`)
    } catch (err) {
      setStatus('error')
      setMessage(err instanceof Error ? err.message : 'Finalize failed')
    } finally {
      setLoading(false)
    }
  }

  async function handleSync() {
    if (!versionData) return
    clearStatus()
    setLoading(true)
    try {
      const removed = await syncRemovals(versionData.project, versionData.version, { fetcher: authFetch })
      setSyncResult(removed)
      setStatus('success')
      setMessage(
        removed.length === 0
          ? 'No removals detected — all parent units are present.'
          : `Detected ${removed.length} removed unit(s).`
      )
    } catch (err) {
      setStatus('error')
      setMessage(err instanceof Error ? err.message : 'Sync failed')
    } finally {
      setLoading(false)
    }
  }

  async function handleDelete(e: React.FormEvent) {
    e.preventDefault()
    if (!versionData || !deleteClass.trim()) return
    clearStatus()
    setLoading(true)
    try {
      await deleteUnit(versionData.project, versionData.version, deleteClass.trim(), { fetcher: authFetch })
      setStatus('success')
      setMessage(`Unit "${deleteClass.trim()}" deleted from version ${versionData.version}.`)
      setDeleteClass('')
    } catch (err) {
      setStatus('error')
      setMessage(err instanceof Error ? err.message : 'Delete failed')
    } finally {
      setLoading(false)
    }
  }

  const isOpen = versionData?.status === 'OPEN'

  return (
    <div className="space-y-8 max-w-lg mx-auto">
      {/* ── Version Lookup ── */}
      <form onSubmit={handleLookup} className="space-y-4">
        <div className="grid grid-cols-[1fr_auto_auto] gap-2 items-end">
          <div className="space-y-1">
            <label className="text-xs font-medium text-muted-foreground">Project</label>
            <input
              type="text"
              placeholder="e.g. demo-crud-server"
              value={project}
              onChange={e => setProject(e.target.value)}
              className="w-full px-3 py-2 rounded-lg border border-input bg-secondary/50 text-sm"
            />
          </div>
          <div className="space-y-1">
            <label className="text-xs font-medium text-muted-foreground">Version</label>
            <input
              type="number"
              placeholder="e.g. 1"
              value={versionNum}
              onChange={e => setVersionNum(e.target.value)}
              className="w-28 px-3 py-2 rounded-lg border border-input bg-secondary/50 text-sm"
            />
          </div>
          <button
            type="submit"
            disabled={loading || !project.trim() || !versionNum}
            className="px-4 py-2 rounded-lg bg-primary text-primary-foreground text-sm font-medium disabled:opacity-50 disabled:cursor-not-allowed flex items-center gap-1.5"
          >
            <Search className="h-4 w-4" /> Lookup
          </button>
        </div>
      </form>

      {/* ── Version Details Card ── */}
      {versionData && (
        <div className="border border-border rounded-xl bg-card/50 p-5 space-y-4">
          <div className="flex items-center justify-between">
            <h3 className="font-semibold text-base flex items-center gap-2">
              <GitBranch className="h-4 w-4 text-primary" />
              {versionData.project} v{versionData.version}
            </h3>
            <span
              className={`px-2.5 py-0.5 rounded-full text-xs font-medium ${
                isOpen
                  ? 'bg-green-100 text-green-700 dark:bg-green-900/30 dark:text-green-400'
                  : 'bg-amber-100 text-amber-700 dark:bg-amber-900/30 dark:text-amber-400'
              }`}
            >
              {versionData.status}
            </span>
          </div>

          <div className="grid grid-cols-2 gap-3 text-sm">
            <div>
              <span className="text-muted-foreground">Parent version</span>
              <p className="font-mono">
                {versionData.parentVersion != null ? `v${versionData.parentVersion}` : '—'}
              </p>
            </div>
            <div>
              <span className="text-muted-foreground">Created</span>
              <p className="font-mono text-xs">
                {new Date(versionData.createdAt).toLocaleString()}
              </p>
            </div>
            {versionData.finalizedAt && (
              <div className="col-span-2">
                <span className="text-muted-foreground">Finalized</span>
                <p className="font-mono text-xs">
                  {new Date(versionData.finalizedAt).toLocaleString()}
                </p>
              </div>
            )}
          </div>

          {/* ── Info about inheritance ── */}
          {versionData.parentVersion != null && (
            <div className="flex items-start gap-2 p-3 rounded-lg bg-blue-50 dark:bg-blue-900/10 text-sm">
              <Info className="h-4 w-4 text-blue-500 mt-0.5 shrink-0" />
              <p className="text-muted-foreground">
                This version inherits units from v{versionData.parentVersion}.
                Units are carried forward unless they were re-uploaded or explicitly removed.
              </p>
            </div>
          )}

          {/* ── Actions (only for OPEN versions) ── */}
          {isOpen && (
            <div className="space-y-3 pt-2 border-t border-border">
              <h4 className="text-sm font-medium text-muted-foreground">Actions</h4>

              <div className="flex gap-2">
                <button
                  onClick={handleFinalize}
                  disabled={loading}
                  title="Closes this version permanently — no further uploads or deletions will be accepted"
                  className="flex-1 h-10 rounded-lg bg-amber-500 hover:bg-amber-600 text-white text-sm font-medium flex items-center justify-center gap-1.5 disabled:opacity-50 transition"
                >
                  <Lock className="h-4 w-4" /> Close Version
                </button>
                <button
                  onClick={handleSync}
                  disabled={loading}
                  title="Detects units present in the parent version but not re-uploaded here, and marks them as removed"
                  className="flex-1 h-10 rounded-lg bg-blue-500 hover:bg-blue-600 text-white text-sm font-medium flex items-center justify-center gap-1.5 disabled:opacity-50 transition"
                >
                  <RefreshCw className="h-4 w-4" /> Sync Removals
                </button>
              </div>

              {/* ── Delete Unit ── */}
              <div className="space-y-1">
                <label className="text-xs font-medium text-muted-foreground flex items-center gap-1">
                  Delete unit
                  <span
                    title="Marks a class/unit as deleted in this version. Use the JVM internal path format (slashes, no .class suffix)."
                    className="cursor-help text-muted-foreground/60 hover:text-muted-foreground"
                  >ⓘ</span>
                </label>
                <form onSubmit={handleDelete} className="flex gap-2">
                  <input
                    type="text"
                    placeholder="com/example/Foo"
                    value={deleteClass}
                    onChange={e => setDeleteClass(e.target.value)}
                    className="flex-1 px-3 py-2 rounded-lg border border-input bg-secondary/50 text-sm"
                  />
                  <button
                    type="submit"
                    disabled={loading || !deleteClass.trim()}
                    className="px-4 py-2 rounded-lg bg-destructive hover:bg-destructive/90 text-destructive-foreground text-sm font-medium flex items-center gap-1.5 disabled:opacity-50 transition"
                  >
                    <Trash2 className="h-4 w-4" /> Delete
                  </button>
                </form>
              </div>
            </div>
          )}
        </div>
      )}

      {/* ── Sync Results ── */}
      {syncResult && syncResult.length > 0 && (
        <div className="border border-border rounded-xl bg-card/50 p-4">
          <h4 className="font-semibold text-sm mb-2">Removed Units</h4>
          <ul className="space-y-1">
            {syncResult.map(path => (
              <li
                key={path}
                className="font-mono text-xs bg-destructive/10 text-destructive rounded px-2 py-1"
              >
                {path}
              </li>
            ))}
          </ul>
        </div>
      )}

      {/* ── Status messages ── */}
      {status === 'success' && (
        <div className="flex items-center gap-2 text-green-600 text-sm">
          <CheckCircle2 className="w-4 h-4" />
          {message}
        </div>
      )}
      {status === 'error' && (
        <div className="flex items-center gap-2 text-destructive text-sm">
          <AlertCircle className="w-4 h-4" />
          {message}
        </div>
      )}
    </div>
  )
}

