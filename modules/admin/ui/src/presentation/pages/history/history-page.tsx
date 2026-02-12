import { useState } from 'react'
import {
  Activity,
  AlertCircle,
  ArrowRight,
  Calendar,
  CheckCircle2,
  ChevronDown,
  ChevronRight,
  Database,
  Filter,
  GitBranch,
  Globe,
  History,
  Play,
  RefreshCw,
  Search,
  Server,
  XCircle,
} from 'lucide-react'

import { cn } from '@/lib/utils'
import { Button } from '@/presentation/components/ui/button'
import { Input } from '@/presentation/components/ui/input'
import { Badge } from '@/presentation/components/ui/badge'
import { Card, CardContent, CardHeader, CardTitle } from '@/presentation/components/ui/card'
import { PageHeader } from '@/presentation/components/shared/page-header'
import { Breadcrumb } from '@/domain/tracing/entities/breadcrumb'

// History event types
type HistoryEventType =
  | 'dag.started'
  | 'dag.completed'
  | 'dag.failed'
  | 'node.entered'
  | 'node.exited'
  | 'node.error'
  | 'edge.traversed'
  | 'edge.retry'
  | 'http.request'
  | 'http.response'
  | 'kafka.consume'
  | 'kafka.produce'

interface HistoryEvent {
  id: string
  type: HistoryEventType
  timestamp: string
  title: string
  description?: string
  dagHash?: string
  traversalId?: string
  nodeId?: string
  serviceName?: string
  duration?: number
  metadata?: Record<string, unknown>
  breadcrumbs?: Breadcrumb[]
}

// Mock history events
const MOCK_HISTORY: HistoryEvent[] = [
  {
    id: '01HQX001',
    type: 'dag.started',
    timestamp: new Date(Date.now() - 1000 * 30).toISOString(),
    title: 'DAG traversal started',
    description: 'payment-processing DAG initiated from origin-http',
    dagHash: 'abc123def456',
    traversalId: '01HQX789ABC',
    serviceName: 'origin-http',
  },
  {
    id: '01HQX002',
    type: 'node.entered',
    timestamp: new Date(Date.now() - 1000 * 28).toISOString(),
    title: 'Entered node: processor-transform',
    traversalId: '01HQX789ABC',
    nodeId: 'node-001',
    serviceName: 'processor-transform',
  },
  {
    id: '01HQX003',
    type: 'http.request',
    timestamp: new Date(Date.now() - 1000 * 27).toISOString(),
    title: 'HTTP POST /v1/transform',
    description: 'Request to transformation service',
    traversalId: '01HQX789ABC',
    serviceName: 'processor-transform',
    metadata: { method: 'POST', path: '/v1/transform', statusCode: 200 },
  },
  {
    id: '01HQX004',
    type: 'node.exited',
    timestamp: new Date(Date.now() - 1000 * 25).toISOString(),
    title: 'Exited node: processor-transform',
    traversalId: '01HQX789ABC',
    nodeId: 'node-001',
    serviceName: 'processor-transform',
    duration: 3000,
  },
  {
    id: '01HQX005',
    type: 'edge.traversed',
    timestamp: new Date(Date.now() - 1000 * 24).toISOString(),
    title: 'Edge traversed: processor-transform → destination-webhook',
    traversalId: '01HQX789ABC',
    serviceName: 'waystation',
  },
  {
    id: '01HQX006',
    type: 'node.entered',
    timestamp: new Date(Date.now() - 1000 * 23).toISOString(),
    title: 'Entered node: destination-webhook',
    traversalId: '01HQX789ABC',
    nodeId: 'node-002',
    serviceName: 'destination-webhook',
  },
  {
    id: '01HQX007',
    type: 'http.request',
    timestamp: new Date(Date.now() - 1000 * 22).toISOString(),
    title: 'HTTP POST https://api.example.com/webhook',
    description: 'Delivering event to external webhook',
    traversalId: '01HQX789ABC',
    serviceName: 'destination-webhook',
    metadata: { method: 'POST', url: 'https://api.example.com/webhook' },
  },
  {
    id: '01HQX008',
    type: 'edge.retry',
    timestamp: new Date(Date.now() - 1000 * 20).toISOString(),
    title: 'Retry attempt 1/3',
    description: 'Connection timeout, retrying with exponential backoff',
    traversalId: '01HQX789ABC',
    serviceName: 'destination-webhook',
    metadata: { retryCount: 1, maxRetries: 3, delay: 1000 },
  },
  {
    id: '01HQX009',
    type: 'http.response',
    timestamp: new Date(Date.now() - 1000 * 18).toISOString(),
    title: 'HTTP 200 OK',
    description: 'Webhook delivery successful on retry',
    traversalId: '01HQX789ABC',
    serviceName: 'destination-webhook',
    duration: 4500,
    metadata: { statusCode: 200 },
  },
  {
    id: '01HQX010',
    type: 'node.exited',
    timestamp: new Date(Date.now() - 1000 * 17).toISOString(),
    title: 'Exited node: destination-webhook',
    traversalId: '01HQX789ABC',
    nodeId: 'node-002',
    serviceName: 'destination-webhook',
    duration: 6000,
  },
  {
    id: '01HQX011',
    type: 'dag.completed',
    timestamp: new Date(Date.now() - 1000 * 16).toISOString(),
    title: 'DAG traversal completed',
    description: 'All nodes processed successfully',
    dagHash: 'abc123def456',
    traversalId: '01HQX789ABC',
    duration: 14000,
  },
  {
    id: '01HQX012',
    type: 'dag.started',
    timestamp: new Date(Date.now() - 1000 * 60 * 2).toISOString(),
    title: 'DAG traversal started',
    description: 'notification-dispatch DAG initiated from origin-kafka',
    dagHash: 'xyz789ghi012',
    traversalId: '01HQX456DEF',
    serviceName: 'origin-kafka',
  },
  {
    id: '01HQX013',
    type: 'kafka.consume',
    timestamp: new Date(Date.now() - 1000 * 60 * 2 + 100).toISOString(),
    title: 'Kafka message consumed',
    description: 'Topic: events, Partition: 2, Offset: 12345',
    traversalId: '01HQX456DEF',
    serviceName: 'origin-kafka',
    metadata: { topic: 'events', partition: 2, offset: 12345 },
  },
  {
    id: '01HQX014',
    type: 'node.error',
    timestamp: new Date(Date.now() - 1000 * 60 * 2 + 5000).toISOString(),
    title: 'Error in node: processor-openai',
    description: 'OpenAI API rate limit exceeded',
    traversalId: '01HQX456DEF',
    nodeId: 'node-003',
    serviceName: 'processor-openai',
    metadata: { error: 'RateLimitError', code: 429 },
  },
  {
    id: '01HQX015',
    type: 'dag.failed',
    timestamp: new Date(Date.now() - 1000 * 60 * 2 + 6000).toISOString(),
    title: 'DAG traversal failed',
    description: 'Traversal aborted due to unrecoverable error',
    dagHash: 'xyz789ghi012',
    traversalId: '01HQX456DEF',
    duration: 6000,
  },
]

