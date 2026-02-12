/**
 * Timestamp - ISO 8601 timestamp value object
 * Mirrors: com.monadial.waygrid.common.domain.value.InstantValue
 */
export type Timestamp = string & { readonly __brand: unique symbol }

export function isValidTimestamp(value: string): value is Timestamp {
  const date = new Date(value)
  return !isNaN(date.getTime())
}

export function createTimestamp(value: string | Date): Timestamp {
  if (value instanceof Date) {
    return value.toISOString() as Timestamp
  }
  if (!isValidTimestamp(value)) {
    throw new Error(`Invalid timestamp: ${value}`)
  }
  return value
}

export function now(): Timestamp {
  return new Date().toISOString() as Timestamp
}
