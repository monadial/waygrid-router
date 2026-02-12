import { memo } from 'react'
import { Handle, Position, NodeProps } from 'reactflow'
import { Globe, Cpu, Send, Settings, RotateCcw } from 'lucide-react'

import { cn } from '@/lib/utils'
import { NodeComponent, NodeService } from '@/domain/dag-editor/entities/editor-dag'

export interface RetryConfig {
  type: 'none' | 'linear' | 'exponential'
  maxRetries?: number
  delay?: string
  baseDelay?: string
}

export interface DagNodeData {
  component: NodeComponent
  service: NodeService
  name: string
  isEntry?: boolean
  parameters?: Record<string, unknown>
  retry?: RetryConfig
}

const componentConfig: Record<
  NodeComponent,
  {
    icon: React.ElementType
    color: string
    bgColor: string
    borderColor: string
    hasInput: boolean
    hasOutput: boolean
  }
> = {
  origin: {
    icon: Globe,
    color: 'text-emerald-600 dark:text-emerald-400',
    bgColor: 'bg-emerald-50 dark:bg-emerald-950/50',
    borderColor: 'border-emerald-200 dark:border-emerald-800',
    hasInput: false, // Origins only produce events
    hasOutput: true,
  },
  processor: {
    icon: Cpu,
    color: 'text-blue-600 dark:text-blue-400',
    bgColor: 'bg-blue-50 dark:bg-blue-950/50',
    borderColor: 'border-blue-200 dark:border-blue-800',
    hasInput: true,
    hasOutput: true,
  },
  destination: {
    icon: Send,
    color: 'text-purple-600 dark:text-purple-400',
    bgColor: 'bg-purple-50 dark:bg-purple-950/50',
    borderColor: 'border-purple-200 dark:border-purple-800',
    hasInput: true,
    hasOutput: false, // Destinations only consume events
  },
  system: {
    icon: Settings,
    color: 'text-orange-600 dark:text-orange-400',
    bgColor: 'bg-orange-50 dark:bg-orange-950/50',
    borderColor: 'border-orange-200 dark:border-orange-800',
    hasInput: true,
    hasOutput: true,
  },
}

function DagNodeComponent({ data, selected }: NodeProps<DagNodeData>) {
  const config = componentConfig[data.component]
  const Icon = config.icon
  const hasRetry = data.retry && data.retry.type !== 'none'

  return (
    <div
      className={cn(
        'relative min-w-[180px] rounded-lg border-2 shadow-sm transition-all',
        config.bgColor,
        config.borderColor,
        selected && 'ring-2 ring-primary ring-offset-2'
      )}
    >
      {/* Entry indicator */}
      {data.isEntry && (
        <div className="absolute -top-2 left-1/2 -translate-x-1/2">
          <span className="rounded-full bg-emerald-500 px-2 py-0.5 text-[10px] font-medium text-white">
            ENTRY
          </span>
        </div>
      )}

      {/* Retry indicator */}
      {hasRetry && (
        <div className="absolute -top-2 right-2">
          <span className="flex items-center gap-1 rounded-full bg-amber-500 px-2 py-0.5 text-[10px] font-medium text-white">
            <RotateCcw className="h-2.5 w-2.5" />
            {data.retry?.maxRetries ?? 3}x
          </span>
        </div>
      )}

      {/* Input handle - only for non-origin nodes */}
      {config.hasInput && (
        <Handle
          type="target"
          position={Position.Left}
          className={cn(
            '!h-3 !w-3 !border-2 !border-background !bg-muted-foreground',
            'hover:!bg-primary'
          )}
        />
      )}

      {/* Node content */}
      <div className="p-3">
        <div className="flex items-center gap-2">
          <div className={cn('rounded-md p-1.5', config.bgColor)}>
            <Icon className={cn('h-4 w-4', config.color)} />
          </div>
          <div className="flex flex-col">
            <span className="text-xs font-medium text-muted-foreground">
              {data.component}.{data.service}
            </span>
            <span className="text-sm font-semibold">{data.name}</span>
          </div>
        </div>
      </div>

      {/* Output handle - only for non-destination nodes */}
      {config.hasOutput && (
        <Handle
          type="source"
          position={Position.Right}
          className={cn(
            '!h-3 !w-3 !border-2 !border-background !bg-muted-foreground',
            'hover:!bg-primary'
          )}
        />
      )}
    </div>
  )
}

export const DagNode = memo(DagNodeComponent)
