import { Globe, Cpu, Send, Settings, Trash2, X } from 'lucide-react'
import { Node, Edge } from 'reactflow'

import { Button } from '@/presentation/components/ui/button'
import { Input } from '@/presentation/components/ui/input'
import { Label } from '@/presentation/components/ui/label'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/presentation/components/ui/select'
import { Separator } from '@/presentation/components/ui/separator'
import { cn } from '@/lib/utils'
import { NodeComponent, NodeService, EdgeGuard } from '@/domain/dag-editor/entities/editor-dag'
import { DagNodeData, RetryConfig } from './dag-node'
import { DagEdgeData } from './dag-edge'

interface PropertiesSidebarProps {
  selectedNode: Node<DagNodeData> | null
  selectedEdge: Edge<DagEdgeData> | null
  onNodeUpdate: (nodeId: string, data: Partial<DagNodeData>) => void
  onEdgeUpdate: (edgeId: string, data: Partial<DagEdgeData>) => void
  onNodeDelete: (nodeId: string) => void
  onEdgeDelete: (edgeId: string) => void
  onClose: () => void
  nodes: Node<DagNodeData>[]
}

const componentIcons: Record<NodeComponent, React.ElementType> = {
  origin: Globe,
  processor: Cpu,
  destination: Send,
  system: Settings,
}

const componentColors: Record<NodeComponent, string> = {
  origin: 'text-emerald-600 bg-emerald-100 dark:bg-emerald-900/50',
  processor: 'text-blue-600 bg-blue-100 dark:bg-blue-900/50',
  destination: 'text-purple-600 bg-purple-100 dark:bg-purple-900/50',
  system: 'text-orange-600 bg-orange-100 dark:bg-orange-900/50',
}

const servicesByComponent: Record<NodeComponent, { value: NodeService; label: string }[]> = {
  origin: [
    { value: 'http', label: 'HTTP Webhook' },
    { value: 'kafka', label: 'Kafka Consumer' },
    { value: 'grpc', label: 'gRPC Server' },
  ],
  processor: [
    { value: 'transform', label: 'Transform' },
    { value: 'openai', label: 'OpenAI' },
  ],
  destination: [
    { value: 'webhook', label: 'Webhook' },
    { value: 'websocket', label: 'WebSocket' },
  ],
  system: [
    { value: 'scheduler', label: 'Scheduler' },
    { value: 'topology', label: 'Topology' },
  ],
}

const serviceParameters: Record<NodeService, { key: string; label: string; placeholder: string }[]> = {
  http: [
    { key: 'path', label: 'Path', placeholder: '/webhooks/incoming' },
    { key: 'method', label: 'Method', placeholder: 'POST' },
  ],
  kafka: [
    { key: 'topic', label: 'Topic', placeholder: 'events.incoming' },
    { key: 'groupId', label: 'Consumer Group', placeholder: 'waygrid-consumer' },
  ],
  grpc: [
    { key: 'service', label: 'Service', placeholder: 'EventService' },
    { key: 'method', label: 'Method', placeholder: 'IngestEvent' },
  ],
  transform: [
    { key: 'script', label: 'Transform Script', placeholder: 'event => ({ ...event })' },
  ],
  openai: [
    { key: 'model', label: 'Model', placeholder: 'gpt-4' },
    { key: 'prompt', label: 'System Prompt', placeholder: 'You are a helpful assistant...' },
  ],
  webhook: [
    { key: 'url', label: 'URL', placeholder: 'https://api.example.com/webhook' },
    { key: 'headers', label: 'Headers (JSON)', placeholder: '{"Authorization": "Bearer ..."}' },
  ],
  websocket: [
    { key: 'channel', label: 'Channel', placeholder: 'events' },
  ],
  scheduler: [
    { key: 'cron', label: 'Cron Expression', placeholder: '0 * * * *' },
  ],
  topology: [],
}

const guardOptions: { value: EdgeGuard['type']; label: string; color: string }[] = [
  { value: 'success', label: 'On Success', color: 'text-emerald-600' },
  { value: 'failure', label: 'On Failure', color: 'text-red-600' },
  { value: 'any', label: 'Always', color: 'text-slate-600' },
  { value: 'timeout', label: 'On Timeout', color: 'text-amber-600' },
  { value: 'condition', label: 'Conditional', color: 'text-violet-600' },
]