const EVENT_TYPE_CONFIG: Record<
  HistoryEventType,
  { icon: typeof Activity; color: string; label: string }
> = {
  'dag.started': { icon: Play, color: 'text-blue-500', label: 'DAG Started' },
  'dag.completed': { icon: CheckCircle2, color: 'text-green-500', label: 'DAG Completed' },
  'dag.failed': { icon: XCircle, color: 'text-red-500', label: 'DAG Failed' },
  'node.entered': { icon: ArrowRight, color: 'text-blue-400', label: 'Node Entered' },
  'node.exited': { icon: ChevronRight, color: 'text-gray-500', label: 'Node Exited' },
  'node.error': { icon: AlertCircle, color: 'text-red-500', label: 'Node Error' },
  'edge.traversed': { icon: GitBranch, color: 'text-purple-500', label: 'Edge Traversed' },
  'edge.retry': { icon: RefreshCw, color: 'text-yellow-500', label: 'Edge Retry' },
  'http.request': { icon: Globe, color: 'text-blue-400', label: 'HTTP Request' },
  'http.response': { icon: Globe, color: 'text-green-400', label: 'HTTP Response' },
  'kafka.consume': { icon: Database, color: 'text-orange-500', label: 'Kafka Consume' },
  'kafka.produce': { icon: Database, color: 'text-orange-400', label: 'Kafka Produce' },
}

function formatTimestamp(timestamp: string): string {
  const date = new Date(timestamp)
  return date.toLocaleTimeString('en-US', {
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
    hour12: false,
  })
}

function formatRelativeTime(timestamp: string): string {
  const now = Date.now()
  const then = new Date(timestamp).getTime()
  const diff = now - then

  const seconds = Math.floor(diff / 1000)
  const minutes = Math.floor(seconds / 60)
  const hours = Math.floor(minutes / 60)

  if (hours > 0) return `${hours}h ago`
  if (minutes > 0) return `${minutes}m ago`
  if (seconds > 0) return `${seconds}s ago`
  return 'just now'
}

