import { Timestamp } from '@/domain/shared'

/**
 * Breadcrumb - A trail of events leading up to an issue (like Sentry breadcrumbs)
 */
export interface Breadcrumb {
  readonly timestamp: Timestamp
  readonly category: BreadcrumbCategory
  readonly message: string
  readonly level: BreadcrumbLevel
  readonly data?: Record<string, unknown>
}

export type BreadcrumbCategory =
  | 'http'
  | 'navigation'
  | 'user'
  | 'console'
  | 'dag'
  | 'node'
  | 'error'
  | 'info'
  | 'query'
  | 'transaction'

export type BreadcrumbLevel = 'fatal' | 'error' | 'warning' | 'info' | 'debug'

export const BREADCRUMB_ICONS: Record<BreadcrumbCategory, string> = {
  http: 'Globe',
  navigation: 'ArrowRight',
  user: 'User',
  console: 'Terminal',
  dag: 'GitBranch',
  node: 'Server',
  error: 'AlertCircle',
  info: 'Info',
  query: 'Database',
  transaction: 'Activity',
}

export function createBreadcrumb(params: {
  timestamp: string
  category: BreadcrumbCategory
  message: string
  level?: BreadcrumbLevel
  data?: Record<string, unknown>
}): Breadcrumb {
  return {
    timestamp: params.timestamp as Timestamp,
    category: params.category,
    message: params.message,
    level: params.level ?? 'info',
    data: params.data,
  }
}
