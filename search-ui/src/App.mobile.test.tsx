/**
 * Mobile display tests for App.tsx
 *
 * jsdom does not evaluate CSS so we cannot test that `hidden sm:inline` actually
 * hides an element visually. What we CAN verify is:
 *   1. The responsive Tailwind classes are present on the right elements
 *      (structural correctness — a missing class means the design breaks on mobile).
 *   2. window.matchMedia is available (required by any library that reads it).
 *   3. Interactive behaviour (tab switching, auth gate, sign-in modal) works
 *      correctly regardless of viewport width.
 */

import { render, screen, within, fireEvent } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import App from './App'

// ── Mock all heavy child components so App layout renders in isolation ────────

jest.mock('@/components/SearchForm', () => ({
  SearchForm: ({ isLoading }: { isLoading: boolean; onSearch: unknown }) => (
    <div data-testid="search-form" data-loading={isLoading} />
  ),
}))
jest.mock('@/components/ResultsTable', () => ({
  ResultsTable: () => <div data-testid="results-table" />,
}))
jest.mock('@/components/UploadForm', () => ({
  UploadForm: () => <div data-testid="upload-form" />,
}))
jest.mock('@/components/ClassLookupForm', () => ({
  ClassLookupForm: () => <div data-testid="class-lookup-form" />,
}))
jest.mock('@/components/VersionManager', () => ({
  VersionManager: () => <div data-testid="version-manager" />,
}))
jest.mock('@/components/DiffViewer', () => ({
  DiffViewer: () => <div data-testid="diff-viewer" />,
}))
jest.mock('@/components/LoginForm', () => ({
  LoginModal: ({ onClose }: { onClose: () => void; onSuccess: () => void; signIn: unknown }) => (
    <div data-testid="login-modal">
      <button onClick={onClose} aria-label="close modal">Close</button>
    </div>
  ),
}))

// ── Auth hook — expose a setter so individual tests can flip the auth state ──

let mockIsAuthenticated = false
let mockAuthRequired = true
let mockBackendAvailable = true
const mockSignIn = jest.fn()
const mockSignOut = jest.fn()
const mockAuthFetch = jest.fn()

jest.mock('@/hooks/useAuth', () => ({
  useAuth: () => ({
    isAuthenticated: mockIsAuthenticated,
    isLoading: false,
    authRequired: mockAuthRequired,
    backendAvailable: mockBackendAvailable,
    canAccess: !mockAuthRequired || mockIsAuthenticated,
    signIn: mockSignIn,
    signOut: mockSignOut,
    authFetch: mockAuthFetch,
  }),
}))

jest.mock('@/hooks/useSearch', () => ({
  useSearch: () => ({
    data: null,
    isLoading: false,
    error: null,
    hasSearched: false,
    search: jest.fn(),
    reset: jest.fn(),
  }),
}))

// ── window.matchMedia stub ────────────────────────────────────────────────────
// jsdom does not provide matchMedia; libraries (and some tests) need it.

function mockMatchMedia(mobileWidth = 375) {
  Object.defineProperty(window, 'innerWidth', { writable: true, configurable: true, value: mobileWidth })
  Object.defineProperty(window, 'matchMedia', {
    writable: true,
    configurable: true,
    value: (query: string): MediaQueryList => ({
      matches: query.includes(`max-width`) ? mobileWidth <= parseInt(query.match(/\d+/)?.[0] ?? '0') : false,
      media: query,
      onchange: null,
      addListener: jest.fn(),
      removeListener: jest.fn(),
      addEventListener: jest.fn(),
      removeEventListener: jest.fn(),
      dispatchEvent: jest.fn(),
    }),
  })
}

beforeEach(() => {
  mockIsAuthenticated = false
  mockAuthRequired = true
  mockBackendAvailable = true
  jest.clearAllMocks()
  mockMatchMedia(375) // default to mobile width
})

// ─────────────────────────────────────────────────────────────────────────────
// 1. Header — responsive padding and content
// ─────────────────────────────────────────────────────────────────────────────

