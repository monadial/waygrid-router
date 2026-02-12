import { Spec } from '@/domain/traversal'
import { ingestEvent, IngestResult } from './queries/ingest-event'
import { OriginApiClient } from '@/infrastructure/api/origin-api'

/**
 * Traversal Application Service
 * Orchestrates traversal-related use cases
 */
export const TraversalService = {
  /**
   * Ingest a new event with routing specification
   */
  async ingest(spec: Spec): Promise<IngestResult> {
    return ingestEvent(spec)
  },

  /**
   * Check if the origin service is healthy
   */
  async isOriginHealthy(): Promise<boolean> {
    return OriginApiClient.healthCheck()
  },
}
