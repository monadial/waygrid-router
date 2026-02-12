import { ULID, isValidULID } from '@/domain/shared'

/**
 * RouteId - ULID for route identification
 * Mirrors: com.monadial.waygrid.common.domain.model.routing.RouteId
 */
export type RouteId = ULID & { readonly __routeId: unique symbol }

export function createRouteId(value: string): RouteId {
  if (!isValidULID(value)) {
    throw new Error(`Invalid RouteId: ${value}`)
  }
  return value as RouteId
}
