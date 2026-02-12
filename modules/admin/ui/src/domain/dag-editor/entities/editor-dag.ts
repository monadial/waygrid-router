import { Position, DEFAULT_POSITION } from '../value-objects/position'

/**
 * Edge guard types - determines when an edge is traversed
 */
export type EdgeGuard =
  | { type: 'success' }
  | { type: 'failure' }
  | { type: 'any' }
  | { type: 'timeout' }
  | { type: 'condition'; expression: string }

export function guardToString(guard: EdgeGuard): string {
  switch (guard.type) {
    case 'success':
      return '->success'
    case 'failure':
      return '->failure'
    case 'any':
      return '->any'
    case 'timeout':
      return '->timeout'
    case 'condition':
      return `->condition "${guard.expression}"`
  }
}

export function guardFromString(str: string): EdgeGuard | null {
  if (str === '->success') return { type: 'success' }
  if (str === '->failure') return { type: 'failure' }
  if (str === '->any') return { type: 'any' }
  if (str === '->timeout') return { type: 'timeout' }
  const condMatch = str.match(/^->condition\s+"([^"]*)"$/)
  if (condMatch) return { type: 'condition', expression: condMatch[1] }
  return null
}

/**
 * Node component types in the Waygrid system
 */
export type NodeComponent = 'origin' | 'processor' | 'destination' | 'system'

/**
 * Node service - the specific service within a component
 */
export type NodeService =
  | 'http'
  | 'kafka'
  | 'grpc'
  | 'transform'
  | 'openai'
  | 'webhook'
  | 'websocket'
  | 'topology'
  | 'scheduler'

/**
 * EditorNode - A node in the visual DAG editor
 */
export interface EditorNode {
  readonly id: string
  readonly component: NodeComponent
  readonly service: NodeService
  readonly name: string
  readonly position: Position
  readonly isEntry?: boolean
  readonly parameters?: Record<string, unknown>
}

export function createEditorNode(props: {
  id: string
  component: NodeComponent
  service: NodeService
  name: string
  position?: Position
  isEntry?: boolean
  parameters?: Record<string, unknown>
}): EditorNode {
  return {
    id: props.id,
    component: props.component,
    service: props.service,
    name: props.name,
    position: props.position ?? DEFAULT_POSITION,
    isEntry: props.isEntry,
    parameters: props.parameters,
  }
}

export function nodeDescriptor(node: EditorNode): string {
  return `${node.component}.${node.service}`
}

/**
 * EditorEdge - A connection between nodes with a guard condition
 */
export interface EditorEdge {
  readonly id: string
  readonly source: string
  readonly target: string
  readonly guard: EdgeGuard
}

export function createEditorEdge(props: {
  id: string
  source: string
  target: string
  guard: EdgeGuard
}): EditorEdge {
  return {
    id: props.id,
    source: props.source,
    target: props.target,
    guard: props.guard,
  }
}

/**
 * Repeat policy for DAG execution
 */
export type RepeatPolicy =
  | { type: 'no-repeat' }
  | { type: 'indefinitely'; every: string }
  | { type: 'times'; every: string; count: number }
  | { type: 'until'; every: string; until: string }

/**
 * EditorDag - The complete DAG representation for the editor
 */
export interface EditorDag {
  readonly name: string
  readonly timeout?: string
  readonly repeatPolicy: RepeatPolicy
  readonly nodes: EditorNode[]
  readonly edges: EditorEdge[]
}

export function createEditorDag(props: {
  name: string
  timeout?: string
  repeatPolicy?: RepeatPolicy
  nodes?: EditorNode[]
  edges?: EditorEdge[]
}): EditorDag {
  return {
    name: props.name,
    timeout: props.timeout,
    repeatPolicy: props.repeatPolicy ?? { type: 'no-repeat' },
    nodes: props.nodes ?? [],
    edges: props.edges ?? [],
  }
}

export function getEntryNode(dag: EditorDag): EditorNode | undefined {
  return dag.nodes.find((n) => n.isEntry)
}

export function getNodeById(dag: EditorDag, id: string): EditorNode | undefined {
  return dag.nodes.find((n) => n.id === id)
}

export function getEdgesFromNode(dag: EditorDag, nodeId: string): EditorEdge[] {
  return dag.edges.filter((e) => e.source === nodeId)
}

export function getEdgesToNode(dag: EditorDag, nodeId: string): EditorEdge[] {
  return dag.edges.filter((e) => e.target === nodeId)
}

/**
 * Generate a unique node ID
 */
let nodeIdCounter = 0
export function generateNodeId(): string {
  return `node_${Date.now()}_${++nodeIdCounter}`
}

/**
 * Generate a unique edge ID
 */
let edgeIdCounter = 0
export function generateEdgeId(): string {
  return `edge_${Date.now()}_${++edgeIdCounter}`
}

/**
 * Create an empty DAG for new projects
 */
export function createEmptyDag(name: string = 'new-dag'): EditorDag {
  return createEditorDag({
    name,
    repeatPolicy: { type: 'no-repeat' },
    nodes: [],
    edges: [],
  })
}

/**
 * Create a sample DAG for demonstration
 */
export function createSampleDag(): EditorDag {
  const entryNode = createEditorNode({
    id: 'node_1',
    component: 'origin',
    service: 'http',
    name: 'webhook-ingress',
    position: { x: 100, y: 200 },
    isEntry: true,
  })

  const processorNode = createEditorNode({
    id: 'node_2',
    component: 'processor',
    service: 'transform',
    name: 'validate-payload',
    position: { x: 350, y: 200 },
  })

  const destNode = createEditorNode({
    id: 'node_3',
    component: 'destination',
    service: 'webhook',
    name: 'api-delivery',
    position: { x: 600, y: 150 },
  })

  const errorNode = createEditorNode({
    id: 'node_4',
    component: 'destination',
    service: 'webhook',
    name: 'error-handler',
    position: { x: 600, y: 300 },
  })

  return createEditorDag({
    name: 'payment-flow',
    timeout: '30s',
    repeatPolicy: { type: 'no-repeat' },
    nodes: [entryNode, processorNode, destNode, errorNode],
    edges: [
      createEditorEdge({
        id: 'edge_1',
        source: 'node_1',
        target: 'node_2',
        guard: { type: 'success' },
      }),
      createEditorEdge({
        id: 'edge_2',
        source: 'node_2',
        target: 'node_3',
        guard: { type: 'success' },
      }),
      createEditorEdge({
        id: 'edge_3',
        source: 'node_2',
        target: 'node_4',
        guard: { type: 'failure' },
      }),
    ],
  })
}
