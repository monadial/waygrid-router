/**
 * NodeService - Service name within a component
 * Mirrors: com.monadial.waygrid.common.domain.model.node.NodeService
 */
export type NodeService = string & { readonly __brand: unique symbol }

export function createNodeService(value: string): NodeService {
  if (!value || value.trim().length === 0) {
    throw new Error('NodeService cannot be empty')
  }
  return value.trim().toLowerCase() as NodeService
}

export function isValidNodeService(value: string): value is NodeService {
  return value.length > 0 && /^[a-z0-9-]+$/.test(value)
}