describe('Header — mobile layout', () => {
  it('header container has responsive horizontal padding (px-4 sm:px-6)', () => {
    render(<App />)
    const header = screen.getByRole('banner')
    const inner = header.firstElementChild
    expect(inner?.className).toContain('px-4')
    expect(inner?.className).toContain('sm:px-6')
  })

  it('header is sticky and sits above other content (z-10)', () => {
    render(<App />)
    const header = screen.getByRole('banner')
    expect(header.className).toContain('sticky')
    expect(header.className).toContain('z-10')
  })

  it('sign-in label text has hidden sm:inline so only the icon shows on mobile', () => {
    render(<App />)
    const signInSpan = screen.getByText('Sign in')
    expect(signInSpan.className).toContain('hidden')
    expect(signInSpan.className).toContain('sm:inline')
  })

  it('shows Sign in button when not authenticated', () => {
    render(<App />)
    expect(screen.getByTitle('Sign in')).toBeInTheDocument()
    expect(screen.queryByTitle('Sign out')).not.toBeInTheDocument()
  })

  it('shows Sign out button when authenticated', () => {
    mockIsAuthenticated = true
    render(<App />)
    expect(screen.getByTitle('Sign out')).toBeInTheDocument()
    expect(screen.queryByTitle('Sign in')).not.toBeInTheDocument()
  })

  it('sign-out label text has hidden sm:inline so only the icon shows on mobile', () => {
    mockIsAuthenticated = true
    render(<App />)
    const signOutSpan = screen.getByText('Sign out')
    expect(signOutSpan.className).toContain('hidden')
    expect(signOutSpan.className).toContain('sm:inline')
  })

  it('GitHub link is always visible', () => {
    render(<App />)
    expect(screen.getByRole('link', { name: '' })).toHaveAttribute('href', expect.stringContaining('github.com'))
  })
})

// ─────────────────────────────────────────────────────────────────────────────
// 2. Tab navigation — horizontally scrollable on mobile
// ─────────────────────────────────────────────────────────────────────────────

describe('Tab navigation — mobile layout', () => {
  it('nav element has overflow-x-auto to enable horizontal scroll on mobile', () => {
    render(<App />)
    const nav = screen.getByRole('navigation')
    expect(nav.className).toContain('overflow-x-auto')
  })

  it('tab container has min-w-max so tabs never wrap/squash on mobile', () => {
    render(<App />)
    const nav = screen.getByRole('navigation')
    const inner = nav.firstElementChild
    expect(inner?.className).toContain('min-w-max')
  })

  it('tab container has sm:min-w-0 to reset min-width on wider screens', () => {
    render(<App />)
    const nav = screen.getByRole('navigation')
    const inner = nav.firstElementChild
    expect(inner?.className).toContain('sm:min-w-0')
  })

  it('each tab button renders an icon — visible on both mobile and desktop', () => {
    render(<App />)
    const nav = screen.getByRole('navigation')
    const buttons = within(nav).getAllByRole('button')
    // All 5 tab buttons should contain an svg icon
    buttons.forEach(btn => {
      expect(btn.querySelector('svg')).not.toBeNull()
    })
  })

  it('tab label text spans have hidden sm:inline — visible only on desktop', () => {
    render(<App />)
    const nav = screen.getByRole('navigation')
    // Each tab button contains a <span> with the label
    const labelSpans = within(nav).getAllByText(/Search|Class Lookup|Diff|Upload|Versions/)
    labelSpans.forEach(span => {
      expect(span.className).toContain('hidden')
      expect(span.className).toContain('sm:inline')
    })
  })

  it('tab buttons have reduced mobile padding (px-3) and expanded desktop padding (sm:px-4)', () => {
    render(<App />)
    const nav = screen.getByRole('navigation')
    const buttons = within(nav).getAllByRole('button')
    buttons.forEach(btn => {
      expect(btn.className).toContain('px-3')
      expect(btn.className).toContain('sm:px-4')
    })
  })

  it('all 5 tabs are present and accessible', () => {
    render(<App />)
    const nav = screen.getByRole('navigation')
    expect(within(nav).getAllByRole('button')).toHaveLength(5)
  })

  it('clicking a tab updates the active content on mobile', async () => {
    const user = userEvent.setup()
    render(<App />)
    const lookupBtn = screen.getByTitle(/Fetch all constants declared/i)
    await user.click(lookupBtn)
    expect(screen.getByTestId('class-lookup-form')).toBeInTheDocument()
  })
})

// ─────────────────────────────────────────────────────────────────────────────
// 3. Main content sections — reduced padding on mobile
// ─────────────────────────────────────────────────────────────────────────────

