import { ULID, isValidULID } from '@/domain/shared'

/**
 * EventId - Unique identifier for a traced event
 */
export type EventId = ULID & { readonly __eventId: unique symbol }

export function createEventId(value: string): EventId {
  if (!isValidULID(value)) {
    throw new Error(`Invalid EventId: ${value}`)
  }
  return value as EventId
}

/**
 * Short event ID for display (first 8 chars)
 */
export function shortEventId(id: EventId): string {
  return id.slice(0, 8)
}
