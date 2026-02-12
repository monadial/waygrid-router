import { useCallback, useRef, useState } from 'react'
import ReactFlow, {
  Node,
  Edge,
  Controls,
  Background,
  BackgroundVariant,
  Connection,
  addEdge,
  useNodesState,
  useEdgesState,
  ReactFlowProvider,
  ReactFlowInstance,
  NodeTypes,
  EdgeTypes,
  OnSelectionChangeParams,
} from 'reactflow'
import 'reactflow/dist/style.css'

import { DagNode, DagNodeData } from './dag-node'
import { DagEdge, DagEdgeData } from './dag-edge'
import { NodePalette, NodeTemplate } from './node-palette'
import { NodeConfigDialog, NodeConfig } from './node-config-dialog'
import { EdgeConfigDialog, EdgeConfig } from './edge-config-dialog'
import { DagSettingsPanel, DagSettings } from './dag-settings-panel'
import { PropertiesSidebar } from './properties-sidebar'
import {
  EditorDag,
  EditorNode,
  EditorEdge,
  generateNodeId,
  generateEdgeId,
} from '@/domain/dag-editor/entities/editor-dag'

const nodeTypes: NodeTypes = {
  dagNode: DagNode,
}

const edgeTypes: EdgeTypes = {
  dagEdge: DagEdge,
}

interface DagCanvasProps {
  initialDag?: EditorDag
  onChange?: (dag: EditorDag) => void
  showDslEditor?: boolean
  onToggleDslEditor?: () => void
}

function editorNodeToFlowNode(node: EditorNode): Node<DagNodeData> {
  return {
    id: node.id,
    type: 'dagNode',
    position: node.position,
    data: {
      component: node.component,
      service: node.service,
      name: node.name,
      isEntry: node.isEntry,
      parameters: node.parameters,
    },
  }
}

function editorEdgeToFlowEdge(edge: EditorEdge): Edge<DagEdgeData> {
  return {
    id: edge.id,
    source: edge.source,
    target: edge.target,
    type: 'dagEdge',
    data: {
      guard: edge.guard,
    },
  }
}

interface PendingNode {
  template: NodeTemplate
  position: { x: number; y: number }
}

interface PendingEdge {
  connection: Connection
}