function formatDuration(ms: number): string {
  if (ms < 1000) return `${ms}ms`
  if (ms < 60000) return `${(ms / 1000).toFixed(2)}s`
  return `${(ms / 60000).toFixed(2)}m`
}

function TimelineEvent({ event, isLast }: { event: HistoryEvent; isLast: boolean }) {
  const config = EVENT_TYPE_CONFIG[event.type]
  const Icon = config.icon
  const [expanded, setExpanded] = useState(false)

  const isError = event.type.includes('error') || event.type.includes('failed')
  const isSuccess = event.type.includes('completed')

  return (
    <div className="relative flex gap-4 pb-6">
      {/* Timeline line */}
      {!isLast && (
        <div className="absolute left-[15px] top-8 h-full w-px bg-border" />
      )}

      {/* Icon */}
      <div
        className={cn(
          'relative z-10 flex h-8 w-8 shrink-0 items-center justify-center rounded-full border bg-background',
          isError && 'border-red-500/50 bg-red-500/10',
          isSuccess && 'border-green-500/50 bg-green-500/10',
          !isError && !isSuccess && 'border-border'
        )}
      >
        <Icon className={cn('h-4 w-4', config.color)} />
      </div>

      {/* Content */}
      <div className="min-w-0 flex-1">
        <div className="flex items-start justify-between gap-4">
          <div className="min-w-0 flex-1">
            <button
              onClick={() => setExpanded(!expanded)}
              className="flex items-center gap-2 text-left"
            >
              <span className="font-medium">{event.title}</span>
              {event.metadata && (
                <ChevronDown
                  className={cn(
                    'h-4 w-4 text-muted-foreground transition-transform',
                    expanded && 'rotate-180'
                  )}
                />
              )}
            </button>
            {event.description && (
              <p className="mt-0.5 text-sm text-muted-foreground">{event.description}</p>
            )}
          </div>
          <div className="flex shrink-0 items-center gap-3 text-xs text-muted-foreground">
            {event.duration && (
              <Badge variant="secondary" className="font-mono">
                {formatDuration(event.duration)}
              </Badge>
            )}
            <span className="font-mono">{formatTimestamp(event.timestamp)}</span>
            <span>{formatRelativeTime(event.timestamp)}</span>
          </div>
        </div>

        {/* Tags */}
        <div className="mt-2 flex flex-wrap items-center gap-2">
          <Badge variant="outline" className="text-xs">
            {config.label}
          </Badge>
          {event.serviceName && (
            <Badge variant="secondary" className="text-xs">
              <Server className="mr-1 h-3 w-3" />
              {event.serviceName}
            </Badge>
          )}
          {event.traversalId && (
            <span className="font-mono text-xs text-muted-foreground">
              {event.traversalId.slice(0, 12)}...
            </span>
          )}
        </div>

        {/* Expanded metadata */}
        {expanded && event.metadata && (
          <div className="mt-3 rounded-md border bg-muted/50 p-3">
            <pre className="text-xs">
              {JSON.stringify(event.metadata, null, 2)}
            </pre>
          </div>
        )}
      </div>
    </div>
  )
}

