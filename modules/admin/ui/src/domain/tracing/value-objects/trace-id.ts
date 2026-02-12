/**
 * TraceId - Identifier for a distributed trace
 * 32-character hex string (OpenTelemetry format)
 */
export type TraceId = string & { readonly __traceId: unique symbol }

const TRACE_ID_REGEX = /^[a-f0-9]{32}$/

export function createTraceId(value: string): TraceId {
  const normalized = value.toLowerCase()
  if (!TRACE_ID_REGEX.test(normalized)) {
    throw new Error(`Invalid TraceId: ${value}`)
  }
  return normalized as TraceId
}

export function isValidTraceId(value: string): value is TraceId {
  return TRACE_ID_REGEX.test(value.toLowerCase())
}

export function shortTraceId(id: TraceId): string {
  return id.slice(0, 8)
}
