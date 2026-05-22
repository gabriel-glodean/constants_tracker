import { render, screen, waitFor, act } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { JarJobsPanel } from './JarJobsPanel'
import * as jarJobsApi from '@/api/jarJobsApi'

jest.mock('@/api/jarJobsApi')
const mockedGetJarJobs = jarJobsApi.getJarJobs as jest.MockedFunction<typeof jarJobsApi.getJarJobs>

const COMPLETED_JOB: jarJobsApi.JarJob = {
  project: 'my-app',
  version: 1,
  jarName: 'app.jar',
  status: 'COMPLETED',
  startedAt: '2026-05-21T10:00:00Z',
  lastUpdatedAt: '2026-05-21T10:01:00Z',
  nestedTotal: 10,
  nestedProcessed: 10,
  nestedFailed: 0,
  errorMessage: null,
}

const STARTED_JOB: jarJobsApi.JarJob = {
  ...COMPLETED_JOB,
  jarName: 'other.jar',
  status: 'STARTED',
  nestedProcessed: 3,
}

const FAILED_JOB: jarJobsApi.JarJob = {
  ...COMPLETED_JOB,
  jarName: 'bad.jar',
  status: 'FAILED',
  nestedFailed: 2,
  errorMessage: 'Boom',
}

beforeEach(() => {
  jest.clearAllMocks()
  jest.useFakeTimers()
})

afterEach(() => {
  jest.runOnlyPendingTimers()
  jest.useRealTimers()
})

describe('JarJobsPanel', () => {
  it('renders nothing when project or version is not set', () => {
    const { container } = render(<JarJobsPanel project="" version="" />)
    expect(container.firstChild).toBeNull()
  })

  it('renders nothing when version is invalid', () => {
    const { container } = render(<JarJobsPanel project="my-app" version="abc" />)
    expect(container.firstChild).toBeNull()
  })

  it('fetches and displays jobs on mount', async () => {
    mockedGetJarJobs.mockResolvedValue([COMPLETED_JOB])
    render(<JarJobsPanel project="my-app" version="1" />)
    await waitFor(() => expect(screen.getByText('app.jar')).toBeInTheDocument())
    expect(screen.getByText('COMPLETED')).toBeInTheDocument()
    expect(screen.getByText('10 / 10')).toBeInTheDocument()
  })

  it('shows STARTED badge with animation class', async () => {
    mockedGetJarJobs.mockResolvedValue([STARTED_JOB])
    render(<JarJobsPanel project="my-app" version="1" />)
    await waitFor(() => expect(screen.getByText('STARTED')).toBeInTheDocument())
  })

  it('shows failed count in destructive colour', async () => {
    mockedGetJarJobs.mockResolvedValue([FAILED_JOB])
    render(<JarJobsPanel project="my-app" version="1" />)
    await waitFor(() => expect(screen.getByText('FAILED')).toBeInTheDocument())
    expect(screen.getByText('2')).toBeInTheDocument()
  })

  it('shows empty state when no jobs returned', async () => {
    mockedGetJarJobs.mockResolvedValue([])
    render(<JarJobsPanel project="my-app" version="1" />)
    await waitFor(() =>
      expect(screen.getByText(/no extraction jobs found/i)).toBeInTheDocument(),
    )
  })

  it('shows error message on fetch failure', async () => {
    mockedGetJarJobs.mockRejectedValue(new Error('Jar jobs fetch failed (HTTP 500)'))
    render(<JarJobsPanel project="my-app" version="1" />)
    await waitFor(() =>
      expect(screen.getByText(/Jar jobs fetch failed/i)).toBeInTheDocument(),
    )
  })

  it('polls again while a job is STARTED', async () => {
    mockedGetJarJobs
      .mockResolvedValueOnce([STARTED_JOB])
      .mockResolvedValueOnce([COMPLETED_JOB])

    render(<JarJobsPanel project="my-app" version="1" />)
    await waitFor(() => expect(screen.getByText('STARTED')).toBeInTheDocument())

    await act(async () => {
      jest.advanceTimersByTime(3_100)
    })
    await waitFor(() => expect(screen.getByText('COMPLETED')).toBeInTheDocument())
  })

  it('stops polling when all jobs are terminal', async () => {
    mockedGetJarJobs.mockResolvedValue([COMPLETED_JOB])
    render(<JarJobsPanel project="my-app" version="1" />)
    await waitFor(() => expect(screen.getByText('COMPLETED')).toBeInTheDocument())

    await act(async () => {
      jest.advanceTimersByTime(10_000)
    })
    // Only the initial + schedule calls, not repeated polling
    expect(mockedGetJarJobs).toHaveBeenCalledTimes(2)
  })

  it('manual refresh button triggers re-fetch', async () => {
    mockedGetJarJobs.mockResolvedValue([COMPLETED_JOB])
    const user = userEvent.setup({ advanceTimers: jest.advanceTimersByTime })
    render(<JarJobsPanel project="my-app" version="1" />)
    await waitFor(() => expect(screen.getByText('app.jar')).toBeInTheDocument())

    mockedGetJarJobs.mockResolvedValue([FAILED_JOB])
    await user.click(screen.getByTitle('Refresh'))
    await waitFor(() => expect(screen.getByText('bad.jar')).toBeInTheDocument())
  })
})

