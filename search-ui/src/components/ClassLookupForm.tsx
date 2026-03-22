import { useState } from 'react'
import { getClassConstants } from '@/api/classApi'
import { Search, AlertCircle } from 'lucide-react'

export function ClassLookupForm() {
  const [project, setProject] = useState('')
  const [className, setClassName] = useState('')
  const [version, setVersion] = useState('')
  const [result, setResult] = useState<{[k:string]: string[]} | null>(null)
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(false)

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault()
    setError('')
    setResult(null)
    if (!project.trim() || !className.trim() || !version) {
      setError('All fields are required.')
      return
    }
    setLoading(true)
    try {
      const data = await getClassConstants({
        project: project.trim(),
        className: className.trim(),
        version: Number(version)
      })
      setResult(data.constants)
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Lookup failed')
    } finally {
      setLoading(false)
    }
  }

  return (
    <form onSubmit={handleSubmit} className="space-y-6 max-w-lg mx-auto">
      <div className="flex gap-2">
        <input type="text" placeholder="Project" value={project} onChange={e=>setProject(e.target.value)} className="flex-1 px-3 py-2 rounded-lg border border-input bg-secondary/50 text-sm"/>
        <input type="text" placeholder="Class name (e.g. java/lang/String)" value={className} onChange={e=>setClassName(e.target.value)} className="flex-1 px-3 py-2 rounded-lg border border-input bg-secondary/50 text-sm"/>
        <input type="number" placeholder="Version" value={version} onChange={e=>setVersion(e.target.value)} className="w-28 px-3 py-2 rounded-lg border border-input bg-secondary/50 text-sm"/>
      </div>
      <button type="submit" disabled={loading} className="w-full h-11 rounded-xl bg-primary text-primary-foreground font-medium text-base flex items-center justify-center gap-2 disabled:opacity-50 disabled:cursor-not-allowed">
        {loading ? <span className="animate-spin h-5 w-5 border-2 border-primary-foreground/30 border-t-primary-foreground rounded-full"/> : <Search className="h-5 w-5"/>}
        Lookup
      </button>
      {error && <div className="flex items-center gap-2 text-destructive text-sm"><AlertCircle className="w-4 h-4"/>{error}</div>}
      {result && (
        <div className="mt-6 border border-border rounded-xl bg-card/50 p-4">
          <h3 className="font-semibold mb-2 text-base">Constants</h3>
          <ul className="space-y-2">
            {Object.entries(result).map(([constant, usages]) => (
              <li key={constant} className="flex flex-col gap-1">
                <span className="font-mono text-xs bg-muted/40 rounded px-2 py-1 text-foreground break-all">{constant}</span>
                <span className="text-xs text-muted-foreground">{usages.join(', ')}</span>
              </li>
            ))}
          </ul>
        </div>
      )}
    </form>
  )
}

