/**
 * Waygrid DSL Parser - Converts tokens to EditorDag domain model
 */

import { Token, TokenType, tokenize, getSignificantTokens } from './lexer'
import { ParseError, createParseError } from '../value-objects/parse-error'
import {
  EditorDag,
  EditorNode,
  EditorEdge,
  EdgeGuard,
  RepeatPolicy,
  NodeComponent,
  NodeService,
  createEditorDag,
  createEditorNode,
  createEditorEdge,
  generateNodeId,
  generateEdgeId,
} from '../entities/editor-dag'

export interface ParseResult {
  dag: EditorDag | null
  errors: ParseError[]
}

interface ParserState {
  tokens: Token[]
  pos: number
  errors: ParseError[]
  nodesByName: Map<string, EditorNode>
}

function createParserState(tokens: Token[]): ParserState {
  return {
    tokens: getSignificantTokens(tokens),
    pos: 0,
    errors: [],
    nodesByName: new Map(),
  }
}

function current(state: ParserState): Token {
  return state.tokens[state.pos] ?? { type: 'EOF', value: '', line: 0, column: 0, length: 0 }
}

function peek(state: ParserState, offset = 0): Token {
  return state.tokens[state.pos + offset] ?? { type: 'EOF', value: '', line: 0, column: 0, length: 0 }
}

function advance(state: ParserState): Token {
  const token = current(state)
  state.pos++
  return token
}

function check(state: ParserState, type: TokenType): boolean {
  return current(state).type === type
}

function checkAny(state: ParserState, ...types: TokenType[]): boolean {
  return types.includes(current(state).type)
}

function match(state: ParserState, type: TokenType): Token | null {
  if (check(state, type)) {
    return advance(state)
  }
  return null
}

function expect(state: ParserState, type: TokenType, message: string): Token | null {
  const token = match(state, type)
  if (!token) {
    const curr = current(state)
    state.errors.push(createParseError(message, curr.line, curr.column))
  }
  return token
}

function isNodeComponent(type: TokenType): boolean {
  return ['COMPONENT_ORIGIN', 'COMPONENT_PROCESSOR', 'COMPONENT_DESTINATION', 'COMPONENT_SYSTEM'].includes(type)
}

function tokenToComponent(type: TokenType): NodeComponent {
  switch (type) {
    case 'COMPONENT_ORIGIN':
      return 'origin'
    case 'COMPONENT_PROCESSOR':
      return 'processor'
    case 'COMPONENT_DESTINATION':
      return 'destination'
    case 'COMPONENT_SYSTEM':
      return 'system'
    default:
      return 'processor'
  }
}

function parseString(state: ParserState): string | null {
  const token = match(state, 'STRING')
  if (!token) return null
  // Remove quotes
  return token.value.slice(1, -1)
}

/**
 * Parse a node reference: component.service "name"
 * Returns { component, service, name } or null
 */
function parseNodeRef(state: ParserState): { component: NodeComponent; service: NodeService; name: string } | null {
  if (!isNodeComponent(current(state).type)) {
    return null
  }

  const componentToken = advance(state)
  const component = tokenToComponent(componentToken.type)

  if (!match(state, 'DOT')) {
    state.errors.push(createParseError('Expected "." after component', componentToken.line, componentToken.column))
    return null
  }

  const serviceToken = match(state, 'IDENTIFIER')
  if (!serviceToken) {
    state.errors.push(createParseError('Expected service name', current(state).line, current(state).column))
    return null
  }
  const service = serviceToken.value as NodeService

  const name = parseString(state)
  if (!name) {
    state.errors.push(createParseError('Expected node name in quotes', current(state).line, current(state).column))
    return null
  }

  return { component, service, name }
}

/**
 * Get or create a node by its full identifier
 */
function getOrCreateNode(
  state: ParserState,
  component: NodeComponent,
  service: NodeService,
  name: string,
  isEntry: boolean = false
): EditorNode {
  const key = `${component}.${service}:${name}`

  if (state.nodesByName.has(key)) {
    const existing = state.nodesByName.get(key)!
    // Update entry flag if this is an entry declaration
    if (isEntry && !existing.isEntry) {
      const updated = { ...existing, isEntry: true }
      state.nodesByName.set(key, updated)
      return updated
    }
    return existing
  }

  const node = createEditorNode({
    id: generateNodeId(),
    component,
    service,
    name,
    isEntry,
  })
  state.nodesByName.set(key, node)
  return node
}

/**
 * Parse edge guard: ->success, ->failure, ->any, ->timeout, ->condition "expr"
 */
