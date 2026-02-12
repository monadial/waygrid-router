import { useState } from 'react'
import { Globe, Cpu, Send, Settings } from 'lucide-react'

import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/presentation/components/ui/dialog'
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
import { cn } from '@/lib/utils'
import { NodeComponent, NodeService } from '@/domain/dag-editor/entities/editor-dag'
import { RetryConfig } from './dag-node'

export interface NodeConfig {
  component: NodeComponent
  service: NodeService
  name: string
  isEntry: boolean
  retry: RetryConfig
  parameters: Record<string, string>
}

interface NodeConfigDialogProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  initialConfig?: Partial<NodeConfig>
  onSave: (config: NodeConfig) => void
  mode: 'create' | 'edit'
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

const componentIcons: Record<NodeComponent, React.ElementType> = {
  origin: Globe,
  processor: Cpu,
  destination: Send,
  system: Settings,
}

const componentColors: Record<NodeComponent, string> = {
  origin: 'border-emerald-500 bg-emerald-50 text-emerald-700 dark:bg-emerald-950 dark:text-emerald-300',
  processor: 'border-blue-500 bg-blue-50 text-blue-700 dark:bg-blue-950 dark:text-blue-300',
  destination: 'border-purple-500 bg-purple-50 text-purple-700 dark:bg-purple-950 dark:text-purple-300',
  system: 'border-orange-500 bg-orange-50 text-orange-700 dark:bg-orange-950 dark:text-orange-300',
}

// Service-specific parameter definitions
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

