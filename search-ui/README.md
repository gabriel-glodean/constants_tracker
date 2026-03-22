# Constant Tracker — Search UI

Minimal React + TypeScript search interface for the Constant Tracker fuzzy search API.

## Prerequisites

- **Node.js 18+** and npm
- The Spring Boot backend running on `http://localhost:8080`  
  (see `constant-tracker-app/docker-compose.yml` to spin up Solr + Redis)

## Quick Start

```bash
cd search-ui
npm install
npm run dev        # starts Vite dev server on http://localhost:5173
```

The Vite dev server proxies `/search`, `/class`, and `/jar` requests to `http://localhost:8080`, so no CORS issues during development.

## Production Build

```bash
npm run build      # output in dist/
npm run preview    # preview the production build locally
```

## Stack

| Layer       | Technology                        |
|-------------|-----------------------------------|
| Framework   | React 19 + TypeScript             |
| Build       | Vite 8                            |
| Styling     | Tailwind CSS v4                   |
| Icons       | Lucide React                      |
| API         | Native `fetch()` (no extra deps)  |

## Project Structure

```
src/
├── api/
│   └── searchApi.ts        # typed API client for GET /search
├── components/
│   ├── SearchForm.tsx       # search input + advanced options
│   └── ResultsTable.tsx     # expandable results list
├── hooks/
│   └── useSearch.ts         # search state management hook
├── lib/
│   └── utils.ts             # cn() class-merge utility
├── App.tsx                  # main app shell
├── main.tsx                 # React entry point
└── index.css                # Tailwind v4 theme tokens
```
