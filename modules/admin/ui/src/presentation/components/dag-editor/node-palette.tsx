import { Globe, Cpu, Send, Settings, GripVertical } from 'lucide-react'

import { cn } from '@/lib/utils'
import { NodeComponent, NodeService } from '@/domain/dag-editor/entities/editor-dag'

export interface NodeTemplate {
  component: NodeComponent
  service: NodeService
  label: string
}

const nodeTemplates: NodeTemplate[] = [
  // Origins
  { component: 'origin', service: 'http', label: 'HTTP Origin' },
  { component: 'origin', service: 'kafka', label: 'Kafka Origin' },
  { component: 'origin', service: 'grpc', label: 'gRPC Origin' },
  // Processors
  { component: 'processor', service: 'transform', label: 'Transform' },
  { component: 'processor', service: 'openai', label: 'OpenAI' },
  // Destinations
  { component: 'destination', service: 'webhook', label: 'Webhook' },
  { component: 'destination', service: 'websocket', label: 'WebSocket' },
  // System
  { component: 'system', service: 'scheduler', label: 'Scheduler' },
  { component: 'system', service: 'topology', label: 'Topology' },
]

const componentIcons: Record<NodeComponent, React.ElementType> = {
  origin: Globe,
  processor: Cpu,
  destination: Send,
  system: Settings,
}

const componentColors: Record<NodeComponent, string> = {
  origin: 'text-emerald-600 dark:text-emerald-400 bg-emerald-50 dark:bg-emerald-950/50',
  processor: 'text-blue-600 dark:text-blue-400 bg-blue-50 dark:bg-blue-950/50',
  destination: 'text-purple-600 dark:text-purple-400 bg-purple-50 dark:bg-purple-950/50',
  system: 'text-orange-600 dark:text-orange-400 bg-orange-50 dark:bg-orange-950/50',
}

interface NodePaletteProps {
  onDragStart: (template: NodeTemplate) => void
}

export function NodePalette({ onDragStart }: NodePaletteProps) {
  const groupedTemplates = nodeTemplates.reduce(
    (acc, template) => {
      if (!acc[template.component]) {
        acc[template.component] = []
      }
      acc[template.component].push(template)
      return acc
    },
    {} as Record<NodeComponent, NodeTemplate[]>
  )

  return (
    <div className="flex h-full w-64 flex-col border-r bg-card">
      <div className="border-b p-4">
        <h3 className="text-sm font-semibold">Node Palette</h3>
        <p className="text-xs text-muted-foreground">Drag nodes onto the canvas</p>
      </div>
      <div className="flex-1 overflow-y-auto p-2">
        {(Object.entries(groupedTemplates) as [NodeComponent, NodeTemplate[]][]).map(
          ([component, templates]) => {
            const Icon = componentIcons[component]
            return (
              <div key={component} className="mb-4">
                <div className="mb-2 flex items-center gap-2 px-2">
                  <Icon className={cn('h-3.5 w-3.5', componentColors[component].split(' ')[0])} />
                  <span className="text-xs font-medium uppercase tracking-wider text-muted-foreground">
                    {component}
                  </span>
                </div>
                <div className="space-y-1">
                  {templates.map((template) => (
                    <PaletteItem
                      key={`${template.component}.${template.service}`}
                      template={template}
                      onDragStart={onDragStart}
                    />
                  ))}
                </div>
              </div>
            )
          }
        )}
      </div>
    </div>
  )
}

interface PaletteItemProps {
  template: NodeTemplate
  onDragStart: (template: NodeTemplate) => void
}

function PaletteItem({ template, onDragStart }: PaletteItemProps) {
  const Icon = componentIcons[template.component]
  const colorClasses = componentColors[template.component]

  const handleDragStart = (event: React.DragEvent) => {
    event.dataTransfer.setData('application/reactflow', JSON.stringify(template))
    event.dataTransfer.effectAllowed = 'move'
    onDragStart(template)
  }

  return (
    <div
      draggable
      onDragStart={handleDragStart}
      className={cn(
        'flex cursor-grab items-center gap-2 rounded-md border px-2 py-1.5 transition-colors',
        'hover:bg-accent active:cursor-grabbing',
        'border-transparent hover:border-border'
      )}
    >
      <GripVertical className="h-3.5 w-3.5 text-muted-foreground/50" />
      <div className={cn('rounded p-1', colorClasses)}>
        <Icon className="h-3.5 w-3.5" />
      </div>
      <span className="text-sm">{template.label}</span>
    </div>
  )
}
