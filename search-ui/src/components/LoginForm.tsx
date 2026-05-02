import { useState } from 'react'
import { Lock, X, LogIn } from 'lucide-react'

interface LoginModalProps {
  onSuccess: () => void
  onClose: () => void
  signIn: (password: string) => Promise<void>
}

export function LoginModal({ onSuccess, onClose, signIn }: LoginModalProps) {
  const [password, setPassword] = useState('')
  const [error, setError] = useState('')
  const [isLoading, setIsLoading] = useState(false)

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault()
    setError('')
    setIsLoading(true)
    try {
      await signIn(password)
      onSuccess()
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Login failed')
    } finally {
      setIsLoading(false)
    }
  }

  return (
    /* Backdrop */
    <div
      className="fixed inset-0 bg-black/50 backdrop-blur-sm z-50 flex items-center justify-center p-4"
      onClick={onClose}
    >
      {/* Card — stop click propagation so clicking inside doesn't close */}
      <div
        className="bg-card border border-border rounded-xl p-8 w-full max-w-sm shadow-xl relative"
        onClick={e => e.stopPropagation()}
      >
        {/* Close button */}
        <button
          onClick={onClose}
          className="absolute top-4 right-4 text-muted-foreground hover:text-foreground transition-colors"
          aria-label="Close"
        >
          <X className="h-4 w-4" />
        </button>

        {/* Icon + heading */}
        <div className="flex flex-col items-center mb-6">
          <div className="h-10 w-10 rounded-full bg-primary/10 flex items-center justify-center mb-3">
            <Lock className="h-5 w-5 text-primary" />
          </div>
          <h2 className="text-lg font-semibold">Sign in</h2>
          <p className="text-xs text-muted-foreground mt-1">Required for upload &amp; version management</p>
        </div>

        <form onSubmit={handleSubmit} className="space-y-4">
          <div className="space-y-1">
            <label className="text-xs font-medium text-muted-foreground">Password</label>
            <input
              type="password"
              value={password}
              onChange={e => setPassword(e.target.value)}
              placeholder="Enter password"
              autoFocus
              className="w-full px-3 py-2 rounded-lg border border-input bg-secondary/50 text-sm focus:outline-none focus:ring-2 focus:ring-primary/40"
            />
          </div>

          {error && (
            <p className="text-xs text-destructive">{error}</p>
          )}

          <button
            type="submit"
            disabled={!password || isLoading}
            className="w-full h-10 rounded-lg bg-primary text-primary-foreground font-medium text-sm flex items-center justify-center gap-2 disabled:opacity-50 disabled:cursor-not-allowed transition-opacity"
          >
            {isLoading
              ? <span className="h-4 w-4 border-2 border-primary-foreground/30 border-t-primary-foreground rounded-full animate-spin" />
              : <LogIn className="h-4 w-4" />
            }
            Sign in
          </button>
        </form>
      </div>
    </div>
  )
}