describe('Content sections — responsive padding', () => {
  it('search section has reduced top padding on mobile (pt-8) and more on desktop (sm:pt-16)', () => {
    render(<App />)
    const main = screen.getByRole('main')
    // Find the first section (hero/search)
    const section = main.querySelector('section')
    expect(section?.className).toContain('pt-8')
    expect(section?.className).toContain('sm:pt-16')
  })

  it('search section has responsive horizontal padding', () => {
    render(<App />)
    const main = screen.getByRole('main')
    const section = main.querySelector('section')
    expect(section?.className).toContain('px-4')
    expect(section?.className).toContain('sm:px-6')
  })

  it('search heading has smaller font on mobile (text-2xl) and larger on desktop (sm:text-4xl)', () => {
    render(<App />)
    const heading = screen.getByRole('heading', { name: /Search Constants/i })
    expect(heading.className).toContain('text-2xl')
    expect(heading.className).toContain('sm:text-4xl')
  })

  it('diff section has responsive padding on mobile', async () => {
    const user = userEvent.setup()
    render(<App />)
    await user.click(screen.getByTitle(/Compare constants/i))
    const main = screen.getByRole('main')
    const section = main.querySelector('section')
    expect(section?.className).toContain('px-4')
    expect(section?.className).toContain('pt-8')
  })

  it('upload section has responsive padding on mobile', async () => {
    const user = userEvent.setup()
    render(<App />)
    await user.click(screen.getByTitle(/Index a .class/i))
    const main = screen.getByRole('main')
    const section = main.querySelector('section')
    expect(section?.className).toContain('px-4')
    expect(section?.className).toContain('pt-8')
  })

  it('versions section has responsive padding on mobile', async () => {
    const user = userEvent.setup()
    render(<App />)
    await user.click(screen.getByTitle(/Manage project versions/i))
    const main = screen.getByRole('main')
    const section = main.querySelector('section')
    expect(section?.className).toContain('px-4')
    expect(section?.className).toContain('pt-8')
  })
})

// ─────────────────────────────────────────────────────────────────────────────
// 4. Footer — stacks vertically on mobile
// ─────────────────────────────────────────────────────────────────────────────

describe('Footer — mobile layout', () => {
  it('footer inner container has flex-col for mobile stacking', () => {
    render(<App />)
    const footer = screen.getByRole('contentinfo')
    const inner = footer.firstElementChild
    expect(inner?.className).toContain('flex-col')
  })

  it('footer switches to row layout on desktop (sm:flex-row)', () => {
    render(<App />)
    const footer = screen.getByRole('contentinfo')
    const inner = footer.firstElementChild
    expect(inner?.className).toContain('sm:flex-row')
  })

  it('footer has responsive horizontal padding', () => {
    render(<App />)
    const footer = screen.getByRole('contentinfo')
    const inner = footer.firstElementChild
    expect(inner?.className).toContain('px-4')
    expect(inner?.className).toContain('sm:px-6')
  })

  it('both footer texts are always visible', () => {
    render(<App />)
    expect(screen.getByText(/Constant Tracker v/i)).toBeInTheDocument()
    expect(screen.getByText(/Solr \+ Spring WebFlux/i)).toBeInTheDocument()
  })
})

// ─────────────────────────────────────────────────────────────────────────────
// 5. Auth gate — works correctly on mobile
// ─────────────────────────────────────────────────────────────────────────────

describe('Auth gate — mobile behaviour', () => {
  it('upload tab shows lock banner when not authenticated', async () => {
    const user = userEvent.setup()
    render(<App />)
    await user.click(screen.getByTitle(/Index a .class/i))
    expect(screen.getByText(/Sign in to use this feature/i)).toBeInTheDocument()
  })

  it('versions tab shows lock banner when not authenticated', async () => {
    const user = userEvent.setup()
    render(<App />)
    await user.click(screen.getByTitle(/Manage project versions/i))
    expect(screen.getByText(/Sign in to use this feature/i)).toBeInTheDocument()
  })

  it('lock banner sign-in shortcut opens the login modal', async () => {
    const user = userEvent.setup()
    render(<App />)
    await user.click(screen.getByTitle(/Index a .class/i))
    // The inline "Sign in" link inside the banner
    const bannerSignIn = screen.getAllByText('Sign in').find(el => el.tagName === 'BUTTON' && !el.closest('header'))
    expect(bannerSignIn).toBeDefined()
    await user.click(bannerSignIn!)
    expect(screen.getByTestId('login-modal')).toBeInTheDocument()
  })

  it('upload form is disabled (pointer-events-none) when not authenticated', async () => {
    const user = userEvent.setup()
    render(<App />)
    await user.click(screen.getByTitle(/Index a .class/i))
    const wrapper = screen.getByTestId('upload-form').parentElement
    expect(wrapper?.className).toContain('pointer-events-none')
    expect(wrapper?.className).toContain('opacity-40')
  })

  it('upload form is fully enabled when authenticated', async () => {
    mockIsAuthenticated = true
    const user = userEvent.setup()
    render(<App />)
    await user.click(screen.getByTitle(/Index a .class/i))
    const wrapper = screen.getByTestId('upload-form').parentElement
    expect(wrapper?.className).not.toContain('pointer-events-none')
    expect(screen.queryByText(/Sign in to use this feature/i)).not.toBeInTheDocument()
  })

  it('sign-in header button opens the modal on mobile tap', async () => {
    const user = userEvent.setup()
    render(<App />)
    await user.click(screen.getByTitle('Sign in'))
    expect(screen.getByTestId('login-modal')).toBeInTheDocument()
  })

  it('sign-out header button calls signOut', async () => {
    mockIsAuthenticated = true
    const user = userEvent.setup()
    render(<App />)
    await user.click(screen.getByTitle('Sign out'))
    expect(mockSignOut).toHaveBeenCalledTimes(1)
  })

  it('closing the login modal removes it from the DOM', async () => {
    const user = userEvent.setup()
    render(<App />)
    await user.click(screen.getByTitle('Sign in'))
    expect(screen.getByTestId('login-modal')).toBeInTheDocument()
    await user.click(screen.getByLabelText('close modal'))
    expect(screen.queryByTestId('login-modal')).not.toBeInTheDocument()
  })
})

