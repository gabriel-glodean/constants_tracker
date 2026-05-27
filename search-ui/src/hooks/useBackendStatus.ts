import { useEffect, useState } from 'react'
import { getAuthStatus } from '@/api/authApi'

interface UseBackendStatusOptions {
  /** How often (in ms) to poll the backend. Defaults to 15 000 ms. */
  pollMs?: number
}

/**
 * Actively monitors backend availability by periodically probing /auth/status.
 * Also reacts to the browser's native online/offline events so the banner
 * appears immediately when the network drops.
 */
export function useBackendStatus(options?: UseBackendStatusOptions) {
  const pollMs = options?.pollMs ?? 15_000
  const [backendAvailable, setBackendAvailable] = useState(true)

  useEffect(() => {
    let cancelled = false

    const probe = async () => {
      try {
        await getAuthStatus()
        if (!cancelled) setBackendAvailable(true)
      } catch {
        if (!cancelled) setBackendAvailable(false)
      }
    }

    const handleOffline = () => {
      if (!cancelled) setBackendAvailable(false)
    }

    const handleOnline = () => {
      void probe()
    }

    // Probe immediately on mount.
    void probe()

    const intervalId = window.setInterval(() => {
      void probe()
    }, pollMs)

    window.addEventListener('offline', handleOffline)
    window.addEventListener('online', handleOnline)

    return () => {
      cancelled = true
      window.clearInterval(intervalId)
      window.removeEventListener('offline', handleOffline)
      window.removeEventListener('online', handleOnline)
    }
  }, [pollMs])

  return { backendAvailable }
}

