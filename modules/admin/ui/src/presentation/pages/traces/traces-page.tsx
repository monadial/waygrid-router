import { useState } from 'react'
import {
  Activity,
  ArrowRight,
  Clock,
  Filter,
  GitBranch,
  Search,
  CheckCircle,
  XCircle,
} from 'lucide-react'

import { Button } from '@/presentation/components/ui/button'
import { Input } from '@/presentation/components/ui/input'
import { Badge } from '@/presentation/components/ui/badge'
import { Card, CardContent, CardHeader, CardTitle } from '@/presentation/components/ui/card'
import { PageHeader } from '@/presentation/components/shared/page-header'
import { formatDuration } from '@/domain/tracing/entities/span'

// Mock traces for development
interface TraceOverview {
  traceId: string
  rootOperation: string
  rootService: string
  startTime: string
  duration: number
  spanCount: number
  status: 'ok' | 'error' | 'unset'
  services: string[]
}

const MOCK_TRACES: TraceOverview[] = [
  {
    traceId: '4bf92f3577b34da6a3ce929d0e0e4736',
    rootOperation: 'POST /v1/ingest',
    rootService: 'origin-http',
    startTime: new Date(Date.now() - 1000 * 60 * 2).toISOString(),
    duration: 1234,
    spanCount: 8,
    status: 'ok',
    services: ['origin-http', 'waystation', 'processor-transform', 'destination-webhook'],
  },
  {
    traceId: '5cf92f3577b34da6a3ce929d0e0e4737',
    rootOperation: 'Kafka consume',
    rootService: 'origin-kafka',
    startTime: new Date(Date.now() - 1000 * 60 * 5).toISOString(),
    duration: 3456,
    spanCount: 12,
    status: 'error',
    services: ['origin-kafka', 'waystation', 'processor-openai', 'destination-webhook'],
  },
  {
    traceId: '6df92f3577b34da6a3ce929d0e0e4738',
    rootOperation: 'gRPC receive',
    rootService: 'origin-grpc',
    startTime: new Date(Date.now() - 1000 * 60 * 8).toISOString(),
    duration: 567,
    spanCount: 5,
    status: 'ok',
    services: ['origin-grpc', 'waystation', 'destination-websocket'],
  },
  {
    traceId: '7ef92f3577b34da6a3ce929d0e0e4739',
    rootOperation: 'POST /v1/ingest',
    rootService: 'origin-http',
    startTime: new Date(Date.now() - 1000 * 60 * 12).toISOString(),
    duration: 8901,
    spanCount: 15,
    status: 'error',
    services: ['origin-http', 'waystation', 'processor-transform', 'processor-openai', 'destination-webhook'],
  },
  {
    traceId: '8ff92f3577b34da6a3ce929d0e0e4740',
    rootOperation: 'Scheduled job',
    rootService: 'scheduler',
    startTime: new Date(Date.now() - 1000 * 60 * 20).toISOString(),
    duration: 234,
    spanCount: 3,
    status: 'ok',
    services: ['scheduler', 'waystation', 'destination-blackhole'],
  },
]

function formatRelativeTime(timestamp: string): string {
  const now = Date.now()
  const then = new Date(timestamp).getTime()
  const diff = now - then

  const seconds = Math.floor(diff / 1000)
  const minutes = Math.floor(seconds / 60)
  const hours = Math.floor(minutes / 60)

  if (hours > 0) return `${hours}h ago`
  if (minutes > 0) return `${minutes}m ago`
  return 'just now'
}

function TraceRow({ trace }: { trace: TraceOverview }) {
  return (
    <div className="group flex items-center gap-4 border-b px-4 py-3 transition-colors hover:bg-muted/50">
      {/* Status indicator */}
      <div className="shrink-0">
        {trace.status === 'ok' ? (
          <CheckCircle className="h-5 w-5 text-green-500" />
        ) : (
          <XCircle className="h-5 w-5 text-red-500" />
        )}
      </div>

      {/* Trace info */}
      <div className="min-w-0 flex-1">
        <div className="flex items-center gap-2">
          <span className="font-medium">{trace.rootOperation}</span>
          <span className="text-muted-foreground">-</span>
          <span className="text-sm text-muted-foreground">{trace.rootService}</span>
        </div>
        <div className="mt-1 flex items-center gap-4 text-xs text-muted-foreground">
          <span className="font-mono">{trace.traceId.slice(0, 16)}...</span>
          <span className="flex items-center gap-1">
            <Clock className="h-3 w-3" />
            {formatRelativeTime(trace.startTime)}
          </span>
        </div>
      </div>

      {/* Service flow */}
      <div className="hidden shrink-0 items-center gap-1 md:flex">
        {trace.services.slice(0, 4).map((service, index) => (
          <div key={service} className="flex items-center">
            {index > 0 && <ArrowRight className="h-3 w-3 text-muted-foreground" />}
            <Badge variant="outline" className="text-xs">
              {service.replace(/^(origin|processor|destination)-/, '')}
            </Badge>
          </div>
        ))}
        {trace.services.length > 4 && (
          <Badge variant="secondary" className="text-xs">
            +{trace.services.length - 4}
          </Badge>
        )}
      </div>

      {/* Stats */}
      <div className="flex shrink-0 items-center gap-4 text-sm">
        <div className="text-right">
          <div className="font-medium">{formatDuration(trace.duration)}</div>
          <div className="text-xs text-muted-foreground">duration</div>
        </div>
        <div className="text-right">
          <div className="font-medium">{trace.spanCount}</div>
          <div className="text-xs text-muted-foreground">spans</div>
        </div>
      </div>
    </div>
  )
}

