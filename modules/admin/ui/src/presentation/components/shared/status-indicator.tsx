import { cn } from '@/lib/utils'

type Status = 'healthy' | 'degraded' | 'unhealthy' | 'unknown'

interface StatusIndicatorProps {
  status: Status
  label?: string
  showLabel?: boolean
  size?: 'sm' | 'md' | 'lg'
}

const statusColors: Record<Status, string> = {
  healthy: 'bg-green-500',
  degraded: 'bg-yellow-500',
  unhealthy: 'bg-red-500',
  unknown: 'bg-gray-400',
}

const statusLabels: Record<Status, string> = {
  healthy: 'Healthy',
  degraded: 'Degraded',
  unhealthy: 'Unhealthy',
  unknown: 'Unknown',
}

const sizeClasses = {
  sm: 'h-2 w-2',
  md: 'h-3 w-3',
  lg: 'h-4 w-4',
}

export function StatusIndicator({
  status,
  label,
  showLabel = true,
  size = 'md',
}: StatusIndicatorProps) {
  return (
    <div className="flex items-center gap-2">
      <span
        className={cn(
          'rounded-full',
          statusColors[status],
          sizeClasses[size],
          status === 'healthy' && 'animate-pulse'
        )}
      />
      {showLabel && (
        <span className="text-sm text-muted-foreground">
          {label ?? statusLabels[status]}
        </span>
      )}
    </div>
  )
}
