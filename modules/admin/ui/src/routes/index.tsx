import { createRootRoute, createRoute, createRouter, Outlet } from '@tanstack/react-router'

import { AppLayout } from '@/presentation/components/layout/app-layout'
import { DashboardPage } from '@/presentation/pages/dashboard'
import { TopologyPage } from '@/presentation/pages/topology'
import { TraversalsPage } from '@/presentation/pages/traversals'
import { DagsPage } from '@/presentation/pages/dags'
import { DagEditorPage } from '@/presentation/pages/dag-editor'
import { SchedulerPage } from '@/presentation/pages/scheduler'
import { IamPage } from '@/presentation/pages/iam'
import { HistoryPage } from '@/presentation/pages/history'
import { SecretsPage } from '@/presentation/pages/secrets'
import { BillingPage } from '@/presentation/pages/billing'
import { SettingsPage } from '@/presentation/pages/settings'
import { IssuesPage } from '@/presentation/pages/issues'
import { TracesPage } from '@/presentation/pages/traces'
import { StatsPage } from '@/presentation/pages/stats'

// Root route
const rootRoute = createRootRoute({
  component: () => (
    <AppLayout>
      <Outlet />
    </AppLayout>
  ),
})

// Dashboard (index route)
const dashboardRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: '/',
  component: DashboardPage,
})

// Issues (Sentry-style error tracking)
const issuesRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: '/issues',
  component: IssuesPage,
})

// DAGs
const dagsRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: '/dags',
  component: DagsPage,
})

// DAG Editor
const dagEditorRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: '/dags/editor',
  component: DagEditorPage,
})

// Traces (distributed tracing)
const tracesRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: '/traces',
  component: TracesPage,
})

// History (event timeline)
const historyRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: '/history',
  component: HistoryPage,
})

// Topology
const topologyRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: '/topology',
  component: TopologyPage,
})

// Stats
const statsRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: '/stats',
  component: StatsPage,
})

// Traversals (legacy, redirect or keep for compatibility)
const traversalsRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: '/traversals',
  component: TraversalsPage,
})

// Scheduler
const schedulerRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: '/scheduler',
  component: SchedulerPage,
})

// IAM
const iamRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: '/iam',
  component: IamPage,
})

// Secrets
const secretsRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: '/secrets',
  component: SecretsPage,
})

// Billing
const billingRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: '/billing',
  component: BillingPage,
})

// Settings
const settingsRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: '/settings',
  component: SettingsPage,
})

// Build route tree (ordered by navigation priority)
const routeTree = rootRoute.addChildren([
  dashboardRoute,
  issuesRoute,
  dagsRoute,
  dagEditorRoute,
  tracesRoute,
  historyRoute,
  topologyRoute,
  statsRoute,
  traversalsRoute,
  schedulerRoute,
  iamRoute,
  secretsRoute,
  billingRoute,
  settingsRoute,
])

// Create router
export const router = createRouter({ routeTree })

// Type declarations for type-safe routing
declare module '@tanstack/react-router' {
  interface Register {
    router: typeof router
  }
}
