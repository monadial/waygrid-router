/**
 * Waygrid DSL Generator - Converts EditorDag domain model to DSL code
 */

import {
  EditorDag,
  EditorNode,
  EditorEdge,
  EdgeGuard,
  RepeatPolicy,
  nodeDescriptor,
  getEntryNode,
  getEdgesFromNode,
} from '../entities/editor-dag'

interface GeneratorOptions {
  indent: string
  includeComments: boolean
}

const DEFAULT_OPTIONS: GeneratorOptions = {
  indent: '  ',
  includeComments: true,
}

/**
 * Generate guard arrow syntax
 */
function generateGuard(guard: EdgeGuard): string {
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

/**
 * Generate node reference: component.service "name"
 */
function generateNodeRef(node: EditorNode): string {
  return `${nodeDescriptor(node)} "${node.name}"`
}

/**
 * Generate repeat policy syntax
 */
function generateRepeatPolicy(policy: RepeatPolicy): string {
  switch (policy.type) {
    case 'no-repeat':
      return 'no-repeat'
    case 'indefinitely':
      return `indefinitely "${policy.every}"`
    case 'times':
      return `times ${policy.count} "${policy.every}"`
    case 'until':
      return `until "${policy.until}" "${policy.every}"`
  }
}

/**
 * Build flow chains from edges
 * Returns arrays of connected nodes representing flow paths
 */
function buildFlowChains(dag: EditorDag): EditorEdge[][] {
  const chains: EditorEdge[][] = []
  const usedEdges = new Set<string>()

  // Start from entry node if available, otherwise from nodes with no incoming edges
  const entryNode = getEntryNode(dag)
  const startNodes: EditorNode[] = []

  if (entryNode) {
    startNodes.push(entryNode)
  }

  // Also add nodes with no incoming edges
  const hasIncoming = new Set(dag.edges.map((e) => e.target))
  for (const node of dag.nodes) {
    if (!hasIncoming.has(node.id) && node.id !== entryNode?.id) {
      startNodes.push(node)
    }
  }

  // Build chains starting from each start node
  for (const startNode of startNodes) {
    const visited = new Set<string>()
    const stack: { nodeId: string; chain: EditorEdge[] }[] = [
      { nodeId: startNode.id, chain: [] },
    ]

    while (stack.length > 0) {
      const { nodeId, chain } = stack.pop()!

      if (visited.has(nodeId)) continue
      visited.add(nodeId)

      const outEdges = getEdgesFromNode(dag, nodeId)

      for (const edge of outEdges) {
        if (usedEdges.has(edge.id)) continue

        const newChain = [...chain, edge]
        usedEdges.add(edge.id)

        // If target has single outgoing edge, continue chain
        // Otherwise, save this chain and start new ones
        const targetOutEdges = getEdgesFromNode(dag, edge.target).filter(
          (e) => !usedEdges.has(e.id)
        )

        if (targetOutEdges.length === 1) {
          stack.push({ nodeId: edge.target, chain: newChain })
        } else {
          if (newChain.length > 0) {
            chains.push(newChain)
          }
          if (targetOutEdges.length > 0) {
            stack.push({ nodeId: edge.target, chain: [] })
          }
        }
      }

      // If no outgoing edges and we have a chain, save it
      if (outEdges.length === 0 && chain.length > 0) {
        chains.push(chain)
      }
    }
  }

  // Handle any remaining edges (cycles or disconnected)
  for (const edge of dag.edges) {
    if (!usedEdges.has(edge.id)) {
      chains.push([edge])
      usedEdges.add(edge.id)
    }
  }

  return chains
}

/**
 * Generate DSL code from a chain of edges
 */
function generateFlowStatement(
  chain: EditorEdge[],
  dag: EditorDag,
  indent: string
): string {
  if (chain.length === 0) return ''

  const lines: string[] = []
  const firstEdge = chain[0]
  const sourceNode = dag.nodes.find((n) => n.id === firstEdge.source)

  if (!sourceNode) return ''

  // First line: source node
  let currentLine = `${indent}${generateNodeRef(sourceNode)}`

  for (const edge of chain) {
    const targetNode = dag.nodes.find((n) => n.id === edge.target)
    if (!targetNode) continue

    // Add arrow and target on new line with indentation
    lines.push(currentLine)
    currentLine = `${indent}  ${generateGuard(edge.guard)} ${generateNodeRef(targetNode)}`
  }

  lines.push(currentLine)
  return lines.join('\n')
}

/**
 * Generate complete DSL code from an EditorDag
 */
export function generateDSL(dag: EditorDag, options: Partial<GeneratorOptions> = {}): string {
  const opts = { ...DEFAULT_OPTIONS, ...options }
  const lines: string[] = []
  const indent = opts.indent

  // Header comment
  if (opts.includeComments) {
    lines.push('# Waygrid DAG Definition')
    lines.push('')
  }

  // DAG declaration
  lines.push(`dag "${dag.name}" {`)

  // Timeout if specified
  if (dag.timeout) {
    lines.push(`${indent}timeout: "${dag.timeout}"`)
  }

  // Repeat policy
  lines.push(`${indent}repeat: ${generateRepeatPolicy(dag.repeatPolicy)}`)
  lines.push('')

  // Entry point
  const entryNode = getEntryNode(dag)
  if (entryNode) {
    if (opts.includeComments) {
      lines.push(`${indent}# Entry point`)
    }
    lines.push(`${indent}entry ${generateNodeRef(entryNode)}`)
    lines.push('')
  }

  // Flow statements
  const chains = buildFlowChains(dag)

  if (chains.length > 0 && opts.includeComments) {
    lines.push(`${indent}# Flow definition`)
  }

  for (const chain of chains) {
    const flowStatement = generateFlowStatement(chain, dag, indent)
    if (flowStatement) {
      lines.push(flowStatement)
      lines.push('')
    }
  }

  // Closing brace
  lines.push('}')

  return lines.join('\n')
}

/**
 * Generate a minimal DSL snippet for a single node
 */
export function generateNodeSnippet(node: EditorNode): string {
  return generateNodeRef(node)
}

/**
 * Generate a minimal DSL snippet for an edge
 */
export function generateEdgeSnippet(
  edge: EditorEdge,
  sourceNode: EditorNode,
  targetNode: EditorNode
): string {
  return `${generateNodeRef(sourceNode)}\n  ${generateGuard(edge.guard)} ${generateNodeRef(targetNode)}`
}

/**
 * Generate empty DAG template
 */
export function generateEmptyDagTemplate(name: string = 'new-dag'): string {
  return `# Waygrid DAG Definition
dag "${name}" {
  timeout: "30s"
  repeat: no-repeat

  # Entry point
  entry origin.http "webhook-ingress"

  # Flow definition
  origin.http "webhook-ingress"
    ->success processor.transform "validate"
    ->success destination.webhook "api-delivery"
    ->failure destination.webhook "error-handler"
}
`
}