export function NodeConfigDialog({
  open,
  onOpenChange,
  initialConfig,
  onSave,
  mode,
}: NodeConfigDialogProps) {
  const [component, setComponent] = useState<NodeComponent>(initialConfig?.component ?? 'origin')
  const [service, setService] = useState<NodeService>(initialConfig?.service ?? 'http')
  const [name, setName] = useState(initialConfig?.name ?? '')
  const [isEntry, setIsEntry] = useState(initialConfig?.isEntry ?? false)
  const [retryType, setRetryType] = useState<RetryConfig['type']>(initialConfig?.retry?.type ?? 'none')
  const [maxRetries, setMaxRetries] = useState(initialConfig?.retry?.maxRetries?.toString() ?? '3')
  const [retryDelay, setRetryDelay] = useState(initialConfig?.retry?.delay ?? '1s')
  const [parameters, setParameters] = useState<Record<string, string>>(
    (initialConfig?.parameters as Record<string, string>) ?? {}
  )

  const handleComponentChange = (newComponent: NodeComponent) => {
    setComponent(newComponent)
    // Reset service to first available for new component
    const services = servicesByComponent[newComponent]
    if (services.length > 0) {
      setService(services[0].value)
    }
    // Reset parameters when component changes
    setParameters({})
  }

  const handleSave = () => {
    const config: NodeConfig = {
      component,
      service,
      name: name || `${service}-${Date.now().toString(36)}`,
      isEntry: component === 'origin' ? isEntry : false,
      retry:
        retryType === 'none'
          ? { type: 'none' }
          : {
              type: retryType,
              maxRetries: parseInt(maxRetries, 10),
              delay: retryType === 'linear' ? retryDelay : undefined,
              baseDelay: retryType === 'exponential' ? retryDelay : undefined,
            },
      parameters,
    }
    onSave(config)
    onOpenChange(false)
  }

  const currentParams = serviceParameters[service] ?? []

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-lg">
        <DialogHeader>
          <DialogTitle>{mode === 'create' ? 'Add Node' : 'Edit Node'}</DialogTitle>
          <DialogDescription>
            Configure the node properties and parameters.
          </DialogDescription>
        </DialogHeader>

        <div className="grid gap-4 py-4">
          {/* Component Type Selection */}
          <div className="space-y-2">
            <Label>Component Type</Label>
            <div className="grid grid-cols-4 gap-2">
              {(Object.keys(servicesByComponent) as NodeComponent[]).map((comp) => {
                const Icon = componentIcons[comp]
                return (
                  <button
                    key={comp}
                    onClick={() => handleComponentChange(comp)}
                    className={cn(
                      'flex flex-col items-center gap-1 rounded-lg border-2 p-3 transition-all',
                      component === comp
                        ? componentColors[comp]
                        : 'border-transparent bg-muted/50 hover:bg-muted'
                    )}
                  >
                    <Icon className="h-5 w-5" />
                    <span className="text-xs font-medium capitalize">{comp}</span>
                  </button>
                )
              })}
            </div>
          </div>

          {/* Service Selection */}
          <div className="space-y-2">
            <Label htmlFor="service">Service</Label>
            <Select value={service} onValueChange={(v) => setService(v as NodeService)}>
              <SelectTrigger>
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                {servicesByComponent[component].map((svc) => (
                  <SelectItem key={svc.value} value={svc.value}>
                    {svc.label}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>

          {/* Node Name */}
          <div className="space-y-2">
            <Label htmlFor="name">Node Name</Label>
            <Input
              id="name"
              value={name}
              onChange={(e) => setName(e.target.value)}
              placeholder={`${service}-node`}
            />
          </div>

          {/* Entry Point (only for origins) */}
          {component === 'origin' && (
            <div className="flex items-center gap-2">
              <input
                type="checkbox"
                id="isEntry"
                checked={isEntry}
                onChange={(e) => setIsEntry(e.target.checked)}
                className="h-4 w-4 rounded border-gray-300"
              />
              <Label htmlFor="isEntry">Set as entry point</Label>
            </div>
          )}

          {/* Retry Configuration */}
          <div className="space-y-2">
            <Label>Retry Policy</Label>
            <Select value={retryType} onValueChange={(v) => setRetryType(v as RetryConfig['type'])}>
              <SelectTrigger>
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="none">No Retry</SelectItem>
                <SelectItem value="linear">Linear Backoff</SelectItem>
                <SelectItem value="exponential">Exponential Backoff</SelectItem>
              </SelectContent>
            </Select>

            {retryType !== 'none' && (
              <div className="grid grid-cols-2 gap-2 pt-2">
                <div className="space-y-1">
                  <Label htmlFor="maxRetries" className="text-xs">
                    Max Retries
                  </Label>
                  <Input
                    id="maxRetries"
                    type="number"
                    min="1"
                    max="10"
                    value={maxRetries}
                    onChange={(e) => setMaxRetries(e.target.value)}
                  />
                </div>
                <div className="space-y-1">
                  <Label htmlFor="retryDelay" className="text-xs">
                    {retryType === 'linear' ? 'Delay' : 'Base Delay'}
                  </Label>
                  <Input
                    id="retryDelay"
                    value={retryDelay}
                    onChange={(e) => setRetryDelay(e.target.value)}
                    placeholder="1s"
                  />
                </div>
              </div>
            )}
          </div>

          {/* Service-specific Parameters */}
          {currentParams.length > 0 && (
            <div className="space-y-3">
              <Label className="text-muted-foreground">Service Parameters</Label>
              {currentParams.map((param) => (
                <div key={param.key} className="space-y-1">
                  <Label htmlFor={param.key} className="text-xs">
                    {param.label}
                  </Label>
                  <Input
                    id={param.key}
                    value={parameters[param.key] ?? ''}
                    onChange={(e) =>
                      setParameters((prev) => ({ ...prev, [param.key]: e.target.value }))
                    }
                    placeholder={param.placeholder}
                  />
                </div>
              ))}
            </div>
          )}
        </div>

        <DialogFooter>
          <Button variant="outline" onClick={() => onOpenChange(false)}>
            Cancel
          </Button>
          <Button onClick={handleSave}>{mode === 'create' ? 'Add Node' : 'Save Changes'}</Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
