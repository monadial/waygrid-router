/**
 * DagHash - Content-based hash of DAG structure
 * Mirrors: com.monadial.waygrid.common.domain.model.routing.dag.DagHash
 */
export type DagHash = string & { readonly __dagHash: unique symbol }

export function createDagHash(value: string): DagHash {
  if (!value || value.trim().length === 0) {
    throw new Error('DagHash cannot be empty')
  }
  return value as DagHash
}

export function isValidDagHash(value: string): value is DagHash {
  return value.length > 0
}
