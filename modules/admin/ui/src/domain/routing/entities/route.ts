import { Timestamp } from '@/domain/shared'
import { RouteId, createRouteId } from '../value-objects/route-id'
import { DagHash } from '@/domain/traversal'

/**
 * Route - A registered routing configuration
 */
export interface Route {
  readonly id: RouteId
  readonly name: string
  readonly description?: string
  readonly dagHash: DagHash
  readonly isActive: boolean
  readonly createdAt: Timestamp
  readonly updatedAt: Timestamp
}

export function createRoute(params: {
  id: string
  name: string
  description?: string
  dagHash: string
  isActive: boolean
  createdAt: string
  updatedAt: string
}): Route {
  return {
    id: createRouteId(params.id),
    name: params.name,
    description: params.description,
    dagHash: params.dagHash as DagHash,
    isActive: params.isActive,
    createdAt: params.createdAt as Timestamp,
    updatedAt: params.updatedAt as Timestamp,
  }
}
