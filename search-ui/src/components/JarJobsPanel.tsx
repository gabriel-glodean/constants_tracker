import { useCallback, useEffect, useState } from 'react'
import { RefreshCw, CheckCircle2, XCircle, Loader2, Clock } from 'lucide-react'
import { getJarJobs, type JarJob } from '@/api/jarJobsApi'

interface Props {
  project: string
  version: string
  authFetch: typeof fetch
}

const POLL_MS = 3_000

function StatusBadge({ status }: { status: JarJob['status'] }) {
  if (status === 'COMPLETED')
    return (
      <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-xs font-medium bg-green-500/10 text-green-600 dark:text-green-400">
        <CheckCircle2 className="h-3 w-3" /> COMPLETED
      </span>
    )
  if (status === 'FAILED')
    return (
      <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-xs font-medium bg-destructive/10 text-destructive">
        <XCircle className="h-3 w-3" /> FAILED
      </span>
    )
  return (
    <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-xs font-medium bg-primary/10 text-primary animate-pulse">
      <Loader2 className="h-3 w-3 animate-spin" /> STARTED
    </span>
  )
}

function fmt(iso: string): string {
  try {
    return new Date(iso).toLocaleTimeString(undefined, { hour: '2-digit', minute: '2-digit', second: '2-digit' })
  } catch {
    return iso
  }
}

export function JarJobsPanel({ project, version, authFetch }: Props) {
  const [jobs, setJobs] = useState<JarJob[]>([])
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const versionNum = parseInt(version, 10)
  const ready = project.trim().length > 0 && !isNaN(versionNum) && versionNum > 0

  const poll = useCallback(async (silent = false) => {
    if (!ready) return
    if (!silent) setLoading(true)
    try {
      const data = await getJarJobs(project.trim(), versionNum, undefined, { fetcher: authFetch })
      setJobs(data)
      setError(null)
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Failed to load jobs')
    } finally {
      if (!silent) setLoading(false)
    }
  }, [ready, project, versionNum, authFetch])

  useEffect(() => {
    if (!ready) {
      setJobs([])
      return
    }

    // Initial fetch
    poll()

    // Always keep polling at a fixed interval while this panel is mounted.
    // The previous setTimeout-with-conditional-reschedule stopped permanently
    // when no jobs were in STARTED state, missing new uploads.
    const id = setInterval(() => poll(true), POLL_MS)
    return () => clearInterval(id)
    // Re-run whenever scope or auth state changes.
  }, [poll])

  if (!ready) return null

  return (
    <div className="mt-8 rounded-xl border border-border bg-card/60 overflow-hidden">
      <div className="flex items-center justify-between px-4 py-3 border-b border-border">
        <div className="flex items-center gap-2">
          <Clock className="h-4 w-4 text-muted-foreground" />
          <span className="text-sm font-medium">Extraction Jobs</span>
          {jobs.length > 0 && (
            <span className="text-xs text-muted-foreground">({jobs.length})</span>
          )}
        </div>
        <button
          onClick={() => poll()}
          disabled={loading}
          title="Refresh"
          className="p-1.5 rounded-md hover:bg-secondary text-muted-foreground hover:text-foreground transition-colors disabled:opacity-50"
        >
          <RefreshCw className={`h-3.5 w-3.5 ${loading ? 'animate-spin' : ''}`} />
        </button>
      </div>

      {error && (
        <div className="px-4 py-3 text-xs text-destructive">{error}</div>
      )}

      {!error && jobs.length === 0 && (
        <div className="px-4 py-6 text-center text-xs text-muted-foreground">
          No extraction jobs found.
        </div>
      )}

      {jobs.length > 0 && (
        <div className="overflow-x-auto">
          <table className="w-full text-xs">
            <thead>
              <tr className="border-b border-border text-muted-foreground">
                <th className="text-left px-4 py-2 font-medium">JAR</th>
                <th className="text-left px-4 py-2 font-medium">Status</th>
                <th className="text-right px-4 py-2 font-medium whitespace-nowrap">Processed / Total</th>
                <th className="text-right px-4 py-2 font-medium">Failed</th>
                <th className="text-right px-4 py-2 font-medium whitespace-nowrap">Last updated</th>
              </tr>
            </thead>
            <tbody>
              {jobs.map(job => (
                <tr
                  key={job.jarName}
                  className="border-b border-border/50 last:border-0 hover:bg-secondary/30 transition-colors"
                  title={job.errorMessage ?? undefined}
                >
                  <td className="px-4 py-2 font-mono break-all max-w-[18rem]">{job.jarName}</td>
                  <td className="px-4 py-2"><StatusBadge status={job.status} /></td>
                  <td className="px-4 py-2 text-right tabular-nums">
                    {`${job.nestedProcessed} / ${job.nestedTotal}`}
                  </td>
                  <td className="px-4 py-2 text-right tabular-nums">
                    {job.nestedFailed > 0
                      ? <span className="text-destructive">{job.nestedFailed}</span>
                      : <span className="text-muted-foreground">—</span>}
                  </td>
                  <td className="px-4 py-2 text-right text-muted-foreground whitespace-nowrap">
                    {fmt(job.lastUpdatedAt)}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  )
}

