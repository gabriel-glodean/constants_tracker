interface ProjectVersionPaneProps {
  project: string;
  version: string;
  onProjectChange: (value: string) => void;
  onVersionChange: (value: string) => void;
  versionRequired?: boolean;
}

export function ProjectVersionPane({
  project,
  version,
  onProjectChange,
  onVersionChange,
  versionRequired = true,
}: ProjectVersionPaneProps) {
  return (
    <div className="mb-6 rounded-xl border border-border bg-card/60 p-4">
      <p className="text-xs text-muted-foreground mb-3">Workspace scope</p>
      <div className="grid grid-cols-[1fr_8.5rem] gap-3 items-end">
        <div className="space-y-1">
          <label className="text-xs font-medium text-muted-foreground">
            Project <span className="text-destructive">*</span>
          </label>
          <input
            type="text"
            placeholder="e.g. demo-crud-server"
            value={project}
            onChange={e => onProjectChange(e.target.value)}
            className="w-full px-3 py-2 rounded-lg border border-input bg-secondary/50 text-sm"
          />
        </div>
        <div className="space-y-1">
          <label className="text-xs font-medium text-muted-foreground">
            Version {versionRequired ? <span className="text-destructive">*</span> : null}
          </label>
          <input
            type="number"
            placeholder={versionRequired ? 'e.g. 1' : 'optional'}
            value={version}
            onChange={e => onVersionChange(e.target.value)}
            className="w-full px-3 py-2 rounded-lg border border-input bg-secondary/50 text-sm"
          />
        </div>
      </div>
    </div>
  )
}

