import { renderHook, act, waitFor } from '@testing-library/react'
import { useBackendStatus } from './useBackendStatus'
import * as authApi from '../api/authApi'

jest.mock('@/api/authApi')

const mockedGetAuthStatus = authApi.getAuthStatus as jest.MockedFunction<typeof authApi.getAuthStatus>

beforeEach(() => {
  jest.clearAllMocks()
  mockedGetAuthStatus.mockResolvedValue({ enabled: true })
})

describe('useBackendStatus', () => {
  // ── Initial probe ───────────────────────────────────────────────────────────

  describe('initial probe', () => {
    it('defaults to backendAvailable=true before the probe completes', () => {
      // probe is async, so synchronously at mount the state should still be true
      mockedGetAuthStatus.mockReturnValue(new Promise(() => { /* never resolves */ }))
      const { result } = renderHook(() => useBackendStatus())
      expect(result.current.backendAvailable).toBe(true)
    })

    it('stays true when the probe succeeds', async () => {
      mockedGetAuthStatus.mockResolvedValue({ enabled: true })
      const { result } = renderHook(() => useBackendStatus())
      await waitFor(() => expect(mockedGetAuthStatus).toHaveBeenCalledTimes(1))
      expect(result.current.backendAvailable).toBe(true)
    })

    it('flips to false when the probe throws (backend unreachable)', async () => {
      mockedGetAuthStatus.mockRejectedValue(new TypeError('Failed to fetch'))
      const { result } = renderHook(() => useBackendStatus())
      await waitFor(() => expect(result.current.backendAvailable).toBe(false))
    })

    it('does not update state after unmount (no act warning)', async () => {
      const { unmount } = renderHook(() => useBackendStatus())
      unmount()
      // Resolve the pending probe after unmount — should not cause a React warning.
      await act(async () => {
        await Promise.resolve()
      })
      // No assertion needed; the test passes if no "act" warning / error is thrown.
    })
  })

  // ── Browser online / offline events ────────────────────────────────────────

  describe('browser online / offline events', () => {
    it('immediately marks unavailable when the browser fires "offline"', async () => {
      const { result } = renderHook(() => useBackendStatus())
      await waitFor(() => expect(result.current.backendAvailable).toBe(true))

      act(() => {
        window.dispatchEvent(new Event('offline'))
      })

      expect(result.current.backendAvailable).toBe(false)
    })

    it('re-probes and marks available when the browser fires "online"', async () => {
      mockedGetAuthStatus
        .mockResolvedValueOnce({ enabled: true })  // initial probe
        .mockResolvedValueOnce({ enabled: true })  // online re-probe

      const { result } = renderHook(() => useBackendStatus())
      await waitFor(() => expect(result.current.backendAvailable).toBe(true))

      // Simulate going offline then back online.
      act(() => { window.dispatchEvent(new Event('offline')) })
      expect(result.current.backendAvailable).toBe(false)

      await act(async () => { window.dispatchEvent(new Event('online')) })
      await waitFor(() => expect(result.current.backendAvailable).toBe(true))
      expect(mockedGetAuthStatus).toHaveBeenCalledTimes(2)
    })

    it('recovers to available after a re-probe that succeeds following a failed attempt', async () => {
      mockedGetAuthStatus
        .mockRejectedValueOnce(new TypeError('Failed to fetch'))  // initial — unavailable
        .mockResolvedValueOnce({ enabled: false })                 // online re-probe — available

      const { result } = renderHook(() => useBackendStatus())
      await waitFor(() => expect(result.current.backendAvailable).toBe(false))

      await act(async () => { window.dispatchEvent(new Event('online')) })
      await waitFor(() => expect(result.current.backendAvailable).toBe(true))
    })
  })

  // ── Polling ─────────────────────────────────────────────────────────────────

  describe('polling interval', () => {
    beforeEach(() => jest.useFakeTimers())
    afterEach(() => jest.useRealTimers())

    it('calls the probe again after the configured interval', async () => {
      mockedGetAuthStatus.mockResolvedValue({ enabled: true })

      renderHook(() => useBackendStatus({ pollMs: 5_000 }))

      // Flush the initial probe.
      await act(async () => { await Promise.resolve() })
      expect(mockedGetAuthStatus).toHaveBeenCalledTimes(1)

      // Advance one full interval.
      await act(async () => {
        jest.advanceTimersByTime(5_000)
        await Promise.resolve()
      })
      expect(mockedGetAuthStatus).toHaveBeenCalledTimes(2)

      // Advance a second interval.
      await act(async () => {
        jest.advanceTimersByTime(5_000)
        await Promise.resolve()
      })
      expect(mockedGetAuthStatus).toHaveBeenCalledTimes(3)
    })

    it('stops polling after the hook is unmounted', async () => {
      mockedGetAuthStatus.mockResolvedValue({ enabled: true })

      const { unmount } = renderHook(() => useBackendStatus({ pollMs: 5_000 }))
      await act(async () => { await Promise.resolve() })
      expect(mockedGetAuthStatus).toHaveBeenCalledTimes(1)

      unmount()

      // Advancing time must NOT trigger further calls.
      await act(async () => {
        jest.advanceTimersByTime(15_000)
        await Promise.resolve()
      })
      expect(mockedGetAuthStatus).toHaveBeenCalledTimes(1)
    })

    it('flips to unavailable mid-poll when a periodic probe fails', async () => {
      mockedGetAuthStatus
        .mockResolvedValueOnce({ enabled: true })              // initial — OK
        .mockRejectedValueOnce(new TypeError('Failed to fetch'))  // 2nd poll — down

      const { result } = renderHook(() => useBackendStatus({ pollMs: 5_000 }))
      await act(async () => { await Promise.resolve() })
      expect(result.current.backendAvailable).toBe(true)

      await act(async () => {
        jest.advanceTimersByTime(5_000)
        await Promise.resolve()
      })
      await waitFor(() => expect(result.current.backendAvailable).toBe(false))
    })
  })
})

