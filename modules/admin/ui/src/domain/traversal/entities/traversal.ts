import { Timestamp } from '@/domain/shared'
import { TraversalId, createTraversalId } from '../value-objects/traversal-id'
import { DagHash, createDagHash } from '../value-objects/dag-hash'

/**
 * TraversalStatus - Current state of a traversal
 */
export type TraversalStatus =
  | 'pending'
  | 'running'
  | 'completed'
  | 'failed'
  | 'cancelled'
  | 'timeout'

/**
 * Traversal - An instance of an event flowing through a DAG
 */
export interface Traversal {
  readonly id: TraversalId
  readonly dagHash: DagHash
  readonly status: TraversalStatus
  readonly currentNode?: string
  readonly startedAt: Timestamp
  readonly completedAt?: Timestamp
  readonly error?: string
}

export function createTraversal(params: {
  id: string
  dagHash: string
  status: TraversalStatus
  currentNode?: string
  startedAt: string
  completedAt?: string
  error?: string
}): Traversal {
  return {
    id: createTraversalId(params.id),
    dagHash: createDagHash(params.dagHash),
    status: params.status,
    currentNode: params.currentNode,
    startedAt: params.startedAt as Timestamp,
    completedAt: params.completedAt as Timestamp | undefined,
    error: params.error,
  }
}

export function isTraversalActive(traversal: Traversal): boolean {
  return traversal.status === 'pending' || traversal.status === 'running'
}

export function isTraversalTerminal(traversal: Traversal): boolean {
  return !isTraversalActive(traversal)
}

export function getTraversalDuration(traversal: Traversal): number | null {
  if (!traversal.completedAt) return null
  const start = new Date(traversal.startedAt).getTime()
  const end = new Date(traversal.completedAt).getTime()
  return end - start
}

export function getTraversalStatusColor(status: TraversalStatus): string {
  switch (status) {
    case 'pending':
      return 'text-yellow-500'
    case 'running':
      return 'text-blue-500'
    case 'completed':
      return 'text-green-500'
    case 'failed':
      return 'text-red-500'
    case 'cancelled':
      return 'text-gray-500'
    case 'timeout':
      return 'text-orange-500'
  }
}