function DagCanvasInner({ initialDag, onChange }: DagCanvasProps) {
  const reactFlowWrapper = useRef<HTMLDivElement>(null)
  const [reactFlowInstance, setReactFlowInstance] = useState<ReactFlowInstance | null>(null)

  const initialNodes = initialDag?.nodes.map(editorNodeToFlowNode) ?? []
  const initialEdges = initialDag?.edges.map(editorEdgeToFlowEdge) ?? []

  const [nodes, setNodes, onNodesChange] = useNodesState(initialNodes)
  const [edges, setEdges, onEdgesChange] = useEdgesState(initialEdges)

  // Dialog states
  const [nodeConfigOpen, setNodeConfigOpen] = useState(false)
  const [edgeConfigOpen, setEdgeConfigOpen] = useState(false)
  const [settingsCollapsed, setSettingsCollapsed] = useState(true)

  // Selection state
  const [selectedNode, setSelectedNode] = useState<Node<DagNodeData> | null>(null)
  const [selectedEdge, setSelectedEdge] = useState<Edge<DagEdgeData> | null>(null)

  // Pending items waiting for configuration
  const [pendingNode, setPendingNode] = useState<PendingNode | null>(null)
  const [pendingEdge, setPendingEdge] = useState<PendingEdge | null>(null)

  // DAG settings
  const [dagSettings, setDagSettings] = useState<DagSettings>({
    name: initialDag?.name ?? 'new-dag',
    timeout: initialDag?.timeout,
    repeatPolicy: initialDag?.repeatPolicy ?? { type: 'no-repeat' },
  })

  // Build current DAG from state
  const buildDag = useCallback((): EditorDag => {
    return {
      name: dagSettings.name,
      timeout: dagSettings.timeout,
      repeatPolicy: dagSettings.repeatPolicy,
      nodes: nodes.map((n) => ({
        id: n.id,
        component: n.data.component,
        service: n.data.service,
        name: n.data.name,
        position: n.position,
        isEntry: n.data.isEntry,
        parameters: n.data.parameters,
      })),
      edges: edges.map((e) => ({
        id: e.id,
        source: e.source,
        target: e.target,
        guard: e.data?.guard ?? { type: 'success' },
      })),
    }
  }, [dagSettings, nodes, edges])

  // Notify parent of changes
  const notifyChange = useCallback(() => {
    if (onChange) {
      onChange(buildDag())
    }
  }, [onChange, buildDag])

  // Handle selection change
  const onSelectionChange = useCallback(({ nodes: selectedNodes, edges: selectedEdges }: OnSelectionChangeParams) => {
    if (selectedNodes.length === 1) {
      setSelectedNode(selectedNodes[0] as Node<DagNodeData>)
      setSelectedEdge(null)
    } else if (selectedEdges.length === 1) {
      setSelectedEdge(selectedEdges[0] as Edge<DagEdgeData>)
      setSelectedNode(null)
    } else {
      setSelectedNode(null)
      setSelectedEdge(null)
    }
  }, [])

  // Handle new connection - show edge config dialog
  const onConnect = useCallback((connection: Connection) => {
    setPendingEdge({ connection })
    setEdgeConfigOpen(true)
  }, [])

  // Handle edge configuration save
  const handleEdgeConfigSave = useCallback(
    (config: EdgeConfig) => {
      if (pendingEdge) {
        const newEdge: Edge<DagEdgeData> = {
          id: generateEdgeId(),
          source: pendingEdge.connection.source!,
          target: pendingEdge.connection.target!,
          type: 'dagEdge',
          data: {
            guard: config.guard,
          },
        }
        setEdges((eds) => addEdge(newEdge, eds))
        setPendingEdge(null)
      }
      setTimeout(notifyChange, 0)
    },
    [pendingEdge, setEdges, notifyChange]
  )

  const onDragOver = useCallback((event: React.DragEvent) => {
    event.preventDefault()
    event.dataTransfer.dropEffect = 'move'
  }, [])

  // Handle drop - show node config dialog
  const onDrop = useCallback(
    (event: React.DragEvent) => {
      event.preventDefault()

      if (!reactFlowWrapper.current || !reactFlowInstance) return

      const templateData = event.dataTransfer.getData('application/reactflow')
      if (!templateData) return

      const template: NodeTemplate = JSON.parse(templateData)
      const bounds = reactFlowWrapper.current.getBoundingClientRect()
      const position = reactFlowInstance.project({
        x: event.clientX - bounds.left,
        y: event.clientY - bounds.top,
      })

      // Show configuration dialog
      setPendingNode({ template, position })
      setNodeConfigOpen(true)
    },
    [reactFlowInstance]
  )

  // Handle node configuration save
  const handleNodeConfigSave = useCallback(
    (config: NodeConfig) => {
      if (!pendingNode) return

      const newNode: Node<DagNodeData> = {
        id: generateNodeId(),
        type: 'dagNode',
        position: pendingNode.position,
        data: {
          component: config.component,
          service: config.service,
          name: config.name,
          isEntry: config.isEntry,
          parameters: config.parameters,
          retry: config.retry,
        },
      }

      setNodes((nds) => [...nds, newNode])
      setPendingNode(null)
      setTimeout(notifyChange, 0)
    },
    [pendingNode, setNodes, notifyChange]
  )

  const onPaletteDragStart = useCallback(() => {
    // Could add visual feedback here
  }, [])

  // Handle settings change
  const handleSettingsChange = useCallback((newSettings: DagSettings) => {
    setDagSettings(newSettings)
  }, [])

  // Handle node update from properties sidebar
  const handleNodeUpdate = useCallback(
    (nodeId: string, data: Partial<DagNodeData>) => {
      setNodes((nds) =>
        nds.map((n) => (n.id === nodeId ? { ...n, data: { ...n.data, ...data } } : n))
      )
      // Update selected node reference
      setSelectedNode((prev) =>
        prev?.id === nodeId ? { ...prev, data: { ...prev.data, ...data } } : prev
      )
      setTimeout(notifyChange, 0)
    },
    [setNodes, notifyChange]
  )

  // Handle edge update from properties sidebar
  const handleEdgeUpdate = useCallback(
    (edgeId: string, data: Partial<DagEdgeData>) => {
      setEdges((eds) =>
        eds.map((e) => {
          if (e.id !== edgeId) return e
          const newData: DagEdgeData = {
            guard: data.guard ?? e.data?.guard ?? { type: 'success' },
          }
          return { ...e, data: newData }
        })
      )
      // Update selected edge reference
      setSelectedEdge((prev) => {
        if (prev?.id !== edgeId) return prev
        const newData: DagEdgeData = {
          guard: data.guard ?? prev.data?.guard ?? { type: 'success' },
        }
        return { ...prev, data: newData }
      })
      setTimeout(notifyChange, 0)
    },
    [setEdges, notifyChange]
  )

  // Handle node delete
  const handleNodeDelete = useCallback(
    (nodeId: string) => {
      setNodes((nds) => nds.filter((n) => n.id !== nodeId))
      setEdges((eds) => eds.filter((e) => e.source !== nodeId && e.target !== nodeId))
      setSelectedNode(null)
      setTimeout(notifyChange, 0)
    },
    [setNodes, setEdges, notifyChange]
  )

  // Handle edge delete
  const handleEdgeDelete = useCallback(
    (edgeId: string) => {
      setEdges((eds) => eds.filter((e) => e.id !== edgeId))
      setSelectedEdge(null)
      setTimeout(notifyChange, 0)
    },
    [setEdges, notifyChange]
  )

  // Close properties sidebar
  const handleCloseProperties = useCallback(() => {
    setSelectedNode(null)
    setSelectedEdge(null)
  }, [])

  // Get node names for edge config dialog
  const getNodeName = (nodeId: string | null) => {
    if (!nodeId) return undefined
    const node = nodes.find((n) => n.id === nodeId)
    return node?.data.name
  }

  const showPropertiesSidebar = selectedNode !== null || selectedEdge !== null

  return (
    <div className="relative flex h-full w-full">
      <NodePalette onDragStart={onPaletteDragStart} />
      <div ref={reactFlowWrapper} className="relative flex-1">
        <ReactFlow
          nodes={nodes}
          edges={edges}
          onNodesChange={onNodesChange}
          onEdgesChange={onEdgesChange}
          onConnect={onConnect}
          onSelectionChange={onSelectionChange}
          onInit={setReactFlowInstance}
          onDrop={onDrop}
          onDragOver={onDragOver}
          nodeTypes={nodeTypes}
          edgeTypes={edgeTypes}
          fitView
          snapToGrid
          snapGrid={[15, 15]}
          defaultEdgeOptions={{
            type: 'dagEdge',
          }}
          className="bg-background"
          selectNodesOnDrag={false}
        >
          <Controls className="rounded-lg border bg-card shadow-sm" />
          <Background
            variant={BackgroundVariant.Dots}
            gap={20}
            size={1}
            className="!bg-muted/30"
          />
        </ReactFlow>

        {/* DAG Settings Panel */}
        <DagSettingsPanel
          settings={dagSettings}
          onChange={handleSettingsChange}
          collapsed={settingsCollapsed}
          onToggleCollapsed={() => setSettingsCollapsed(!settingsCollapsed)}
        />
      </div>

      {/* Properties Sidebar */}
      {showPropertiesSidebar && (
        <PropertiesSidebar
          selectedNode={selectedNode}
          selectedEdge={selectedEdge}
          onNodeUpdate={handleNodeUpdate}
          onEdgeUpdate={handleEdgeUpdate}
          onNodeDelete={handleNodeDelete}
          onEdgeDelete={handleEdgeDelete}
          onClose={handleCloseProperties}
          nodes={nodes}
        />
      )}

      {/* Node Configuration Dialog */}
      <NodeConfigDialog
        open={nodeConfigOpen}
        onOpenChange={(open) => {
          setNodeConfigOpen(open)
          if (!open) setPendingNode(null)
        }}
        initialConfig={
          pendingNode
            ? {
                component: pendingNode.template.component,
                service: pendingNode.template.service,
                isEntry: nodes.length === 0 && pendingNode.template.component === 'origin',
              }
            : undefined
        }
        onSave={handleNodeConfigSave}
        mode="create"
      />

      {/* Edge Configuration Dialog */}
      <EdgeConfigDialog
        open={edgeConfigOpen}
        onOpenChange={(open) => {
          setEdgeConfigOpen(open)
          if (!open) {
            setPendingEdge(null)
          }
        }}
        initialConfig={undefined}
        onSave={handleEdgeConfigSave}
        sourceNodeName={pendingEdge ? getNodeName(pendingEdge.connection.source) : undefined}
        targetNodeName={pendingEdge ? getNodeName(pendingEdge.connection.target) : undefined}
      />
    </div>
  )
}

export function DagCanvas(props: DagCanvasProps) {
  return (
    <ReactFlowProvider>
      <DagCanvasInner {...props} />
    </ReactFlowProvider>
  )
}
