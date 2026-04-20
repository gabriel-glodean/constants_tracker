import { useRef, useState } from 'react'
import { uploadClass, uploadJar, uploadConfig } from '@/api/uploadApi'
import { Upload, FileArchive, FileCode2, FileText, CheckCircle2, AlertCircle } from 'lucide-react'

export function UploadForm() {
  const [file, setFile] = useState<File | null>(null)
  const [project, setProject] = useState('')
  const [version, setVersion] = useState('')
  const [type, setType] = useState<'class' | 'jar' | 'config'>('class')
  const [status, setStatus] = useState<'idle' | 'loading' | 'success' | 'error'>('idle')
  const [message, setMessage] = useState('')
  const inputRef = useRef<HTMLInputElement>(null)

  async function handleUpload(e: React.FormEvent) {
    e.preventDefault()
    if (!file || !project.trim()) return
    setStatus('loading')
    setMessage('')
    try {
      const res = type === 'class'
        ? await uploadClass({ file, project: project.trim(), version: version ? Number(version) : undefined })
        : type === 'jar'
        ? await uploadJar({ file, project: project.trim(), jarName: file.name })
        : await uploadConfig({ file, project: project.trim(), version: version ? Number(version) : undefined })
      setStatus(res.status)
      setMessage(res.message)
      if (res.status === 'success') setFile(null)
    } catch (err) {
      setStatus('error')
      setMessage(err instanceof Error ? err.message : 'Upload failed')
    }
  }

  function handleFileChange(e: React.ChangeEvent<HTMLInputElement>) {
    setFile(e.target.files?.[0] || null)
  }

  function handleDrop(e: React.DragEvent) {
    e.preventDefault()
    if (e.dataTransfer.files.length) setFile(e.dataTransfer.files[0])
  }

  return (
    <form onSubmit={handleUpload} className="space-y-6 max-w-md mx-auto">
      <div className="flex gap-2 mb-2">
        <button type="button" className={`flex-1 px-3 py-2 rounded-lg border ${type==='class'?'bg-primary text-primary-foreground':'bg-secondary text-foreground'} transition`} onClick={()=>setType('class')}>
          <FileCode2 className="inline mr-1 w-4 h-4"/> Class
        </button>
        <button type="button" className={`flex-1 px-3 py-2 rounded-lg border ${type==='jar'?'bg-primary text-primary-foreground':'bg-secondary text-foreground'} transition`} onClick={()=>setType('jar')}>
          <FileArchive className="inline mr-1 w-4 h-4"/> JAR
        </button>
        <button type="button" className={`flex-1 px-3 py-2 rounded-lg border ${type==='config'?'bg-primary text-primary-foreground':'bg-secondary text-foreground'} transition`} onClick={()=>setType('config')}>
          <FileText className="inline mr-1 w-4 h-4"/> Config
        </button>
      </div>
      <div
        className="border-2 border-dashed rounded-xl p-6 text-center cursor-pointer bg-muted/30 hover:bg-muted/50 transition"
        onClick={()=>inputRef.current?.click()}
        onDrop={handleDrop}
        onDragOver={e=>e.preventDefault()}
      >
        <Upload className="mx-auto mb-2 w-7 h-7 text-primary"/>
        <div className="text-sm mb-1">Drag & drop or click to select a {type === 'class' ? '.class' : type === 'jar' ? '.jar' : '.yml / .yaml / .properties'} file</div>
        <input ref={inputRef} type="file" accept={type==='class'?'.class':type==='jar'?'.jar':'.yml,.yaml,.properties'} className="hidden" onChange={handleFileChange}/>
        {file && <div className="mt-2 text-xs text-foreground">{file.name}</div>}
      </div>
      <div className="flex gap-2">
        <input type="text" placeholder="Project (required)" value={project} onChange={e=>setProject(e.target.value)} className="flex-1 px-3 py-2 rounded-lg border border-input bg-secondary/50 text-sm"/>
        {type!=='jar' && (
          <input type="number" placeholder="Version (optional)" value={version} onChange={e=>setVersion(e.target.value)} className="w-28 px-3 py-2 rounded-lg border border-input bg-secondary/50 text-sm"/>
        )}
      </div>
      <button type="submit" disabled={!file||!project.trim()||status==='loading'} className="w-full h-11 rounded-xl bg-primary text-primary-foreground font-medium text-base flex items-center justify-center gap-2 disabled:opacity-50 disabled:cursor-not-allowed">
        {status==='loading'?<span className="animate-spin h-5 w-5 border-2 border-primary-foreground/30 border-t-primary-foreground rounded-full"/>:<Upload className="h-5 w-5"/>}
        Upload
      </button>
      {status==='success' && <div className="flex items-center gap-2 text-green-600 text-sm"><CheckCircle2 className="w-4 h-4"/>{message}</div>}
      {status==='error' && <div className="flex items-center gap-2 text-destructive text-sm"><AlertCircle className="w-4 h-4"/>{message}</div>}
    </form>
  )
}

