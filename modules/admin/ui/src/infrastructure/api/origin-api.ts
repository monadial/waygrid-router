import { originApi } from './client'

/**
 * DTO types for Origin HTTP service
 */
export interface IngestRequestDTO {
  graph: SpecDTO
}

export interface IngestResponseDTO {
  traversalId: string
  dagHash: string
}

export interface SpecDTO {
  entryPoints: SpecNodeDTO[]
  repeatPolicy: RepeatPolicyDTO
}

export interface SpecNodeDTO {
  type: string
  address: string
  parameters?: Record<string, unknown>
  retryPolicy: RetryPolicyDTO
  deliveryStrategy: DeliveryStrategyDTO
  onSuccess?: SpecNodeDTO[] | null
  onFailure?: SpecNodeDTO[] | null
  onConditions?: ConditionalEdgeDTO[]
}

export interface ConditionalEdgeDTO {
  condition: string
  to: SpecNodeDTO
}

export type RepeatPolicyDTO =
  | { type: 'NoRepeat' }
  | { type: 'Indefinitely'; every: string }
  | { type: 'Times'; every: string; times: number }
  | { type: 'Until'; every: string; until: string }

export type RetryPolicyDTO =
  | { type: 'None' }
  | { type: 'Linear'; delay: string; maxRetries: number }
  | { type: 'Exponential'; baseDelay: string; maxRetries: number }
  | { type: 'BoundedExponential'; baseDelay: string; maxDelay: string; maxRetries: number }

export type DeliveryStrategyDTO =
  | { type: 'Immediate' }
  | { type: 'ScheduleAfter'; delay: string }

/**
 * Origin HTTP API client
 */
export const OriginApiClient = {
  /**
   * Ingest a new event with routing specification
   */
  async ingest(request: IngestRequestDTO): Promise<IngestResponseDTO> {
    const response = await originApi.post<IngestResponseDTO>('/v1/ingest', request)
    return response.data
  },

  /**
   * Check if the service is healthy
   */
  async healthCheck(): Promise<boolean> {
    try {
      await originApi.get('/health-check')
      return true
    } catch {
      return false
    }
  },
}
