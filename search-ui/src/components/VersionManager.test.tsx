import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { VersionManager } from './VersionManager'
import * as versionApi from '@/api/versionApi'
import type { ProjectVersion } from '@/api/versionApi'

jest.mock('@/api/versionApi')
const mockedGetVersion = versionApi.getVersion as jest.MockedFunction<typeof versionApi.getVersion>
const mockedFinalize = versionApi.finalizeVersion as jest.MockedFunction<typeof versionApi.finalizeVersion>
const mockedSync = versionApi.syncRemovals as jest.MockedFunction<typeof versionApi.syncRemovals>
const mockedDelete = versionApi.deleteUnit as jest.MockedFunction<typeof versionApi.deleteUnit>

function makeVersion(overrides: Partial<ProjectVersion> = {}): ProjectVersion {
  return {
    id: 1,
    project: 'demo',
    version: 1,
    parentVersion: null,
    status: 'OPEN',
    createdAt: '2026-01-01T00:00:00Z',
    finalizedAt: null,
    ...overrides,
  }
}

async function lookup(project = 'demo', version = '1') {
  const user = userEvent.setup()
  await user.type(screen.getByPlaceholderText(/e\.g\. demo-crud-server/i), project)
  await user.type(screen.getByPlaceholderText(/e\.g\. 1/i), version)
  await user.click(screen.getByRole('button', { name: /lookup/i }))
}

beforeEach(() => {
  jest.clearAllMocks()
  jest.spyOn(window, 'confirm').mockReturnValue(true)
})

