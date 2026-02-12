import { ULID, isValidULID } from '@/domain/shared'

/**
 * NodeId - ULID-based unique node identifier
 * Mirrors: com.monadial.waygrid.common.domain.model.node.NodeId
 */
export type NodeId = ULID & { readonly __nodeId: unique symbol }

export function createNodeId(value: string): NodeId {
  if (!isValidULID(value)) {
    throw new Error(`Invalid NodeId: ${value}`)
  }
  return value as NodeId
}

export function isValidNodeId(value: string): value is NodeId {
  return isValidULID(value)
}