export function TracesPage() {
  const [searchQuery, setSearchQuery] = useState('')
  const [statusFilter, setStatusFilter] = useState<'all' | 'ok' | 'error'>('all')

  const filteredTraces = MOCK_TRACES.filter((trace) => {
    if (statusFilter !== 'all' && trace.status !== statusFilter) return false
    if (searchQuery) {
      const query = searchQuery.toLowerCase()
      return (
        trace.traceId.toLowerCase().includes(query) ||
        trace.rootOperation.toLowerCase().includes(query) ||
        trace.rootService.toLowerCase().includes(query)
      )
    }
    return true
  })

  return (
    <div className="space-y-6">
      <PageHeader
        title="Traces"
        description="Distributed tracing for DAG traversals and event flows."
        actions={
          <Button variant="outline">
            <Activity className="mr-2 h-4 w-4" />
            Live Tail
          </Button>
        }
      />

      {/* Stats Cards */}
      <div className="grid gap-4 md:grid-cols-4">
        <Card>
          <CardContent className="pt-4">
            <div className="text-2xl font-bold">{MOCK_TRACES.length}</div>
            <p className="text-xs text-muted-foreground">Traces (last hour)</p>
          </CardContent>
        </Card>
        <Card>
          <CardContent className="pt-4">
            <div className="text-2xl font-bold">
              {Math.round(MOCK_TRACES.reduce((sum, t) => sum + t.duration, 0) / MOCK_TRACES.length)}ms
            </div>
            <p className="text-xs text-muted-foreground">Avg Duration</p>
          </CardContent>
        </Card>
        <Card>
          <CardContent className="pt-4">
            <div className="text-2xl font-bold">
              {MOCK_TRACES.reduce((sum, t) => sum + t.spanCount, 0)}
            </div>
            <p className="text-xs text-muted-foreground">Total Spans</p>
          </CardContent>
        </Card>
        <Card>
          <CardContent className="pt-4">
            <div className="text-2xl font-bold">
              {Math.round((MOCK_TRACES.filter((t) => t.status === 'ok').length / MOCK_TRACES.length) * 100)}%
            </div>
            <p className="text-xs text-muted-foreground">Success Rate</p>
          </CardContent>
        </Card>
      </div>

      {/* Filters and Search */}
      <div className="flex items-center gap-4">
        <div className="relative flex-1">
          <Search className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
          <Input
            placeholder="Search by trace ID, operation, or service..."
            value={searchQuery}
            onChange={(e: React.ChangeEvent<HTMLInputElement>) => setSearchQuery(e.target.value)}
            className="pl-9"
          />
        </div>
        <div className="flex items-center gap-2">
          <Button
            variant={statusFilter === 'all' ? 'secondary' : 'ghost'}
            size="sm"
            onClick={() => setStatusFilter('all')}
          >
            All
          </Button>
          <Button
            variant={statusFilter === 'ok' ? 'secondary' : 'ghost'}
            size="sm"
            onClick={() => setStatusFilter('ok')}
          >
            <CheckCircle className="mr-2 h-4 w-4 text-green-500" />
            Successful
          </Button>
          <Button
            variant={statusFilter === 'error' ? 'secondary' : 'ghost'}
            size="sm"
            onClick={() => setStatusFilter('error')}
          >
            <XCircle className="mr-2 h-4 w-4 text-red-500" />
            Errors
          </Button>
        </div>
        <Button variant="outline" size="sm">
          <Filter className="mr-2 h-4 w-4" />
          More Filters
        </Button>
      </div>

      {/* Traces List */}
      <Card>
        <CardHeader className="border-b py-3">
          <div className="flex items-center justify-between">
            <CardTitle className="flex items-center gap-2 text-sm font-medium">
              <GitBranch className="h-4 w-4" />
              Recent Traces
            </CardTitle>
            <span className="text-sm text-muted-foreground">
              {filteredTraces.length} traces
            </span>
          </div>
        </CardHeader>
        <div>
          {filteredTraces.map((trace) => (
            <TraceRow key={trace.traceId} trace={trace} />
          ))}
          {filteredTraces.length === 0 && (
            <div className="py-12 text-center text-muted-foreground">
              <GitBranch className="mx-auto h-12 w-12 opacity-50" />
              <p className="mt-4">No traces found</p>
              <p className="text-sm">Try adjusting your filters or search query</p>
            </div>
          )}
        </div>
      </Card>
    </div>
  )
}
