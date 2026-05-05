import { useState, useRef, Fragment } from 'react'
import { Database, GithubIcon, AlertCircle, Search as SearchIcon, Upload as UploadIcon, BookOpen, GitBranch, GitCompareArrows, Lock, LogIn, LogOut, ServerCrash } from 'lucide-react'
import { SearchForm } from '@/components/SearchForm'
import { ResultsTable } from '@/components/ResultsTable'
import { useSearch } from '@/hooks/useSearch'
import { useAuth } from '@/hooks/useAuth'
import { UploadForm } from '@/components/UploadForm'
import { ClassLookupForm } from '@/components/ClassLookupForm'
import { VersionManager } from '@/components/VersionManager'
import { DiffViewer } from '@/components/DiffViewer'
import { LoginModal } from '@/components/LoginForm'

const TABS = [
  { key: 'search',   label: 'Search',       icon: SearchIcon,       title: 'Full-text fuzzy search across all indexed constants' },
  { key: 'lookup',   label: 'Class Lookup',  icon: BookOpen,         title: 'Fetch all constants declared in a specific class' },
  { key: 'diff',     label: 'Diff',          icon: GitCompareArrows, title: 'Compare constants between two project versions' },
  { key: 'upload',   label: 'Upload',        icon: UploadIcon,       title: 'Index a .class file, JAR, or config file' },
  { key: 'versions', label: 'Versions',      icon: GitBranch,        title: 'Manage project versions: sync, finalize, delete units' },
] as const

type TabKey = typeof TABS[number]['key']