// ─────────────────────────────────────────────────────────────────────────────
// 7. Backend status — warning banner and auth-not-required behaviour
// ─────────────────────────────────────────────────────────────────────────────

describe('Backend status', () => {
  it('shows a warning banner when backend is unavailable', () => {
    mockBackendAvailable = false
    render(<App />)
    expect(screen.getByText(/Backend unavailable/i)).toBeInTheDocument()
  })

  it('does not show the warning banner when backend is available', () => {
    mockBackendAvailable = true
    render(<App />)
    expect(screen.queryByText(/Backend unavailable/i)).not.toBeInTheDocument()
  })

  it('hides the sign-in/sign-out buttons when auth is not required', () => {
    mockAuthRequired = false
    render(<App />)
    expect(screen.queryByTitle('Sign in')).not.toBeInTheDocument()
    expect(screen.queryByTitle('Sign out')).not.toBeInTheDocument()
  })

  it('upload form is fully enabled when auth is not required', async () => {
    mockAuthRequired = false
    const user = userEvent.setup()
    render(<App />)
    await user.click(screen.getByTitle(/Index a .class/i))
    const wrapper = screen.getByTestId('upload-form').parentElement
    expect(wrapper?.className).not.toContain('pointer-events-none')
    expect(screen.queryByText(/Sign in to use this feature/i)).not.toBeInTheDocument()
  })

  it('versions form is fully enabled when auth is not required', async () => {
    mockAuthRequired = false
    const user = userEvent.setup()
    render(<App />)
    await user.click(screen.getByTitle(/Manage project versions/i))
    const wrapper = screen.getByTestId('version-manager').parentElement
    expect(wrapper?.className).not.toContain('pointer-events-none')
    expect(screen.queryByText(/Sign in to use this feature/i)).not.toBeInTheDocument()
  })
})


// ─────────────────────────────────────────────────────────────────────────────

describe('window.matchMedia — mobile environment', () => {
  it('window.matchMedia is defined (libraries that depend on it will not throw)', () => {
    render(<App />)
    expect(window.matchMedia).toBeDefined()
    expect(typeof window.matchMedia).toBe('function')
  })

  it('matches max-width:767px query when innerWidth is 375', () => {
    expect(window.matchMedia('(max-width: 767px)').matches).toBe(true)
  })

  it('does not match max-width:767px query when innerWidth is 1024', () => {
    mockMatchMedia(1024)
    expect(window.matchMedia('(max-width: 767px)').matches).toBe(false)
  })

  it('App renders without errors at mobile viewport width (375px)', () => {
    mockMatchMedia(375)
    expect(() => render(<App />)).not.toThrow()
  })

  it('App renders without errors at tablet viewport width (768px)', () => {
    mockMatchMedia(768)
    expect(() => render(<App />)).not.toThrow()
  })

  it('App renders without errors at desktop viewport width (1440px)', () => {
    mockMatchMedia(1440)
    expect(() => render(<App />)).not.toThrow()
  })

  it('firing a resize event does not crash the app', () => {
    mockMatchMedia(375)
    render(<App />)
    expect(() => {
      mockMatchMedia(768)
      fireEvent(window, new Event('resize'))
    }).not.toThrow()
  })
})