export function PropertiesSidebar({
  selectedNode,
  selectedEdge,
  onNodeUpdate,
  onEdgeUpdate,
  onNodeDelete,
  onEdgeDelete,
  onClose,
  nodes,
}: PropertiesSidebarProps) {
  if (!selectedNode && !selectedEdge) {
    return null
  }

  // Node editing
  if (selectedNode) {
    const Icon = componentIcons[selectedNode.data.component]
    const currentParams = serviceParameters[selectedNode.data.service] ?? []
    const parameters = (selectedNode.data.parameters ?? {}) as Record<string, string>

    return (
      <div className="flex h-full w-80 flex-col border-l bg-card">
        {/* Header */}
        <div className="flex items-center justify-between border-b p-4">
          <div className="flex items-center gap-2">
            <div className={cn('rounded-md p-1.5', componentColors[selectedNode.data.component])}>
              <Icon className="h-4 w-4" />
            </div>
            <span className="font-semibold">Node Properties</span>
          </div>
          <Button variant="ghost" size="icon" onClick={onClose}>
            <X className="h-4 w-4" />
          </Button>
        </div>

        {/* Content */}
        <div className="flex-1 overflow-y-auto p-4">
          <div className="space-y-4">
            {/* Node Name */}
            <div className="space-y-2">
              <Label htmlFor="nodeName">Name</Label>
              <Input
                id="nodeName"
                value={selectedNode.data.name}
                onChange={(e) => onNodeUpdate(selectedNode.id, { name: e.target.value })}
              />
            </div>

            {/* Component Type (read-only) */}
            <div className="space-y-2">
              <Label>Component</Label>
              <div className="flex items-center gap-2 rounded-md border bg-muted/50 px-3 py-2 text-sm">
                <Icon className={cn('h-4 w-4', componentColors[selectedNode.data.component].split(' ')[0])} />
                <span className="capitalize">{selectedNode.data.component}</span>
              </div>
            </div>

            {/* Service */}
            <div className="space-y-2">
              <Label>Service</Label>
              <Select
                value={selectedNode.data.service}
                onValueChange={(v) => onNodeUpdate(selectedNode.id, { service: v as NodeService })}
              >
                <SelectTrigger>
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  {servicesByComponent[selectedNode.data.component].map((svc) => (
                    <SelectItem key={svc.value} value={svc.value}>
                      {svc.label}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>

            {/* Entry Point (only for origins) */}
            {selectedNode.data.component === 'origin' && (
              <div className="flex items-center gap-2">
                <input
                  type="checkbox"
                  id="isEntry"
                  checked={selectedNode.data.isEntry ?? false}
                  onChange={(e) => onNodeUpdate(selectedNode.id, { isEntry: e.target.checked })}
                  className="h-4 w-4 rounded border-gray-300"
                />
                <Label htmlFor="isEntry">Entry Point</Label>
              </div>
            )}

            <Separator />

            {/* Retry Policy */}
            <div className="space-y-2">
              <Label>Retry Policy</Label>
              <Select
                value={selectedNode.data.retry?.type ?? 'none'}
                onValueChange={(v) => {
                  const type = v as RetryConfig['type']
                  const retry: RetryConfig =
                    type === 'none'
                      ? { type: 'none' }
                      : { type, maxRetries: 3, delay: '1s' }
                  onNodeUpdate(selectedNode.id, { retry })
                }}
              >
                <SelectTrigger>
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="none">No Retry</SelectItem>
                  <SelectItem value="linear">Linear Backoff</SelectItem>
                  <SelectItem value="exponential">Exponential Backoff</SelectItem>
                </SelectContent>
              </Select>

              {selectedNode.data.retry && selectedNode.data.retry.type !== 'none' && (
                <div className="grid grid-cols-2 gap-2 pt-2">
                  <div className="space-y-1">
                    <Label className="text-xs">Max Retries</Label>
                    <Input
                      type="number"
                      min="1"
                      max="10"
                      value={selectedNode.data.retry.maxRetries ?? 3}
                      onChange={(e) =>
                        onNodeUpdate(selectedNode.id, {
                          retry: {
                            ...selectedNode.data.retry!,
                            maxRetries: parseInt(e.target.value, 10),
                          },
                        })
                      }
                    />
                  </div>
                  <div className="space-y-1">
                    <Label className="text-xs">Delay</Label>
                    <Input
                      value={selectedNode.data.retry.delay ?? '1s'}
                      onChange={(e) =>
                        onNodeUpdate(selectedNode.id, {
                          retry: { ...selectedNode.data.retry!, delay: e.target.value },
                        })
                      }
                      placeholder="1s"
                    />
                  </div>
                </div>
              )}
            </div>

            {/* Service Parameters */}
            {currentParams.length > 0 && (
              <>
                <Separator />
                <div className="space-y-3">
                  <Label className="text-muted-foreground">Service Parameters</Label>
                  {currentParams.map((param) => (
                    <div key={param.key} className="space-y-1">
                      <Label className="text-xs">{param.label}</Label>
                      <Input
                        value={parameters[param.key] ?? ''}
                        onChange={(e) =>
                          onNodeUpdate(selectedNode.id, {
                            parameters: { ...parameters, [param.key]: e.target.value },
                          })
                        }
                        placeholder={param.placeholder}
                      />
                    </div>
                  ))}
                </div>
              </>
            )}
          </div>
        </div>

        {/* Footer */}
        <div className="border-t p-4">
          <Button
            variant="destructive"
            size="sm"
            className="w-full"
            onClick={() => onNodeDelete(selectedNode.id)}
          >
            <Trash2 className="mr-2 h-4 w-4" />
            Delete Node
          </Button>
        </div>
      </div>
    )
  }

  // Edge editing
  if (selectedEdge) {
    const sourceNode = nodes.find((n) => n.id === selectedEdge.source)
    const targetNode = nodes.find((n) => n.id === selectedEdge.target)
    const guard = selectedEdge.data?.guard ?? { type: 'success' as const }

    return (
      <div className="flex h-full w-80 flex-col border-l bg-card">
        {/* Header */}
        <div className="flex items-center justify-between border-b p-4">
          <span className="font-semibold">Edge Properties</span>
          <Button variant="ghost" size="icon" onClick={onClose}>
            <X className="h-4 w-4" />
          </Button>
        </div>

        {/* Content */}
        <div className="flex-1 overflow-y-auto p-4">
          <div className="space-y-4">
            {/* Connection Info */}
            <div className="rounded-md bg-muted/50 p-3">
              <div className="text-xs text-muted-foreground">Connection</div>
              <div className="mt-1 flex items-center gap-2 text-sm">
                <span className="font-medium">{sourceNode?.data.name ?? 'Unknown'}</span>
                <span className="text-muted-foreground">→</span>
                <span className="font-medium">{targetNode?.data.name ?? 'Unknown'}</span>
              </div>
            </div>

            {/* Guard Condition */}
            <div className="space-y-2">
              <Label>Trigger Condition</Label>
              <Select
                value={guard.type}
                onValueChange={(v) => {
                  const type = v as EdgeGuard['type']
                  const newGuard: EdgeGuard =
                    type === 'condition'
                      ? { type: 'condition', expression: '' }
                      : { type }
                  onEdgeUpdate(selectedEdge.id, { guard: newGuard })
                }}
              >
                <SelectTrigger>
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  {guardOptions.map((opt) => (
                    <SelectItem key={opt.value} value={opt.value}>
                      <span className={opt.color}>{opt.label}</span>
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>

            {/* Condition Expression */}
            {guard.type === 'condition' && (
              <div className="space-y-2">
                <Label>Condition Expression</Label>
                <Input
                  value={'expression' in guard ? guard.expression : ''}
                  onChange={(e) =>
                    onEdgeUpdate(selectedEdge.id, {
                      guard: { type: 'condition', expression: e.target.value },
                    })
                  }
                  placeholder="event.type === 'payment'"
                />
                <p className="text-xs text-muted-foreground">
                  JavaScript expression that evaluates to true/false
                </p>
              </div>
            )}
          </div>
        </div>

        {/* Footer */}
        <div className="border-t p-4">
          <Button
            variant="destructive"
            size="sm"
            className="w-full"
            onClick={() => onEdgeDelete(selectedEdge.id)}
          >
            <Trash2 className="mr-2 h-4 w-4" />
            Delete Edge
          </Button>
        </div>
      </div>
    )
  }

  return null
}