export function HistoryPage() {
  const [searchQuery, setSearchQuery] = useState('')
  const [typeFilter, setTypeFilter] = useState<string>('all')
  const [isLive, setIsLive] = useState(true)

  const filteredEvents = MOCK_HISTORY.filter((event) => {
    if (typeFilter !== 'all') {
      if (typeFilter === 'dag' && !event.type.startsWith('dag.')) return false
      if (typeFilter === 'node' && !event.type.startsWith('node.')) return false
      if (typeFilter === 'http' && !event.type.startsWith('http.')) return false
      if (typeFilter === 'kafka' && !event.type.startsWith('kafka.')) return false
      if (typeFilter === 'error' && !event.type.includes('error') && !event.type.includes('failed')) return false
    }
    if (searchQuery) {
      const query = searchQuery.toLowerCase()
      return (
        event.title.toLowerCase().includes(query) ||
        event.description?.toLowerCase().includes(query) ||
        event.traversalId?.toLowerCase().includes(query) ||
        event.serviceName?.toLowerCase().includes(query)
      )
    }
    return true
  })

  // Group events by traversal for better organization
  const traversalGroups = filteredEvents.reduce(
    (acc, event) => {
      const key = event.traversalId || 'other'
      if (!acc[key]) acc[key] = []
      acc[key].push(event)
      return acc
    },
    {} as Record<string, HistoryEvent[]>
  )

  return (
    <div className="space-y-6">
      <PageHeader
        title="Event History"
        description="Real-time timeline of DAG traversals and events."
        actions={
          <div className="flex items-center gap-2">
            <Button
              variant={isLive ? 'default' : 'outline'}
              size="sm"
              onClick={() => setIsLive(!isLive)}
            >
              <Activity className={cn('mr-2 h-4 w-4', isLive && 'animate-pulse')} />
              {isLive ? 'Live' : 'Paused'}
            </Button>
            <Button variant="outline" size="sm">
              <Calendar className="mr-2 h-4 w-4" />
              Time Range
            </Button>
          </div>
        }
      />

      {/* Stats */}
      <div className="grid gap-4 md:grid-cols-4">
        <Card>
          <CardContent className="pt-4">
            <div className="text-2xl font-bold">{MOCK_HISTORY.length}</div>
            <p className="text-xs text-muted-foreground">Events (last hour)</p>
          </CardContent>
        </Card>
        <Card>
          <CardContent className="pt-4">
            <div className="text-2xl font-bold">
              {Object.keys(traversalGroups).filter((k) => k !== 'other').length}
            </div>
            <p className="text-xs text-muted-foreground">Traversals</p>
          </CardContent>
        </Card>
        <Card>
          <CardContent className="pt-4">
            <div className="text-2xl font-bold">
              {MOCK_HISTORY.filter((e) => e.type === 'dag.completed').length}
            </div>
            <p className="text-xs text-muted-foreground">Completed</p>
          </CardContent>
        </Card>
        <Card>
          <CardContent className="pt-4">
            <div className="text-2xl font-bold text-red-500">
              {MOCK_HISTORY.filter((e) => e.type.includes('error') || e.type.includes('failed')).length}
            </div>
            <p className="text-xs text-muted-foreground">Errors</p>
          </CardContent>
        </Card>
      </div>

      {/* Filters */}
      <div className="flex items-center gap-4">
        <div className="relative flex-1">
          <Search className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
          <Input
            placeholder="Search events, traversal IDs, services..."
            value={searchQuery}
            onChange={(e: React.ChangeEvent<HTMLInputElement>) => setSearchQuery(e.target.value)}
            className="pl-9"
          />
        </div>
        <div className="flex items-center gap-2">
          <Button
            variant={typeFilter === 'all' ? 'secondary' : 'ghost'}
            size="sm"
            onClick={() => setTypeFilter('all')}
          >
            All
          </Button>
          <Button
            variant={typeFilter === 'dag' ? 'secondary' : 'ghost'}
            size="sm"
            onClick={() => setTypeFilter('dag')}
          >
            <GitBranch className="mr-2 h-4 w-4" />
            DAG
          </Button>
          <Button
            variant={typeFilter === 'node' ? 'secondary' : 'ghost'}
            size="sm"
            onClick={() => setTypeFilter('node')}
          >
            <Server className="mr-2 h-4 w-4" />
            Node
          </Button>
          <Button
            variant={typeFilter === 'http' ? 'secondary' : 'ghost'}
            size="sm"
            onClick={() => setTypeFilter('http')}
          >
            <Globe className="mr-2 h-4 w-4" />
            HTTP
          </Button>
          <Button
            variant={typeFilter === 'error' ? 'secondary' : 'ghost'}
            size="sm"
            onClick={() => setTypeFilter('error')}
          >
            <AlertCircle className="mr-2 h-4 w-4" />
            Errors
          </Button>
        </div>
        <Button variant="outline" size="sm">
          <Filter className="mr-2 h-4 w-4" />
          More Filters
        </Button>
      </div>

      {/* Timeline */}
      <Card>
        <CardHeader className="border-b py-3">
          <div className="flex items-center justify-between">
            <CardTitle className="flex items-center gap-2 text-sm font-medium">
              <History className="h-4 w-4" />
              Event Timeline
            </CardTitle>
            <span className="text-sm text-muted-foreground">
              {filteredEvents.length} events
            </span>
          </div>
        </CardHeader>
        <CardContent className="pt-6">
          {filteredEvents.length === 0 ? (
            <div className="py-12 text-center text-muted-foreground">
              <History className="mx-auto h-12 w-12 opacity-50" />
              <p className="mt-4">No events found</p>
              <p className="text-sm">Try adjusting your filters or search query</p>
            </div>
          ) : (
            <div>
              {filteredEvents.map((event, index) => (
                <TimelineEvent
                  key={event.id}
                  event={event}
                  isLast={index === filteredEvents.length - 1}
                />
              ))}
            </div>
          )}
        </CardContent>
      </Card>
    </div>
  )
}
