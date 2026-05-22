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

function renderViewer(project = 'my-project', fromVersion = '1') {
  render(<DiffViewer project={project} fromVersion={fromVersion} />)
}

async function fillAndSubmit(to = '2') {
  const user = userEvent.setup()
  await user.type(screen.getByPlaceholderText('2'), to)
  await user.click(screen.getByRole('button', { name: /compare/i }))
}

// ── tests ─────────────────────────────────────────────────────────────────────

describe('DiffViewer', () => {
  beforeEach(() => jest.clearAllMocks())

  it('renders the to-version field and compare button', () => {
    renderViewer()
    expect(screen.getByPlaceholderText('2')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /compare/i })).toBeInTheDocument()
  })

  it('Compare button is disabled when to-version is empty', () => {
    renderViewer()
    expect(screen.getByRole('button', { name: /compare/i })).toBeDisabled()
  })

  it('Compare button is disabled when project prop is missing', () => {
    render(<DiffViewer fromVersion="1" />)
    expect(screen.getByRole('button', { name: /compare/i })).toBeDisabled()
  })

  it('Compare button is disabled when fromVersion prop is missing', () => {
    render(<DiffViewer project="my-project" />)
    expect(screen.getByRole('button', { name: /compare/i })).toBeDisabled()
  })

  it('Compare button becomes enabled when to-version is filled and props are set', async () => {
    const user = userEvent.setup()
    renderViewer()
    await user.type(screen.getByPlaceholderText('2'), '2')
    expect(screen.getByRole('button', { name: /compare/i })).toBeEnabled()
  })

  it('shows loading spinner while fetching', async () => {
    mockedGetDiff.mockReturnValue(new Promise(() => {})) // never resolves
    renderViewer()
    await fillAndSubmit()
    expect(document.querySelector('.animate-spin')).toBeInTheDocument()
  })

  it('shows "no differences" message when units is empty', async () => {
    mockedGetDiff.mockResolvedValue(makeDiff({ units: [] }))
    renderViewer()
    await fillAndSubmit()
    await waitFor(() =>
      expect(screen.getByText(/no differences found/i)).toBeInTheDocument()
    )
  })

  it('shows error message when getDiff rejects', async () => {
    mockedGetDiff.mockRejectedValue(new Error('Network error'))
    renderViewer()
    await fillAndSubmit()
    await waitFor(() => expect(screen.getByText('Network error')).toBeInTheDocument())
  })

  it('shows fallback error when rejection value is not an Error', async () => {
    mockedGetDiff.mockRejectedValue('boom')
    renderViewer()
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
    renderViewer()
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
    renderViewer()
    await fillAndSubmit()

    await waitFor(() => expect(screen.getByText(/com\/A/)).toBeInTheDocument())

    expect(screen.getByRole('button', { name: /^removed/ })).toHaveTextContent('1')
    expect(screen.getByRole('button', { name: /^added/ })).toHaveTextContent('1')
    expect(screen.getByRole('button', { name: /^all/ })).toHaveTextContent('2')

    fireEvent.click(screen.getByRole('button', { name: /^removed/ }))
    expect(screen.queryByText('com/A')).not.toBeInTheDocument()
    expect(screen.getByText('com/B')).toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: /^added/ }))
    expect(screen.getByText('com/A')).toBeInTheDocument()
    expect(screen.queryByText('com/B')).not.toBeInTheDocument()

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
    renderViewer()
    await fillAndSubmit()
    await waitFor(() => expect(screen.getByText('com/A')).toBeInTheDocument())

    fireEvent.click(screen.getByRole('button', { name: /^removed/ }))
    expect(screen.getByText(/no units match the selected filter/i)).toBeInTheDocument()
  })

  it('shows empty-state illustration before any diff is loaded', () => {
    renderViewer()
    expect(screen.getByText(/no diff loaded yet/i)).toBeInTheDocument()
  })

  it('hides empty state once a diff result arrives', async () => {
    mockedGetDiff.mockResolvedValue(makeDiff({ units: [] }))
    renderViewer()
    expect(screen.getByText(/no diff loaded yet/i)).toBeInTheDocument()
    await fillAndSubmit()
    await waitFor(() => expect(screen.queryByText(/no diff loaded yet/i)).not.toBeInTheDocument())
  })

  it('hides empty state when an error occurs', async () => {
    mockedGetDiff.mockRejectedValue(new Error('fail'))
    renderViewer()
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
    renderViewer()
    await fillAndSubmit()
    await waitFor(() => expect(screen.getByText('com/C')).toBeInTheDocument())

    const rowButton = screen.getByText('com/C').closest('button')!
    expect(screen.queryByText('secret')).not.toBeInTheDocument()
    rowButton.focus()
    await user.keyboard('{Enter}')
    expect(screen.getByText('secret')).toBeInTheDocument()
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
    renderViewer()
    await fillAndSubmit()
    await waitFor(() => expect(screen.getByText('com/C')).toBeInTheDocument())

    fireEvent.click(screen.getByText('com/C').closest('button')!)

    await waitFor(() => expect(screen.getByText('hello')).toBeInTheDocument())
    fireEvent.click(screen.getByText('hello').closest('button')!)

    await waitFor(() => expect(screen.getByText('METHOD_INVOCATION_PARAMETER')).toBeInTheDocument())
    expect(screen.getByText('(Logging)')).toBeInTheDocument()
    expect(screen.getByText(/com\/Foo#bar:10/)).toBeInTheDocument()
  })

  it('renders the project header and version after diff', async () => {
    mockedGetDiff.mockResolvedValue(makeDiff({ fromVersion: 3, toVersion: 5, units: [] }))
    render(<DiffViewer project="my-project" fromVersion="3" />)
    await fillAndSubmit('5')
    await waitFor(() => {
      const h2 = document.querySelector('h2')
      expect(h2?.textContent).toMatch(/my-project/)
      expect(h2?.textContent).toMatch(/v3/)
      expect(h2?.textContent).toMatch(/v5/)
    })
  })
})

