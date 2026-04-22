import { render, screen, fireEvent, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { DiffViewer } from './DiffViewer'
import * as diffApi from '@/api/diffApi'
import type { ProjectDiffResponse } from '@/api/diffApi'

jest.mock('@/api/diffApi')
const mockedGetDiff = diffApi.getDiff as jest.MockedFunction<typeof diffApi.getDiff>

// ── helpers ──────────────────────────────────────────────────────────────────

function makeDiff(overrides: Partial<ProjectDiffResponse> = {}): ProjectDiffResponse {
  return {
    project: 'my-project',
    fromVersion: 1,
    toVersion: 2,
    units: [],
    ...overrides,
  }
}

async function fillAndSubmit(
  project = 'my-project',
  from = '1',
  to = '2'
) {
  const user = userEvent.setup()
  await user.type(screen.getByPlaceholderText(/e\.g\. demo-crud-server/i), project)
  await user.type(screen.getByPlaceholderText('1'), from)
  await user.type(screen.getByPlaceholderText('2'), to)
  await user.click(screen.getByRole('button', { name: /compare/i }))
}

// ── tests ─────────────────────────────────────────────────────────────────────

describe('DiffViewer', () => {
  beforeEach(() => jest.clearAllMocks())

  it('renders the form fields', () => {
    render(<DiffViewer />)
    expect(screen.getByPlaceholderText(/e\.g\. demo-crud-server/i)).toBeInTheDocument()
    expect(screen.getByPlaceholderText('1')).toBeInTheDocument()
    expect(screen.getByPlaceholderText('2')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /compare/i })).toBeInTheDocument()
  })

  it('Compare button is disabled when fields are empty', () => {
    render(<DiffViewer />)
    expect(screen.getByRole('button', { name: /compare/i })).toBeDisabled()
  })

  it('Compare button becomes enabled when all fields are filled', async () => {
    const user = userEvent.setup()
    render(<DiffViewer />)
    await user.type(screen.getByPlaceholderText(/e\.g\. demo-crud-server/i), 'proj')
    await user.type(screen.getByPlaceholderText('1'), '1')
    await user.type(screen.getByPlaceholderText('2'), '2')
    expect(screen.getByRole('button', { name: /compare/i })).toBeEnabled()
  })

  it('shows loading spinner while fetching', async () => {
    mockedGetDiff.mockReturnValue(new Promise(() => {})) // never resolves
    render(<DiffViewer />)
    await fillAndSubmit()
    expect(document.querySelector('.animate-spin')).toBeInTheDocument()
  })

  it('shows "no differences" message when units is empty', async () => {
    mockedGetDiff.mockResolvedValue(makeDiff({ units: [] }))
    render(<DiffViewer />)
    await fillAndSubmit()
    await waitFor(() =>
      expect(screen.getByText(/no differences found/i)).toBeInTheDocument()
    )
  })

  it('shows error message when getDiff rejects', async () => {
    mockedGetDiff.mockRejectedValue(new Error('Network error'))
    render(<DiffViewer />)
    await fillAndSubmit()
    await waitFor(() => expect(screen.getByText('Network error')).toBeInTheDocument())
  })

  it('shows fallback error when rejection value is not an Error', async () => {
    mockedGetDiff.mockRejectedValue('boom')
    render(<DiffViewer />)
    await fillAndSubmit()
    await waitFor(() => expect(screen.getByText(/diff failed/i)).toBeInTheDocument())
  })

  it('renders diff summary with added / removed / changed counts', async () => {
    mockedGetDiff.mockResolvedValue(
      makeDiff({
        units: [
          { path: 'com/A', addedUnit: true, removedUnit: false, changedConstants: [] },
          { path: 'com/B', addedUnit: false, removedUnit: true, changedConstants: [] },
          {
            path: 'com/C',
            addedUnit: false,
            removedUnit: false,
            changedConstants: [
              { value: '42', valueType: 'int', changeKind: 'CHANGED', fromUsages: [], toUsages: [] },
            ],
          },
        ],
      })
    )
    render(<DiffViewer />)
    await fillAndSubmit()
    await waitFor(() => expect(screen.getByText(/\+1 added/i)).toBeInTheDocument())
    expect(screen.getByText(/−1 removed/i)).toBeInTheDocument()
    expect(screen.getByText(/~1 changed/i)).toBeInTheDocument()
    expect(screen.getByText(/3 unit\(s\) total/i)).toBeInTheDocument()
  })

  it('filter buttons are rendered and work', async () => {
    mockedGetDiff.mockResolvedValue(
      makeDiff({
        units: [
          { path: 'com/A', addedUnit: true, removedUnit: false, changedConstants: [] },
          { path: 'com/B', addedUnit: false, removedUnit: true, changedConstants: [] },
        ],
      })
    )
    render(<DiffViewer />)
    await fillAndSubmit()

    // wait for results
    await waitFor(() => expect(screen.getByText(/com\/A/)).toBeInTheDocument())

    // filter pills show counts (e.g. "removed 1", "added 1", "all 2")
    expect(screen.getByRole('button', { name: /^removed/ })).toHaveTextContent('1')
    expect(screen.getByRole('button', { name: /^added/ })).toHaveTextContent('1')
    expect(screen.getByRole('button', { name: /^all/ })).toHaveTextContent('2')

    // click 'removed' filter
    fireEvent.click(screen.getByRole('button', { name: /^removed/ }))
    expect(screen.queryByText('com/A')).not.toBeInTheDocument()
    expect(screen.getByText('com/B')).toBeInTheDocument()

    // click 'added' filter
    fireEvent.click(screen.getByRole('button', { name: /^added/ }))
    expect(screen.getByText('com/A')).toBeInTheDocument()
    expect(screen.queryByText('com/B')).not.toBeInTheDocument()

    // click 'all' filter to restore
    fireEvent.click(screen.getByRole('button', { name: /^all/ }))
    expect(screen.getByText('com/A')).toBeInTheDocument()
    expect(screen.getByText('com/B')).toBeInTheDocument()
  })

  it('shows "No units match" when filter yields empty list', async () => {
    mockedGetDiff.mockResolvedValue(
      makeDiff({
        units: [
          { path: 'com/A', addedUnit: true, removedUnit: false, changedConstants: [] },
        ],
      })
    )
    render(<DiffViewer />)
    await fillAndSubmit()
    await waitFor(() => expect(screen.getByText('com/A')).toBeInTheDocument())

    fireEvent.click(screen.getByRole('button', { name: /^removed/ }))
    expect(screen.getByText(/no units match the selected filter/i)).toBeInTheDocument()
  })

  it('shows empty-state illustration before any diff is loaded', () => {
    render(<DiffViewer />)
    expect(screen.getByText(/no diff loaded yet/i)).toBeInTheDocument()
    expect(screen.getByText(/enter a project name/i)).toBeInTheDocument()
  })

  it('hides empty state once a diff result arrives', async () => {
    mockedGetDiff.mockResolvedValue(makeDiff({ units: [] }))
    render(<DiffViewer />)
    expect(screen.getByText(/no diff loaded yet/i)).toBeInTheDocument()
    await fillAndSubmit()
    await waitFor(() => expect(screen.queryByText(/no diff loaded yet/i)).not.toBeInTheDocument())
  })

  it('hides empty state when an error occurs', async () => {
    mockedGetDiff.mockRejectedValue(new Error('fail'))
    render(<DiffViewer />)
    await fillAndSubmit()
    await waitFor(() => expect(screen.queryByText(/no diff loaded yet/i)).not.toBeInTheDocument())
  })

  it('toggles UnitRow collapse on Enter key', async () => {
    mockedGetDiff.mockResolvedValue(
      makeDiff({
        units: [
          {
            path: 'com/C',
            addedUnit: false,
            removedUnit: false,
            changedConstants: [
              { value: 'secret', valueType: 'String', changeKind: 'CHANGED', fromUsages: [], toUsages: [] },
            ],
          },
        ],
      })
    )
    const user = userEvent.setup()
    render(<DiffViewer />)
    await fillAndSubmit()
    await waitFor(() => expect(screen.getByText('com/C')).toBeInTheDocument())

    const rowButton = screen.getByText('com/C').closest('button')!
    // not yet expanded
    expect(screen.queryByText('secret')).not.toBeInTheDocument()
    // press Enter to expand
    rowButton.focus()
    await user.keyboard('{Enter}')
    expect(screen.getByText('secret')).toBeInTheDocument()
    // press Enter again to collapse
    await user.keyboard('{Enter}')
    expect(screen.queryByText('secret')).not.toBeInTheDocument()
  })

  it('expands a unit row that has changed constants', async () => {
    mockedGetDiff.mockResolvedValue(
      makeDiff({
        units: [
          {
            path: 'com/C',
            addedUnit: false,
            removedUnit: false,
            changedConstants: [
              {
                value: 'hello',
                valueType: 'String',
                changeKind: 'ADDED',
                fromUsages: [],
                toUsages: [
                  {
                    structuralType: 'METHOD_INVOCATION_PARAMETER',
                    semanticTypeKind: 'CORE',
                    semanticTypeName: 'LOGGING',
                    semanticDisplayName: 'Logging',
                    locationClassName: 'com/Foo',
                    locationMethodName: 'bar',
                    locationLineNumber: 10,
                    confidence: 1,
                  },
                ],
              },
            ],
          },
        ],
      })
    )
    render(<DiffViewer />)
    await fillAndSubmit()
    await waitFor(() => expect(screen.getByText('com/C')).toBeInTheDocument())

    // expand unit row
    fireEvent.click(screen.getByText('com/C').closest('button')!)

    // constant row should now appear; expand it
    await waitFor(() => expect(screen.getByText('hello')).toBeInTheDocument())
    fireEvent.click(screen.getByText('hello').closest('button')!)

    await waitFor(() => expect(screen.getByText('METHOD_INVOCATION_PARAMETER')).toBeInTheDocument())
    expect(screen.getByText('(Logging)')).toBeInTheDocument()
    expect(screen.getByText(/com\/Foo#bar:10/)).toBeInTheDocument()
  })

  it('renders the project header and version after diff', async () => {
    mockedGetDiff.mockResolvedValue(makeDiff({ fromVersion: 3, toVersion: 5, units: [] }))
    render(<DiffViewer />)
    await fillAndSubmit('my-project', '3', '5')
    await waitFor(() => {
      const h2 = document.querySelector('h2')
      expect(h2?.textContent).toMatch(/my-project/)
      expect(h2?.textContent).toMatch(/v3/)
      expect(h2?.textContent).toMatch(/v5/)
    })
  })
})

