import { RepeatPolicy } from '../value-objects/repeat-policy'
import { RetryPolicy } from '../value-objects/retry-policy'
import { DeliveryStrategy } from '../value-objects/delivery-strategy'

/**
 * Spec - Routing specification for creating a DAG
 * Mirrors: com.monadial.waygrid.origin.http.http.resource.v1.IngestResource.EndpointRequest
 */
export interface Spec {
  readonly entryPoints: readonly SpecNode[]
  readonly repeatPolicy: RepeatPolicy
}

export interface SpecNode {
  readonly type: SpecNodeType
  readonly address: string
  readonly parameters?: Record<string, unknown>
  readonly retryPolicy: RetryPolicy
  readonly deliveryStrategy: DeliveryStrategy
  readonly onSuccess?: readonly SpecNode[]
  readonly onFailure?: readonly SpecNode[]
  readonly onConditions?: readonly ConditionalEdge[]
}

export type SpecNodeType = 'standard' | 'fork' | 'join'

export interface ConditionalEdge {
  readonly condition: string
  readonly to: SpecNode
}

export function createSpec(entryPoints: SpecNode[], repeatPolicy: RepeatPolicy): Spec {
  return { entryPoints, repeatPolicy }
}

export function createSpecNode(params: {
  address: string
  type?: SpecNodeType
  parameters?: Record<string, unknown>
  retryPolicy?: RetryPolicy
  deliveryStrategy?: DeliveryStrategy
  onSuccess?: SpecNode[]
  onFailure?: SpecNode[]
  onConditions?: ConditionalEdge[]
}): SpecNode {
  return {
    type: params.type ?? 'standard',
    address: params.address,
    parameters: params.parameters,
    retryPolicy: params.retryPolicy ?? { type: 'NoRetry' },
    deliveryStrategy: params.deliveryStrategy ?? { type: 'Immediate' },
    onSuccess: params.onSuccess,
    onFailure: params.onFailure,
    onConditions: params.onConditions,
  }
}
