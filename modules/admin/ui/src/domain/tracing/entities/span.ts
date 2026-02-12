import { Timestamp } from '@/domain/shared'
import { SpanId, createSpanId } from '../value-objects/span-id'
import { TraceId, createTraceId } from '../value-objects/trace-id'

/**
 * Span - A unit of work within a trace (OpenTelemetry compatible)
 */
export interface Span {
  readonly spanId: SpanId
  readonly traceId: TraceId
  readonly parentSpanId?: SpanId
  readonly operationName: string
  readonly serviceName: string
  readonly startTime: Timestamp
  readonly endTime: Timestamp
  readonly duration: number // milliseconds
  readonly status: SpanStatus
  readonly tags: Record<string, string>
  readonly logs: SpanLog[]
}

export type SpanStatus = 'ok' | 'error' | 'unset'

export interface SpanLog {
  readonly timestamp: Timestamp
  readonly fields: Record<string, unknown>
}

export function createSpan(params: {
  spanId: string
  traceId: string
  parentSpanId?: string
  operationName: string
  serviceName: string
  startTime: string
  endTime: string
  duration: number
  status?: SpanStatus
  tags?: Record<string, string>
  logs?: SpanLog[]
}): Span {
  return {
    spanId: createSpanId(params.spanId),
    traceId: createTraceId(params.traceId),
    parentSpanId: params.parentSpanId ? createSpanId(params.parentSpanId) : undefined,
    operationName: params.operationName,
    serviceName: params.serviceName,
    startTime: params.startTime as Timestamp,
    endTime: params.endTime as Timestamp,
    duration: params.duration,
    status: params.status ?? 'unset',
    tags: params.tags ?? {},
    logs: params.logs ?? [],
  }
}

/**
 * Calculate the depth of a span in the tree
 */
export function getSpanDepth(span: Span, spans: Span[]): number {
  let depth = 0
  let current: Span | undefined = span

  while (current?.parentSpanId) {
    current = spans.find((s) => s.spanId === current!.parentSpanId)
    depth++
  }

  return depth
}

/**
 * Get child spans
 */
export function getChildSpans(span: Span, spans: Span[]): Span[] {
  return spans.filter((s) => s.parentSpanId === span.spanId)
}

export function formatDuration(ms: number): string {
  if (ms < 1) return '<1ms'
  if (ms < 1000) return `${Math.round(ms)}ms`
  if (ms < 60000) return `${(ms / 1000).toFixed(2)}s`
  return `${(ms / 60000).toFixed(2)}m`
}
