# ServProWare — Admin Panel

The office web app for ServProWare: book appointments, plan and optimize each day's routes on a Google Map, text customers their arrival windows, and manage customers, tasks, routes, vehicles, technicians, settings and users. Technicians can also sign in here to see their own schedule. Its scope matches the HTML prototype `../servicepros-final-ui.html`.

It talks to the Spring Boot backend (API documentation: Swagger UI at http://localhost:9091/swagger-ui.html).

## Stack

| | |
|---|---|
| Framework | Next.js 16 (App Router, `src/`), React 19, TypeScript 5.9 |
| Styling | Tailwind CSS 4 (brand colours as CSS variables in `src/app/globals.css`) |
| State | Zustand 5 (auth session persisted to `localStorage`) |
| Forms | react-hook-form 7, Zod 4, @hookform/resolvers 5 |
| Tables | @tanstack/react-table 9 with server-side pagination |
| Maps | @vis.gl/react-google-maps (Advanced Markers, polylines, InfoWindows) |
| Misc | axios, lucide-react, react-hot-toast, clsx + tailwind-merge |

## Getting started

```bash
cd admin
npm install
npm run dev                     # http://localhost:3001  (uses .env.development)
```

The backend must be running at `NEXT_PUBLIC_API_BASE_URL` (local: `http://localhost:9091`), and its CORS settings must allow `http://localhost:3001`.

Log in with an admin or technician account from the database. Admins land on `/dashboard`; technicians land on `/tech`.

### Settings files

| File | Used by | Contents |
|---|---|---|
| `.env.development` | `npm run dev` (your PC) | local backend URL, Maps key |
| `.env.production` | `npm run build` / `npm start` (live server) | live URL, `/admin` base path, Maps key |
| `.env.example` | nobody (template) | list of variables, safe to commit |

Next.js picks the right file automatically. The real files are git-ignored.

### Environment variables

| Variable | Default | Purpose |
|---|---|---|
| `NEXT_PUBLIC_API_BASE_URL` | `http://localhost:9091` | Backend base URL |
| `NEXT_PUBLIC_BASE_PATH` | _(empty)_ | Sub-path when served behind a reverse proxy, e.g. `/admin`. Leave empty locally. |
| `NEXT_PUBLIC_GOOGLE_MAPS_API_KEY` | _(empty)_ | Maps JavaScript API key. Without it the maps show a placeholder; stop lists and "Google Maps" links still work. |

`NEXT_PUBLIC_*` values are built into the app, so rebuild after you change them.

### Production (behind Apache at `https://<domain>/admin`)

```bash
# values come from .env.production (live URL + NEXT_PUBLIC_BASE_PATH=/admin)
npm ci
npm run build
npx next start -p 3102
```

## Scripts

| Command | What it does |
|---|---|
| `npm run dev` | Dev server on port 3001 |
| `npm run build` | Production build (makes no API calls at build time) |
| `npm start` | Serves the production build on port 3001 |
| `npm run lint` | ESLint |
| `npm run typecheck` | `tsc --noEmit` |

## Roles and menus

| Role | Lands on | Pages |
|---|---|---|
| Super Admin | `/super` | **Cities** at `/super/cities` (not in the menu — open the URL directly; the one city list every admin picks from) · **Admins**: create, edit, enable/disable, reset password, delete (soft delete — data is kept, the admin and their technicians can no longer sign in) |
| Admin | `/dashboard` | **Overview:** Dashboard · **Operations:** Schedule Appointment, Day Plan & Map · **Masters:** Customers, Task Master, Route Master · **System:** Notifications, Settings |
| Technician | `/tech` | **My Work:** My Schedule, Route Map · **System:** Notifications |

Every Admin works in a separate space: they only see their own technicians, customers, routes, vehicles,
tasks, appointments, notifications and settings (enforced by the backend). Technicians belong to the admin
who created them.

## Project layout

```
src/
  app/
    login/                 sign-in page (sends each role to its home page)
    forgot-password/       email a 6-digit code, then set a new password
    (admin)/               admin pages (AuthGuard role=ADMIN + AppShell)
      page.tsx             redirects to /dashboard
      appointments/        date filter, book/edit modal (customers grouped by city → auto route), SMS, delete
      schedule/            Day Plan & Map: optimize, allocate & notify, numbered stop lists, refreshes every 20 s
      customers/ tasks/ routes/
      dashboard/ notifications/ settings/
    super/                 Super Admin pages (AuthGuard role=SUPER_ADMIN): Admins, cities/ (City Master)
    tech/                  technician pages (AuthGuard role=TECHNICIAN): My Schedule, Route Map, Notifications
  components/
    ui/                    Modal, ConfirmDialog, DataTable, StatusBadge, PageHeader, FormField, Select,
                           EmptyState, Spinner, Pagination, Button, Card
    layout/                AppShell (sidebar + top bar), AuthGuard
    appointments/ schedule/ masters/ tech/
  hooks/                   useApi (fetch hook), useDebounce, useInterval, useHydrated
  lib/
    api.ts                 axios + request<T>(): Bearer token, one shared refresh on 401, retry, logout
    services.ts            typed functions for every /api/admin/**, /api/tech/** and /api/auth/** call
    format.ts              "HH:mm" → "8:30 AM", dates in America/Chicago
    enums.ts               enum labels (EN_ROUTE → "En Route"), notification icons, badge colours
    nav.ts schemas.ts utils.ts
  store/                   auth.ts (session), techDate.ts (date shared by My Schedule and Route Map)
  types/api.ts             TypeScript types that mirror the backend API
```

## Notes

- **Forgot password.** `/forgot-password` (linked from the login page): admins and technicians get a 6-digit code by email (valid 10 minutes, 5 tries, resend after 60 s) and set a new password; all their sessions are signed out. An unregistered email shows "Email is not registered..!". Not available for the Super Admin.
- **Admin accounts.** The Super Admin records company name, contact person, mobile and a credit card per admin. Only the card brand, last 4 digits, expiry and name on card are stored — never the full number or CVV.
- **Auth.** Tokens live in `localStorage`, so the auth check runs in the browser. On a 401 the client calls `/api/auth/refresh` once (concurrent requests share it) and retries; if that fails, the user is sent to `/login`.
- **Errors.** The backend's `message` is shown as a toast, and `fieldErrors` are attached to the matching form fields.
- **Day Plan.** Stops are numbered in visit order with one colour per route; a dashed line means the plan isn't optimized yet. ⌂ marks the depot. Routes with more than 10 stops get several Google Maps links ("Part 1/2", "Part 2/2").
- **Cities.** One shared list managed by the Super Admin under Cities (`/api/super/cities`); admins read it (`/api/admin/cities`) in every City select and can't change it. Renaming a city renames it on its customers and routes; a city in use can't be deleted.
