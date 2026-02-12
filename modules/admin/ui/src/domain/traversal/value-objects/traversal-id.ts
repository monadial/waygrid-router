import { ULID, isValidULID } from '@/domain/shared'

/**
 * TraversalId - ULID tracking event traversal through DAG
 * Mirrors: com.monadial.waygrid.common.domain.model.routing.TraversalId
 */
export type TraversalId = ULID & { readonly __traversalId: unique symbol }

export function createTraversalId(value: string): TraversalId {
  if (!isValidULID(value)) {
    throw new Error(`Invalid TraversalId: ${value}`)
  }
  return value as TraversalId
}

export function isValidTraversalId(value: string): value is TraversalId {
  return isValidULID(value)
}
