import { Timestamp } from '@/domain/shared'
import { ProjectId } from '@/domain/project'
import { EventId, createEventId, shortEventId } from '../value-objects/event-id'
import { TraceId, createTraceId } from '../value-objects/trace-id'
import { Severity } from '../value-objects/severity'
import { Breadcrumb } from './breadcrumb'
import { Span } from './span'

/**
 * TracedEvent - A captured event in the system (like Sentry issues)
 * This represents a DAG traversal, error, or significant system event
 */
export interface TracedEvent {
  readonly id: EventId
  readonly projectId: ProjectId
  readonly traceId?: TraceId
  readonly title: string
  readonly message?: string
  readonly severity: Severity
  readonly status: EventStatus
  readonly source: EventSource
  readonly timestamp: Timestamp
  readonly environment: string
  readonly tags: Record<string, string>
  readonly breadcrumbs: Breadcrumb[]
  readonly spans: Span[]
  readonly context: EventContext
  readonly fingerprint: string[]
  readonly firstSeen: Timestamp
  readonly lastSeen: Timestamp
  readonly count: number
}

export type EventStatus = 'unresolved' | 'resolved' | 'ignored' | 'archived'

export interface EventSource {
  readonly type: 'dag' | 'node' | 'system' | 'user'
  readonly name: string
  readonly version?: string
}

export interface EventContext {
  readonly dag?: DagContext
  readonly node?: NodeContext
  readonly request?: RequestContext
  readonly user?: UserContext
  readonly extra?: Record<string, unknown>
}

export interface DagContext {
  readonly dagHash: string
  readonly entryPoint: string
  readonly currentNode?: string
  readonly traversalId: string
}

export interface NodeContext {
  readonly nodeId: string
  readonly service: string
  readonly component: string
  readonly region: string
}

export interface RequestContext {
  readonly method: string
  readonly url: string
  readonly headers?: Record<string, string>
  readonly body?: unknown
}

export interface UserContext {
  readonly id?: string
  readonly email?: string
  readonly username?: string
}

export function createTracedEvent(params: {
  id: string
  projectId: string
  traceId?: string
  title: string
  message?: string
  severity: Severity
  status?: EventStatus
  source: EventSource
  timestamp: string
  environment?: string
  tags?: Record<string, string>
  breadcrumbs?: Breadcrumb[]
  spans?: Span[]
  context?: EventContext
  fingerprint?: string[]
  firstSeen?: string
  lastSeen?: string
  count?: number
}): TracedEvent {
  return {
    id: createEventId(params.id),
    projectId: params.projectId as ProjectId,
    traceId: params.traceId ? createTraceId(params.traceId) : undefined,
    title: params.title,
    message: params.message,
    severity: params.severity,
    status: params.status ?? 'unresolved',
    source: params.source,
    timestamp: params.timestamp as Timestamp,
    environment: params.environment ?? 'production',
    tags: params.tags ?? {},
    breadcrumbs: params.breadcrumbs ?? [],
    spans: params.spans ?? [],
    context: params.context ?? {},
    fingerprint: params.fingerprint ?? [params.title],
    firstSeen: (params.firstSeen ?? params.timestamp) as Timestamp,
    lastSeen: (params.lastSeen ?? params.timestamp) as Timestamp,
    count: params.count ?? 1,
  }
}

export function getEventDisplayId(event: TracedEvent): string {
  return shortEventId(event.id)
}

export function isEventError(event: TracedEvent): boolean {
  return event.severity === 'error' || event.severity === 'fatal'
}
