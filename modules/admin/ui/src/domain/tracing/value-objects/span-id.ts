/**
 * SpanId - Identifier for a span within a trace
 * 16-character hex string (OpenTelemetry format)
 */
export type SpanId = string & { readonly __spanId: unique symbol }

const SPAN_ID_REGEX = /^[a-f0-9]{16}$/

export function createSpanId(value: string): SpanId {
  const normalized = value.toLowerCase()
  if (!SPAN_ID_REGEX.test(normalized)) {
    throw new Error(`Invalid SpanId: ${value}`)
  }
  return normalized as SpanId
}

export function isValidSpanId(value: string): value is SpanId {
  return SPAN_ID_REGEX.test(value.toLowerCase())
}

export function shortSpanId(id: SpanId): string {
  return id.slice(0, 8)
}