describe('VersionManager', () => {
  it('renders lookup form fields', () => {
    render(<VersionManager />)
    expect(screen.getByPlaceholderText(/e\.g\. demo-crud-server/i)).toBeInTheDocument()
    expect(screen.getByPlaceholderText(/e\.g\. 1/i)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /lookup/i })).toBeDisabled()
  })

  it('lookup button enables when both fields are filled', async () => {
    const user = userEvent.setup()
    render(<VersionManager />)
    await user.type(screen.getByPlaceholderText(/e\.g\. demo-crud-server/i), 'demo')
    await user.type(screen.getByPlaceholderText(/e\.g\. 1/i), '1')
    expect(screen.getByRole('button', { name: /lookup/i })).toBeEnabled()
  })

  it('shows version details card after successful lookup', async () => {
    mockedGetVersion.mockResolvedValue(makeVersion())
    render(<VersionManager />)
    await lookup()
    await waitFor(() => expect(screen.getByText(/demo v1/i)).toBeInTheDocument())
    expect(screen.getByText('OPEN')).toBeInTheDocument()
  })

  it('shows error on failed lookup', async () => {
    mockedGetVersion.mockRejectedValue(new Error('Version not found.'))
    render(<VersionManager />)
    await lookup()
    await waitFor(() => expect(screen.getByText('Version not found.')).toBeInTheDocument())
  })

  it('shows fallback error for non-Error rejection', async () => {
    mockedGetVersion.mockRejectedValue('fail')
    render(<VersionManager />)
    await lookup()
    await waitFor(() => expect(screen.getByText('Lookup failed')).toBeInTheDocument())
  })

  it('shows parent version info and inheritance note', async () => {
    mockedGetVersion.mockResolvedValue(makeVersion({ parentVersion: 3 }))
    render(<VersionManager />)
    await lookup()
    await waitFor(() => expect(screen.getByText('v3')).toBeInTheDocument())
    expect(screen.getByText(/inherits units from v3/i)).toBeInTheDocument()
  })

  it('shows finalized timestamp for FINALIZED versions', async () => {
    mockedGetVersion.mockResolvedValue(makeVersion({ status: 'FINALIZED', finalizedAt: '2026-02-01T10:00:00Z' }))
    render(<VersionManager />)
    await lookup()
    await waitFor(() => expect(screen.getByText('FINALIZED')).toBeInTheDocument())
    // "Finalized" label (the muted-foreground span, not the status badge)
    expect(screen.getAllByText(/finalized/i).length).toBeGreaterThan(0)
  })

  it('does not show action buttons for FINALIZED versions', async () => {
    mockedGetVersion.mockResolvedValue(makeVersion({ status: 'FINALIZED', finalizedAt: '2026-02-01T10:00:00Z' }))
    render(<VersionManager />)
    await lookup()
    await waitFor(() => expect(screen.getByText('FINALIZED')).toBeInTheDocument())
    expect(screen.queryByRole('button', { name: /close version/i })).not.toBeInTheDocument()
  })

  it('finalizes the version when Close Version is confirmed', async () => {
    const user = userEvent.setup()
    mockedGetVersion.mockResolvedValue(makeVersion())
    mockedFinalize.mockResolvedValue(makeVersion({ status: 'FINALIZED', finalizedAt: '2026-02-01T00:00:00Z' }))
    render(<VersionManager />)
    await lookup()
    await waitFor(() => screen.getByRole('button', { name: /close version/i }))
    await user.click(screen.getByRole('button', { name: /close version/i }))
    await waitFor(() => expect(screen.getByText(/version 1 closed/i)).toBeInTheDocument())
  })

  it('does not finalize if confirm is cancelled', async () => {
    const user = userEvent.setup()
    jest.spyOn(window, 'confirm').mockReturnValue(false)
    mockedGetVersion.mockResolvedValue(makeVersion())
    render(<VersionManager />)
    await lookup()
    await waitFor(() => screen.getByRole('button', { name: /close version/i }))
    await user.click(screen.getByRole('button', { name: /close version/i }))
    expect(mockedFinalize).not.toHaveBeenCalled()
  })

  it('shows error when finalize fails', async () => {
    const user = userEvent.setup()
    mockedGetVersion.mockResolvedValue(makeVersion())
    mockedFinalize.mockRejectedValue(new Error('Version is already finalized.'))
    render(<VersionManager />)
    await lookup()
    await waitFor(() => screen.getByRole('button', { name: /close version/i }))
    await user.click(screen.getByRole('button', { name: /close version/i }))
    await waitFor(() => expect(screen.getByText('Version is already finalized.')).toBeInTheDocument())
  })

  it('syncs removals and shows results list', async () => {
    const user = userEvent.setup()
    mockedGetVersion.mockResolvedValue(makeVersion())
    mockedSync.mockResolvedValue(['com/Foo', 'com/Bar'])
    render(<VersionManager />)
    await lookup()
    await waitFor(() => screen.getByRole('button', { name: /sync removals/i }))
    await user.click(screen.getByRole('button', { name: /sync removals/i }))
    await waitFor(() => expect(screen.getByText('com/Foo')).toBeInTheDocument())
    expect(screen.getByText('com/Bar')).toBeInTheDocument()
    expect(screen.getByText(/detected 2 removed unit/i)).toBeInTheDocument()
  })

  it('syncs removals and shows no-removals message for empty list', async () => {
    const user = userEvent.setup()
    mockedGetVersion.mockResolvedValue(makeVersion())
    mockedSync.mockResolvedValue([])
    render(<VersionManager />)
    await lookup()
    await waitFor(() => screen.getByRole('button', { name: /sync removals/i }))
    await user.click(screen.getByRole('button', { name: /sync removals/i }))
    await waitFor(() => expect(screen.getByText(/no removals detected/i)).toBeInTheDocument())
  })

  it('shows error when sync fails', async () => {
    const user = userEvent.setup()
    mockedGetVersion.mockResolvedValue(makeVersion())
    mockedSync.mockRejectedValue(new Error('Sync failed (HTTP 500)'))
    render(<VersionManager />)
    await lookup()
    await waitFor(() => screen.getByRole('button', { name: /sync removals/i }))
    await user.click(screen.getByRole('button', { name: /sync removals/i }))
    await waitFor(() => expect(screen.getByText('Sync failed (HTTP 500)')).toBeInTheDocument())
  })

  it('deletes a unit and shows success', async () => {
    const user = userEvent.setup()
    mockedGetVersion.mockResolvedValue(makeVersion())
    mockedDelete.mockResolvedValue(undefined)
    render(<VersionManager />)
    await lookup()
    await waitFor(() => screen.getByPlaceholderText('com/example/Foo'))
    await user.type(screen.getByPlaceholderText('com/example/Foo'), 'com/Bar')
    await user.click(screen.getByRole('button', { name: /^delete$/i }))
    await waitFor(() => expect(screen.getByText(/unit "com\/Bar" deleted/i)).toBeInTheDocument())
  })

  it('shows error when delete fails', async () => {
    const user = userEvent.setup()
    mockedGetVersion.mockResolvedValue(makeVersion())
    mockedDelete.mockRejectedValue(new Error('Version is finalized; cannot delete.'))
    render(<VersionManager />)
    await lookup()
    await waitFor(() => screen.getByPlaceholderText('com/example/Foo'))
    await user.type(screen.getByPlaceholderText('com/example/Foo'), 'com/Bar')
    await user.click(screen.getByRole('button', { name: /^delete$/i }))
    await waitFor(() => expect(screen.getByText('Version is finalized; cannot delete.')).toBeInTheDocument())
  })
})

