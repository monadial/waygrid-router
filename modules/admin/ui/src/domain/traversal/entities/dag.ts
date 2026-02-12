import { DagHash, createDagHash } from '../value-objects/dag-hash'
import { RepeatPolicy } from '../value-objects/repeat-policy'
import { EdgeGuard } from '../value-objects/edge-guard'

/**
 * DagNode - A node in the DAG representing a service
 * Mirrors: com.monadial.waygrid.common.domain.model.routing.dag.Node
 */
export interface DagNode {
  readonly id: string
  readonly serviceAddress: string
  readonly nodeType: DagNodeType
  readonly parameters?: Record<string, unknown>
}

export type DagNodeType =
  | { readonly type: 'Standard' }
  | { readonly type: 'Fork'; readonly forkId: string }
  | { readonly type: 'Join'; readonly forkId: string; readonly joinMode: JoinMode; readonly timeout?: string }

export type JoinMode =
  | { readonly type: 'All' }
  | { readonly type: 'Any' }
  | { readonly type: 'AtLeast'; readonly count: number }

/**
 * DagEdge - Connection between nodes
 * Mirrors: com.monadial.waygrid.common.domain.model.routing.dag.Edge
 */
export interface DagEdge {
  readonly from: string
  readonly to: string
  readonly guard: EdgeGuard
}

/**
 * Dag - Directed Acyclic Graph for event routing
 * Mirrors: com.monadial.waygrid.common.domain.model.routing.dag.Dag
 */
export interface Dag {
  readonly hash: DagHash
  readonly entryPoint: string
  readonly repeatPolicy: RepeatPolicy
  readonly nodes: Record<string, DagNode>
  readonly edges: readonly DagEdge[]
  readonly timeout?: string
}

export function createDag(params: {
  hash: string
  entryPoint: string
  repeatPolicy: RepeatPolicy
  nodes: Record<string, DagNode>
  edges: DagEdge[]
  timeout?: string
}): Dag {
  return {
    hash: createDagHash(params.hash),
    entryPoint: params.entryPoint,
    repeatPolicy: params.repeatPolicy,
    nodes: params.nodes,
    edges: params.edges,
    timeout: params.timeout,
  }
}

export function getDagNodeCount(dag: Dag): number {
  return Object.keys(dag.nodes).length
}

export function getDagEdgeCount(dag: Dag): number {
  return dag.edges.length
}

export function getDagEntryNode(dag: Dag): DagNode | undefined {
  return dag.nodes[dag.entryPoint]
}
