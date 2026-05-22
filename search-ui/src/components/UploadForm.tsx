import { useRef, useState, type ChangeEvent, type DragEvent, type SyntheticEvent } from 'react'
import { uploadClass, uploadJar, uploadConfig } from '@/api/uploadApi'
import { Upload, FileArchive, FileCode2, FileText, CheckCircle2, AlertCircle } from 'lucide-react'

interface UploadFormProps {
  authFetch?: typeof fetch
  project?: string
  version?: string
  onProjectChange?: (value: string) => void
  onVersionChange?: (value: string) => void
}

export function UploadForm({
  authFetch,
  project: sharedProject,
  version: sharedVersion,
  onProjectChange,
  onVersionChange,
}: UploadFormProps = {}) {
  const MAX_UPLOAD_BYTES = 100 * 1024 * 1024 // Keep in sync with backend/gateway limits.

  const ACCEPTED_EXTENSIONS: Record<'class' | 'jar' | 'config', string[]> = {
    class: ['.class'],
    jar: ['.jar', '.zip'],
    config: ['.yml', '.yaml', '.properties'],
  }

  const [file, setFile] = useState<File | null>(null)
  const [project, setProject] = useState('')
  const [version, setVersion] = useState('')
  const [type, setType] = useState<'class' | 'jar' | 'config'>('class')
  const [status, setStatus] = useState<'idle' | 'loading' | 'success' | 'error'>('idle')
  const [message, setMessage] = useState('')
  const inputRef = useRef<HTMLInputElement>(null)

  const usesSharedScope =
    sharedProject != null && sharedVersion != null &&
    typeof onProjectChange === 'function' &&
    typeof onVersionChange === 'function'

  const projectValue = usesSharedScope ? sharedProject : project
  const versionValue = usesSharedScope ? sharedVersion : version

  function setProjectValue(value: string) {
    if (usesSharedScope) onProjectChange(value)
    else setProject(value)
  }

  function setVersionValue(value: string) {
    if (usesSharedScope) onVersionChange(value)
    else setVersion(value)
  }

  async function handleUpload(e: SyntheticEvent<HTMLFormElement>) {
    e.preventDefault()
    if (!file || !projectValue.trim()) return
    setStatus('loading')
    setMessage('')
    try {
      const fetcher = authFetch
      const parsedVersion = versionValue ? Number(versionValue) : undefined
      const res = type === 'class'
        ? await uploadClass({ file, project: projectValue.trim(), version: parsedVersion, fetcher })
        : type === 'jar'
        ? await uploadJar({ file, project: projectValue.trim(), jarName: file.name, fetcher })
        : await uploadConfig({ file, project: projectValue.trim(), version: parsedVersion, fetcher })
      setStatus(res.status)
      setMessage(res.message)
      if (res.status === 'success') {
        setFile(null)
        // Reset form after 2 seconds to allow another upload
        setTimeout(() => {
          setStatus('idle')
          setMessage('')
        }, 2000)
      }
    } catch (err) {
      setStatus('error')
      setMessage(err instanceof Error ? err.message : 'Upload failed')
    }
  }

  function validateFile(candidate: File): string | null {
    const loweredName = candidate.name.toLowerCase()
    const allowed = ACCEPTED_EXTENSIONS[type]
    const isAllowed = allowed.some(ext => loweredName.endsWith(ext))
    if (!isAllowed) {
      return `Invalid file type for ${type}. Expected: ${allowed.join(', ')}`
    }

    if (candidate.size > MAX_UPLOAD_BYTES) {
      return `File is too large (${Math.ceil(candidate.size / (1024 * 1024))} MB). Maximum allowed is ${Math.floor(MAX_UPLOAD_BYTES / (1024 * 1024))} MB.`
    }

    return null
  }

  function handleFileChange(e: ChangeEvent<HTMLInputElement>) {
    const selected = e.target.files?.[0] || null
    if (!selected) {
      setFile(null)
      return
    }

    const validationError = validateFile(selected)
    if (validationError) {
      setStatus('error')
      setMessage(validationError)
      setFile(null)
      return
    }

    setStatus('idle')
    setMessage('')
    setFile(selected)
  }

  function handleDrop(e: DragEvent<HTMLDivElement>) {
    e.preventDefault()
    if (!e.dataTransfer.files.length) return

    const droppedFile = e.dataTransfer.files[0]
    const validationError = validateFile(droppedFile)
    if (validationError) {
      setStatus('error')
      setMessage(validationError)
      setFile(null)
      return
    }

    setStatus('idle')
    setMessage('')
    setFile(droppedFile)
  }

  return (
    <form onSubmit={handleUpload} className="space-y-6 max-w-md mx-auto">
      {/* File type selector */}
      <div>
        <label className="block text-xs font-medium text-muted-foreground mb-2">File type</label>
        <div className="flex gap-2">
          <button type="button"
            title="Upload a single compiled .class file"
            className={`flex-1 px-3 py-2 rounded-lg border text-sm font-medium transition ${type==='class'?'bg-primary text-primary-foreground':'bg-secondary text-foreground'}`}
            onClick={()=>setType('class')}>
            <FileCode2 className="inline mr-1 w-4 h-4"/> .class file
          </button>
          <button type="button"
            title="Upload a JAR or ZIP archive — every class inside will be indexed"
            className={`flex-1 px-3 py-2 rounded-lg border text-sm font-medium transition ${type==='jar'?'bg-primary text-primary-foreground':'bg-secondary text-foreground'}`}
            onClick={()=>setType('jar')}>
            <FileArchive className="inline mr-1 w-4 h-4"/> JAR/ZIP archive
          </button>
          <button type="button"
            title="Upload a .yml, .yaml, or .properties config file"
            className={`flex-1 px-3 py-2 rounded-lg border text-sm font-medium transition ${type==='config'?'bg-primary text-primary-foreground':'bg-secondary text-foreground'}`}
            onClick={()=>setType('config')}>
            <FileText className="inline mr-1 w-4 h-4"/> Config file
          </button>
        </div>
      </div>

      {/* Drop zone */}
      <div
        className="border-2 border-dashed rounded-xl p-6 text-center cursor-pointer bg-muted/30 hover:bg-muted/50 transition"
        onClick={()=>inputRef.current?.click()}
        onDrop={handleDrop}
        onDragOver={e=>e.preventDefault()}
      >
        <Upload className="mx-auto mb-2 w-7 h-7 text-primary"/>
        <div className="text-sm mb-1">Drag & drop or click to select a {type === 'class' ? '.class' : type === 'jar' ? '.jar / .zip' : '.yml / .yaml / .properties'} file</div>
        <input ref={inputRef} type="file" accept={type==='class'?'.class':type==='jar'?'.jar,.zip':'.yml,.yaml,.properties'} className="hidden" onChange={handleFileChange}/>
        {file && <div className="mt-2 text-xs text-foreground font-medium">{file.name}</div>}
      </div>

      {/* Project + Version fields */}
      {usesSharedScope ? (
        <div className="text-xs text-muted-foreground bg-secondary/40 border border-border rounded-lg px-3 py-2">
          Using workspace scope: <span className="font-medium text-foreground">{projectValue || '(missing project)'}</span>
          {type !== 'jar' && <span> v<span className="font-medium text-foreground">{versionValue || '(auto)'}</span></span>}
        </div>
      ) : (
        <div className="space-y-3">
          <div className="space-y-1">
            <label className="text-xs font-medium text-muted-foreground">
              Project name <span className="text-destructive">*</span>
            </label>
            <input type="text" placeholder="e.g. demo-crud-server" value={projectValue} onChange={e=>setProjectValue(e.target.value)}
              className="w-full px-3 py-2 rounded-lg border border-input bg-secondary/50 text-sm"/>
          </div>
          {type !== 'jar' && (
            <div className="space-y-1">
              <label className="text-xs font-medium text-muted-foreground flex items-center gap-1">
                Version
                <span
                  title="Leave blank to let the server assign the next version number automatically"
                  className="cursor-help text-muted-foreground/60 hover:text-muted-foreground"
                >ⓘ</span>
              </label>
              <input type="number" placeholder="Leave blank to auto-assign" value={versionValue} onChange={e=>setVersionValue(e.target.value)}
                className="w-full px-3 py-2 rounded-lg border border-input bg-secondary/50 text-sm"/>
            </div>
          )}
        </div>
      )}

      <button type="submit" disabled={!file||!projectValue.trim()||status==='loading'} className="w-full h-11 rounded-xl bg-primary text-primary-foreground font-medium text-base flex items-center justify-center gap-2 disabled:opacity-50 disabled:cursor-not-allowed">
        {status==='loading'?<span className="animate-spin h-5 w-5 border-2 border-primary-foreground/30 border-t-primary-foreground rounded-full"/>:<Upload className="h-5 w-5"/>}
        Upload
      </button>
      {status==='success' && <div className="flex items-center gap-2 text-green-600 text-sm"><CheckCircle2 className="w-4 h-4"/>{message}</div>}
      {status==='error' && <div className="flex items-center gap-2 text-destructive text-sm"><AlertCircle className="w-4 h-4"/>{message}</div>}
    </form>
  )
}

