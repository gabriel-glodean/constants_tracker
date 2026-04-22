import { render, screen, fireEvent, act } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { ResultsTable } from './ResultsTable'
import type { FuzzySearchResponse } from '@/api/searchApi'

function makeResponse(overrides: Partial<FuzzySearchResponse> = {}): FuzzySearchResponse {
  return { hits: [], totalFound: 0, ...overrides }
}

const baseHit = {
  project: 'demo',
  unitName: 'com/example/Foo',
  version: 1,
  sourceKind: 'CLASS',
  constantValues: ['SELECT *', 'hello'],
  semanticPairs: ['SELECT *|SQL Fragment|HIGH', 'hello|Log Message|MEDIUM'],
}

describe('ResultsTable', () => {
  it('shows empty state when hits is empty', () => {
    render(<ResultsTable data={makeResponse()} searchTerm="foo" />)
    expect(screen.getByText(/no results found/i)).toBeInTheDocument()
    expect(screen.getByText(/foo/)).toBeInTheDocument()
  })

  it('shows results count header', () => {
    render(<ResultsTable data={makeResponse({ hits: [baseHit], totalFound: 42 })} searchTerm="sel" />)
    expect(screen.getByText('1')).toBeInTheDocument()
    expect(screen.getByText('42')).toBeInTheDocument()
  })

  it('renders short class name and package path', () => {
    render(<ResultsTable data={makeResponse({ hits: [baseHit], totalFound: 1 })} searchTerm="" />)
    expect(screen.getByText('Foo')).toBeInTheDocument()
    expect(screen.getByText('com.example')).toBeInTheDocument()
  })

  it('renders project and version badges', () => {
    render(<ResultsTable data={makeResponse({ hits: [baseHit], totalFound: 1 })} searchTerm="" />)
    expect(screen.getByText('demo')).toBeInTheDocument()
    expect(screen.getByText('v1')).toBeInTheDocument()
  })

  it('renders sourceKind badge', () => {
    render(<ResultsTable data={makeResponse({ hits: [baseHit], totalFound: 1 })} searchTerm="" />)
    expect(screen.getByText('CLASS')).toBeInTheDocument()
  })

  it('expands a hit row to show constant values', async () => {
    const user = userEvent.setup()
    render(<ResultsTable data={makeResponse({ hits: [baseHit], totalFound: 1 })} searchTerm="SELECT" />)
    await user.click(screen.getByRole('button', { name: /foo/i }))
    // 'SELECT *' is split across mark+span elements; check via code element text content
    expect(document.querySelector('code')?.textContent).toContain('SELECT *')
    expect(screen.getByText('hello')).toBeInTheDocument()
  })

  it('shows semantic badges after expanding', async () => {
    const user = userEvent.setup()
    render(<ResultsTable data={makeResponse({ hits: [baseHit], totalFound: 1 })} searchTerm="" />)
    await user.click(screen.getByRole('button', { name: /foo/i }))
    expect(screen.getByText('SQL Fragment')).toBeInTheDocument()
    expect(screen.getByText('Log Message')).toBeInTheDocument()
  })

  it('highlights matching search term in constant value', async () => {
    const user = userEvent.setup()
    render(<ResultsTable data={makeResponse({ hits: [baseHit], totalFound: 1 })} searchTerm="SELECT" />)
    await user.click(screen.getByRole('button', { name: /foo/i }))
    const mark = document.querySelector('mark')
    expect(mark?.textContent).toBe('SELECT')
  })

  it('copies constant value to clipboard on copy button click', async () => {
    const user = userEvent.setup()
    const writeText = jest.fn().mockResolvedValue(undefined)
    Object.defineProperty(navigator, 'clipboard', { value: { writeText }, configurable: true })
    render(<ResultsTable data={makeResponse({ hits: [baseHit], totalFound: 1 })} searchTerm="" />)
    await user.click(screen.getByRole('button', { name: /foo/i }))
    const copyButtons = screen.getAllByTitle('Copy to clipboard')
    await user.click(copyButtons[0])
    expect(writeText).toHaveBeenCalledWith('SELECT *')
  })

  it('deduplicates semantic badges by type', () => {
    const hit = {
      ...baseHit,
      constantValues: ['dup'],
      semanticPairs: ['dup|SQL Fragment|HIGH', 'dup|SQL Fragment|LOW', 'dup|Unknown|0'],
    }
    render(<ResultsTable data={makeResponse({ hits: [hit], totalFound: 1 })} searchTerm="" />)
    fireEvent.click(screen.getByRole('button', { name: /foo/i }))
    // should deduplicate: only one SQL Fragment badge, Unknown removed
    const badges = screen.queryAllByText('SQL Fragment')
    expect(badges).toHaveLength(1)
    expect(screen.queryByText('Unknown')).not.toBeInTheDocument()
  })

  it('renders unit with no package (top-level class)', () => {
    const hit = { ...baseHit, unitName: 'Foo' }
    render(<ResultsTable data={makeResponse({ hits: [hit], totalFound: 1 })} searchTerm="" />)
    expect(screen.getByText('Foo')).toBeInTheDocument()
  })

  it('collapses an expanded row on second click', async () => {
    const user = userEvent.setup()
    render(<ResultsTable data={makeResponse({ hits: [baseHit], totalFound: 1 })} searchTerm="" />)
    await user.click(screen.getByRole('button', { name: /foo/i }))
    expect(screen.getByText('SELECT *')).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: /foo/i }))
    expect(screen.queryByText('SELECT *')).not.toBeInTheDocument()
  })

  it('renders no sourceKind badge when sourceKind is falsy', () => {
    const hit = { ...baseHit, sourceKind: '' }
    render(<ResultsTable data={makeResponse({ hits: [hit], totalFound: 1 })} searchTerm="" />)
    expect(screen.queryByText('CLASS')).not.toBeInTheDocument()
  })

  it('HighlightedText renders plain text when highlight is empty', async () => {
    const user = userEvent.setup()
    render(<ResultsTable data={makeResponse({ hits: [baseHit], totalFound: 1 })} searchTerm="" />)
    await user.click(screen.getByRole('button', { name: /foo/i }))
    expect(document.querySelector('mark')).not.toBeInTheDocument()
  })

  it('copy button reverts after 2 seconds', async () => {
    jest.useFakeTimers()
    const user = userEvent.setup({ advanceTimers: jest.advanceTimersByTime })
    const writeText = jest.fn().mockResolvedValue(undefined)
    Object.defineProperty(navigator, 'clipboard', { value: { writeText }, configurable: true })
    render(<ResultsTable data={makeResponse({ hits: [baseHit], totalFound: 1 })} searchTerm="" />)
    await user.click(screen.getByRole('button', { name: /foo/i }))
    const copyButtons = screen.getAllByTitle('Copy to clipboard')
    await user.click(copyButtons[0])
    act(() => jest.advanceTimersByTime(2100))
    jest.useRealTimers()
  })
})

