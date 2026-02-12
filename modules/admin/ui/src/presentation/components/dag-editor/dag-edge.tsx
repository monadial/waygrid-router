import { memo } from 'react'
import { EdgeProps, getBezierPath, EdgeLabelRenderer, BaseEdge } from 'reactflow'

import { cn } from '@/lib/utils'
import { EdgeGuard } from '@/domain/dag-editor/entities/editor-dag'

export interface DagEdgeData {
  guard: EdgeGuard
}

const guardConfig: Record<
  EdgeGuard['type'],
  { label: string; color: string; bgColor: string }
> = {
  success: {
    label: 'success',
    color: 'text-emerald-700 dark:text-emerald-300',
    bgColor: 'bg-emerald-100 dark:bg-emerald-900/50',
  },
  failure: {
    label: 'failure',
    color: 'text-red-700 dark:text-red-300',
    bgColor: 'bg-red-100 dark:bg-red-900/50',
  },
  any: {
    label: 'any',
    color: 'text-slate-700 dark:text-slate-300',
    bgColor: 'bg-slate-100 dark:bg-slate-900/50',
  },
  timeout: {
    label: 'timeout',
    color: 'text-amber-700 dark:text-amber-300',
    bgColor: 'bg-amber-100 dark:bg-amber-900/50',
  },
  condition: {
    label: 'condition',
    color: 'text-violet-700 dark:text-violet-300',
    bgColor: 'bg-violet-100 dark:bg-violet-900/50',
  },
}

const guardStrokeColors: Record<EdgeGuard['type'], string> = {
  success: '#10b981', // emerald-500
  failure: '#ef4444', // red-500
  any: '#64748b', // slate-500
  timeout: '#f59e0b', // amber-500
  condition: '#8b5cf6', // violet-500
}

function DagEdgeComponent({
  id,
  sourceX,
  sourceY,
  targetX,
  targetY,
  sourcePosition,
  targetPosition,
  data,
  selected,
}: EdgeProps<DagEdgeData>) {
  const [edgePath, labelX, labelY] = getBezierPath({
    sourceX,
    sourceY,
    sourcePosition,
    targetX,
    targetY,
    targetPosition,
  })

  const guard = data?.guard ?? { type: 'success' as const }
  const config = guardConfig[guard.type]
  const strokeColor = guardStrokeColors[guard.type]

  const label = guard.type === 'condition' && 'expression' in guard
    ? `if: ${guard.expression}`
    : config.label

  return (
    <>
      <BaseEdge
        id={id}
        path={edgePath}
        style={{
          stroke: strokeColor,
          strokeWidth: selected ? 3 : 2,
        }}
      />
      <EdgeLabelRenderer>
        <div
          style={{
            position: 'absolute',
            transform: `translate(-50%, -50%) translate(${labelX}px,${labelY}px)`,
            pointerEvents: 'all',
          }}
          className="nodrag nopan"
        >
          <span
            className={cn(
              'rounded-full px-2 py-0.5 text-[10px] font-medium',
              config.bgColor,
              config.color,
              selected && 'ring-2 ring-primary'
            )}
          >
            {label}
          </span>
        </div>
      </EdgeLabelRenderer>
    </>
  )
}

export const DagEdge = memo(DagEdgeComponent)