function parseEdgeGuard(state: ParserState): EdgeGuard | null {
  const token = current(state)

  switch (token.type) {
    case 'ARROW_SUCCESS':
      advance(state)
      return { type: 'success' }
    case 'ARROW_FAILURE':
      advance(state)
      return { type: 'failure' }
    case 'ARROW_ANY':
      advance(state)
      return { type: 'any' }
    case 'ARROW_TIMEOUT':
      advance(state)
      return { type: 'timeout' }
    case 'ARROW_CONDITION': {
      advance(state)
      const expr = parseString(state)
      if (!expr) {
        state.errors.push(
          createParseError('Expected condition expression in quotes', token.line, token.column)
        )
        return { type: 'condition', expression: '' }
      }
      return { type: 'condition', expression: expr }
    }
    default:
      return null
  }
}

/**
 * Parse a flow statement:
 * origin.http "name" ->success processor.transform "name2" ->failure destination.webhook "name3"
 */
function parseFlowStatement(state: ParserState): EditorEdge[] {
  const edges: EditorEdge[] = []

  // Parse source node
  const sourceRef = parseNodeRef(state)
  if (!sourceRef) return edges

  let sourceNode = getOrCreateNode(state, sourceRef.component, sourceRef.service, sourceRef.name)

  // Parse chain of arrows
  while (
    checkAny(state, 'ARROW_SUCCESS', 'ARROW_FAILURE', 'ARROW_ANY', 'ARROW_TIMEOUT', 'ARROW_CONDITION')
  ) {
    const guard = parseEdgeGuard(state)
    if (!guard) break

    const targetRef = parseNodeRef(state)
    if (!targetRef) {
      state.errors.push(
        createParseError('Expected target node after arrow', current(state).line, current(state).column)
      )
      break
    }

    const targetNode = getOrCreateNode(state, targetRef.component, targetRef.service, targetRef.name)

    edges.push(
      createEditorEdge({
        id: generateEdgeId(),
        source: sourceNode.id,
        target: targetNode.id,
        guard,
      })
    )

    sourceNode = targetNode
  }

  return edges
}

/**
 * Parse repeat policy
 */
function parseRepeatPolicy(state: ParserState): RepeatPolicy {
  const token = current(state)

  if (token.type === 'IDENTIFIER') {
    const value = token.value.toLowerCase()
    if (value === 'no-repeat') {
      advance(state)
      return { type: 'no-repeat' }
    }
    if (value === 'indefinitely') {
      advance(state)
      const every = parseString(state)
      return { type: 'indefinitely', every: every ?? '1h' }
    }
  }

  // Default
  return { type: 'no-repeat' }
}

/**
 * Parse DAG body between braces
 */
function parseDagBody(state: ParserState, dagName: string): EditorDag {
  let timeout: string | undefined
  let repeatPolicy: RepeatPolicy = { type: 'no-repeat' }
  const edges: EditorEdge[] = []

  while (!check(state, 'RBRACE') && !check(state, 'EOF')) {
    const token = current(state)

    // Handle timeout: "30s"
    if (token.type === 'KEYWORD_TIMEOUT') {
      advance(state)
      match(state, 'COLON')
      const value = parseString(state)
      if (value) timeout = value
      continue
    }

    // Handle repeat: no-repeat | indefinitely "1h"
    if (token.type === 'KEYWORD_REPEAT') {
      advance(state)
      match(state, 'COLON')
      repeatPolicy = parseRepeatPolicy(state)
      continue
    }

    // Handle entry: origin.http "name"
    if (token.type === 'KEYWORD_ENTRY') {
      advance(state)
      const nodeRef = parseNodeRef(state)
      if (nodeRef) {
        getOrCreateNode(state, nodeRef.component, nodeRef.service, nodeRef.name, true)
      }
      continue
    }

    // Handle flow statements
    if (isNodeComponent(token.type)) {
      const flowEdges = parseFlowStatement(state)
      edges.push(...flowEdges)
      continue
    }

    // Skip unknown tokens
    advance(state)
  }

  // Collect nodes from the map
  const nodes = Array.from(state.nodesByName.values())

  // Auto-layout nodes if no positions
  const layoutedNodes = autoLayoutNodes(nodes, edges)

  return createEditorDag({
    name: dagName,
    timeout,
    repeatPolicy,
    nodes: layoutedNodes,
    edges,
  })
}

/**
 * Simple auto-layout for nodes based on topology
 */
