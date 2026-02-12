import { Spec } from '@/domain/traversal'
import { OriginApiClient } from '@/infrastructure/api/origin-api'
import { TraversalMapper } from '@/infrastructure/mappers/traversal-mapper'

export interface IngestResult {
  traversalId: string
  dagHash: string
}

/**
 * Command: Ingest Event
 * Submits a routing specification to create a new traversal
 */
export async function ingestEvent(spec: Spec): Promise<IngestResult> {
  const requestDTO = {
    graph: TraversalMapper.toSpecDTO(spec),
  }

  const responseDTO = await OriginApiClient.ingest(requestDTO)
  return TraversalMapper.fromIngestResponse(responseDTO)
}
