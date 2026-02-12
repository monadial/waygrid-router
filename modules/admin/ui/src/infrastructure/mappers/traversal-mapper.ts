import {
  Spec,
  SpecNode,
  RepeatPolicy,
  RetryPolicy,
  DeliveryStrategy,
} from '@/domain/traversal'
import {
  SpecDTO,
  SpecNodeDTO,
  RepeatPolicyDTO,
  RetryPolicyDTO,
  DeliveryStrategyDTO,
  IngestResponseDTO,
} from '../api/origin-api'

/**
 * Mapper: Domain -> DTO (for API requests)
 * Mapper: DTO -> Domain (for API responses)
 */
export const TraversalMapper = {
  /**
   * Map domain Spec to DTO for API request
   */
  toSpecDTO(spec: Spec): SpecDTO {
    return {
      entryPoints: spec.entryPoints.map(this.toSpecNodeDTO),
      repeatPolicy: this.toRepeatPolicyDTO(spec.repeatPolicy),
    }
  },

  toSpecNodeDTO(node: SpecNode): SpecNodeDTO {
    return {
      type: node.type,
      address: node.address,
      parameters: node.parameters,
      retryPolicy: TraversalMapper.toRetryPolicyDTO(node.retryPolicy),
      deliveryStrategy: TraversalMapper.toDeliveryStrategyDTO(node.deliveryStrategy),
      onSuccess: node.onSuccess?.map(TraversalMapper.toSpecNodeDTO) ?? null,
      onFailure: node.onFailure?.map(TraversalMapper.toSpecNodeDTO) ?? null,
      onConditions: node.onConditions?.map((edge) => ({
        condition: edge.condition,
        to: TraversalMapper.toSpecNodeDTO(edge.to),
      })),
    }
  },

  toRepeatPolicyDTO(policy: RepeatPolicy): RepeatPolicyDTO {
    return policy as RepeatPolicyDTO
  },

  toRetryPolicyDTO(policy: RetryPolicy): RetryPolicyDTO {
    if (policy.type === 'NoRetry') {
      return { type: 'None' }
    }
    return policy as RetryPolicyDTO
  },

  toDeliveryStrategyDTO(strategy: DeliveryStrategy): DeliveryStrategyDTO {
    return strategy as DeliveryStrategyDTO
  },

  /**
   * Map IngestResponseDTO to domain types
   */
  fromIngestResponse(dto: IngestResponseDTO): {
    traversalId: string
    dagHash: string
  } {
    return {
      traversalId: dto.traversalId,
      dagHash: dto.dagHash,
    }
  },
}