function App() {
  const [tab, setTab] = useState<TabKey>('search')
  const { data, isLoading, error, hasSearched, search } = useSearch()
  const { isAuthenticated, authRequired, backendAvailable, canAccess, signIn, signOut, authFetch } = useAuth()
  const [showLoginModal, setShowLoginModal] = useState(false)
  const [lastTerm, setLastTerm] = useState('')
  const resultsRef = useRef<HTMLDivElement>(null)

  return (
    <div className="min-h-screen flex flex-col">
      {/* Backend unavailable banner */}
      {!backendAvailable && (
        <div className="flex items-center gap-3 px-4 py-2.5 bg-destructive/10 border-b border-destructive/30 text-sm text-destructive">
          <ServerCrash className="h-4 w-4 shrink-0" />
          <span className="font-medium">Backend unavailable</span>
          <span className="text-destructive/80">— The server could not be reached. Search, upload, and other features may not work.</span>
        </div>
      )}

      {/* Login modal */}
      {showLoginModal && (
        <LoginModal
          signIn={signIn}
          onSuccess={() => setShowLoginModal(false)}
          onClose={() => setShowLoginModal(false)}
        />
      )}

      {/* Header */}
      <header className="border-b border-border bg-card/30 backdrop-blur-sm sticky top-0 z-10">
        <div className="max-w-5xl mx-auto px-4 sm:px-6 h-14 flex items-center justify-between">
          <div className="flex items-center gap-2.5">
            <div className="h-8 w-8 rounded-lg bg-primary/10 flex items-center justify-center">
              <Database className="h-4 w-4 text-primary" />
            </div>
            <span className="font-semibold text-sm tracking-tight">Constant Tracker</span>
          </div>
          <div className="flex items-center gap-3">
            {authRequired && (
              isAuthenticated ? (
                <button
                  onClick={() => signOut()}
                  title="Sign out"
                  className="flex items-center gap-1.5 text-xs text-muted-foreground hover:text-foreground transition-colors"
                >
                  <LogOut className="h-4 w-4" />
                  <span className="hidden sm:inline">Sign out</span>
                </button>
              ) : (
                <button
                  onClick={() => setShowLoginModal(true)}
                  title="Sign in"
                  className="flex items-center gap-1.5 text-xs font-medium text-primary hover:underline transition-colors"
                >
                  <LogIn className="h-4 w-4" />
                  <span className="hidden sm:inline">Sign in</span>
                </button>
              )
            )}
            <a
              href="https://github.com/gabriel-glodean/constants_tracker"
              target="_blank"
              rel="noopener noreferrer"
              className="text-muted-foreground hover:text-foreground transition-colors"
            >
              <GithubIcon className="h-5 w-5" />
            </a>
          </div>
        </div>
      </header>

      {/* Tabs */}
      <nav className="border-b border-border bg-card/40 overflow-x-auto">
        <div className="max-w-5xl mx-auto px-4 sm:px-6 flex gap-1 h-12 min-w-max sm:min-w-0">
          {TABS.map(({ key, label, icon: Icon, title }, i) => (
            <Fragment key={key}>
              {i === 3 && (
                <div className="w-px bg-border my-3 mx-1" />
              )}
              <button
                title={title}
                className={`flex items-center gap-1.5 px-3 sm:px-4 h-10 mt-1 rounded-t-lg font-medium text-sm transition-colors ${tab === key ? 'bg-background text-primary border-x border-t border-border' : 'text-muted-foreground hover:text-foreground'}`}
                onClick={() => setTab(key)}
              >
                <Icon className="h-4 w-4 shrink-0" />
                <span className="hidden sm:inline">{label}</span>
              </button>
            </Fragment>
          ))}
        </div>
      </nav>

      {/* Main content */}
      <main className="flex-1">
        {tab === 'search' && (
          <>
            {/* Hero / Search section */}
            <section className="max-w-5xl mx-auto px-4 sm:px-6 pt-8 sm:pt-16 pb-10">
              <div className="text-center mb-10">
                <h1 className="text-2xl sm:text-4xl font-bold tracking-tight mb-3">
                  Search Constants
                </h1>
                <p className="text-muted-foreground text-base max-w-lg mx-auto">
                  Full-text fuzzy search across all indexed Java constant values.
                  Find strings, SQL queries, URLs, config keys, and more.
                </p>
              </div>
              <div className="max-w-2xl mx-auto">
                <SearchForm
                  onSearch={params => {
                    setLastTerm(params.term)
                    search(params)
                    setTimeout(() => resultsRef.current?.scrollIntoView({ behavior: 'smooth', block: 'start' }), 150)
                  }}
                  isLoading={isLoading}
                />
              </div>
            </section>
            {/* Results section */}
            <section ref={resultsRef} className="max-w-5xl mx-auto px-4 sm:px-6 pb-16">
              {error && (
                <div className="flex items-center gap-3 p-4 rounded-xl border border-destructive/50 bg-destructive/5 text-sm">
                  <AlertCircle className="h-5 w-5 text-destructive shrink-0" />
                  <div>
                    <p className="font-medium text-destructive">Search failed</p>
                    <p className="text-muted-foreground mt-0.5">{error}</p>
                  </div>
                </div>
              )}
              {isLoading && (
                <div className="flex flex-col items-center justify-center py-16">
                  <div className="h-8 w-8 border-2 border-primary/30 border-t-primary rounded-full animate-spin mb-4" />
                  <p className="text-sm text-muted-foreground">Searching constants…</p>
                </div>
              )}
              {!isLoading && !error && data && (
                <ResultsTable data={data} searchTerm={lastTerm} />
              )}
              {!hasSearched && !isLoading && (
                <div className="text-center py-16">
                  <p className="text-sm text-muted-foreground">
                    Enter a search term above to get started.
                  </p>
                  <div className="flex flex-wrap items-center justify-center gap-2 mt-4">
                    {['SELECT', 'log4j', 'http://', '.properties', 'ERROR'].map(example => (
                      <span
                        key={example}
                        className="px-2.5 py-1 rounded-md bg-secondary text-xs text-muted-foreground font-mono"
                      >
                        {example}
                      </span>
                    ))}
                  </div>
                </div>
              )}
            </section>
          </>
        )}
        {tab === 'diff' && (
          <section className="max-w-4xl mx-auto px-4 sm:px-6 pt-8 sm:pt-16 pb-16">
            <h1 className="text-2xl font-bold mb-6">Version Diff</h1>
            <DiffViewer />
          </section>
        )}
        {tab === 'upload' && (
          <section className="max-w-2xl mx-auto px-4 sm:px-6 pt-8 sm:pt-16 pb-16">
            <h1 className="text-2xl font-bold mb-6">Upload Class, JAR, or Config</h1>
            {!canAccess && (
              <div className="flex items-center gap-3 p-4 mb-6 rounded-xl border border-destructive/50 bg-destructive/5 text-sm">
                <Lock className="h-4 w-4 text-destructive shrink-0" />
                <p className="text-destructive">Sign in to use this feature.</p>
                <button
                  onClick={() => setShowLoginModal(true)}
                  className="ml-auto text-xs font-medium text-primary hover:underline shrink-0"
                >
                  Sign in
                </button>
              </div>
            )}
            <div className={!canAccess ? 'pointer-events-none opacity-40 select-none' : ''}>
              <UploadForm authFetch={authFetch} />
            </div>
          </section>
        )}
        {tab === 'lookup' && (
          <section className="max-w-2xl mx-auto px-4 sm:px-6 pt-8 sm:pt-16 pb-16">
            <h1 className="text-2xl font-bold mb-6">Class Constants Lookup</h1>
            <ClassLookupForm />
          </section>
        )}
        {tab === 'versions' && (
          <section className="max-w-2xl mx-auto px-4 sm:px-6 pt-8 sm:pt-16 pb-16">
            <h1 className="text-2xl font-bold mb-6">Version Manager</h1>
            {!canAccess && (
              <div className="flex items-center gap-3 p-4 mb-6 rounded-xl border border-destructive/50 bg-destructive/5 text-sm">
                <Lock className="h-4 w-4 text-destructive shrink-0" />
                <p className="text-destructive">Sign in to use this feature.</p>
                <button
                  onClick={() => setShowLoginModal(true)}
                  className="ml-auto text-xs font-medium text-primary hover:underline shrink-0"
                >
                  Sign in
                </button>
              </div>
            )}
            <div className={!canAccess ? 'pointer-events-none opacity-40 select-none' : ''}>
              <VersionManager authFetch={authFetch} />
            </div>
          </section>
        )}
      </main>

      {/* Footer */}
      <footer className="border-t border-border py-6">
        <div className="max-w-5xl mx-auto px-4 sm:px-6 flex flex-col sm:flex-row items-center justify-between gap-2 text-xs text-muted-foreground">
          <span>Constant Tracker v0.1.0</span>
          <span>Powered by Solr + Spring WebFlux</span>
        </div>
      </footer>
    </div>
  )
}

export default App