function autoLayoutNodes(nodes: EditorNode[], edges: EditorEdge[]): EditorNode[] {
  if (nodes.length === 0) return nodes

  // Build adjacency list
  const outgoing = new Map<string, string[]>()
  const incoming = new Map<string, string[]>()

  for (const node of nodes) {
    outgoing.set(node.id, [])
    incoming.set(node.id, [])
  }

  for (const edge of edges) {
    outgoing.get(edge.source)?.push(edge.target)
    incoming.get(edge.target)?.push(edge.source)
  }

  // Find entry nodes (no incoming edges or marked as entry)
  const entryNodes = nodes.filter(
    (n) => n.isEntry || (incoming.get(n.id)?.length ?? 0) === 0
  )

  // BFS to assign levels
  const levels = new Map<string, number>()
  const visited = new Set<string>()
  const queue: { id: string; level: number }[] = []

  for (const entry of entryNodes) {
    queue.push({ id: entry.id, level: 0 })
    levels.set(entry.id, 0)
  }

  while (queue.length > 0) {
    const { id, level } = queue.shift()!
    if (visited.has(id)) continue
    visited.add(id)

    const currentLevel = Math.max(levels.get(id) ?? 0, level)
    levels.set(id, currentLevel)

    for (const targetId of outgoing.get(id) ?? []) {
      const targetLevel = levels.get(targetId) ?? 0
      levels.set(targetId, Math.max(targetLevel, currentLevel + 1))
      queue.push({ id: targetId, level: currentLevel + 1 })
    }
  }

  // Handle unvisited nodes
  for (const node of nodes) {
    if (!levels.has(node.id)) {
      levels.set(node.id, 0)
    }
  }

  // Group by level
  const levelGroups = new Map<number, EditorNode[]>()
  for (const node of nodes) {
    const level = levels.get(node.id) ?? 0
    if (!levelGroups.has(level)) {
      levelGroups.set(level, [])
    }
    levelGroups.get(level)!.push(node)
  }

  // Assign positions
  const xSpacing = 250
  const ySpacing = 120
  const startX = 100
  const startY = 100

  const positionedNodes: EditorNode[] = []

  for (const [level, levelNodes] of levelGroups) {
    const yOffset = startY + (levelNodes.length - 1) * ySpacing * -0.5

    levelNodes.forEach((node, index) => {
      positionedNodes.push({
        ...node,
        position: {
          x: startX + level * xSpacing,
          y: yOffset + index * ySpacing,
        },
      })
    })
  }

  return positionedNodes
}

/**
 * Parse a complete DAG definition
 */
function parseDag(state: ParserState): EditorDag | null {
  // Expect: dag "name" {
  if (!match(state, 'KEYWORD_DAG')) {
    // If no dag keyword, try to parse as a loose flow
    if (isNodeComponent(current(state).type)) {
      const edges = parseFlowStatement(state)
      const nodes = Array.from(state.nodesByName.values())
      return createEditorDag({
        name: 'untitled',
        nodes: autoLayoutNodes(nodes, edges),
        edges,
      })
    }
    return null
  }

  const name = parseString(state)
  if (!name) {
    state.errors.push(createParseError('Expected DAG name in quotes', current(state).line, current(state).column))
    return null
  }

  if (!match(state, 'LBRACE')) {
    state.errors.push(createParseError('Expected "{"', current(state).line, current(state).column))
    return null
  }

  const dag = parseDagBody(state, name)

  if (!match(state, 'RBRACE')) {
    state.errors.push(createParseError('Expected "}"', current(state).line, current(state).column))
  }

  return dag
}

/**
 * Parse Waygrid DSL source code into an EditorDag
 */
export function parseDSL(source: string): ParseResult {
  const tokens = tokenize(source)
  const state = createParserState(tokens)

  const dag = parseDag(state)

  return {
    dag,
    errors: state.errors,
  }
}

/**
 * Validate a DAG and return validation errors
 */
export function validateDag(dag: EditorDag): ParseError[] {
  const errors: ParseError[] = []

  // Must have at least one node
  if (dag.nodes.length === 0) {
    errors.push(createParseError('DAG must have at least one node', 1, 1, 'warning'))
  }

  // Must have an entry point
  const entryNode = dag.nodes.find((n) => n.isEntry)
  if (!entryNode && dag.nodes.length > 0) {
    errors.push(createParseError('DAG should have an entry point', 1, 1, 'warning'))
  }

  // Check for disconnected nodes
  const connectedNodes = new Set<string>()
  for (const edge of dag.edges) {
    connectedNodes.add(edge.source)
    connectedNodes.add(edge.target)
  }

  for (const node of dag.nodes) {
    if (dag.nodes.length > 1 && !connectedNodes.has(node.id)) {
      errors.push(
        createParseError(`Node "${node.name}" is not connected to any edge`, 1, 1, 'warning')
      )
    }
  }

  return errors
}
