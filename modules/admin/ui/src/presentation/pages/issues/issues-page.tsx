import { useState } from 'react'
import { Link } from '@tanstack/react-router'
import {
  AlertCircle,
  CheckCircle2,
  Clock,
  Filter,
  MoreHorizontal,
  Search,
  TrendingUp,
  Archive,
  Eye,
} from 'lucide-react'

import { cn } from '@/lib/utils'
import { Button } from '@/presentation/components/ui/button'
import { Input } from '@/presentation/components/ui/input'
import { Badge } from '@/presentation/components/ui/badge'
import { Card, CardContent } from '@/presentation/components/ui/card'
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from '@/presentation/components/ui/dropdown-menu'
import { Checkbox } from '@/presentation/components/ui/checkbox'
import { PageHeader } from '@/presentation/components/shared/page-header'
import { TracedEvent, createTracedEvent, getEventDisplayId } from '@/domain/tracing'
import { getSeverityColor, getSeverityLabel } from '@/domain/tracing/value-objects/severity'

// Mock data for development
// Note: ULIDs use Crockford's Base32 (excludes I, L, O, U)
const MOCK_ISSUES: TracedEvent[] = [
  createTracedEvent({
    id: '01HQX123456789ABCDEFGHJK01',
    projectId: '01HQXK5M9ZYXWVTSRQPNMKP001',
    title: 'ConnectionError: Failed to connect to destination webhook',
    message: 'Connection refused to https://api.example.com/webhook',
    severity: 'error',
    status: 'unresolved',
    source: { type: 'node', name: 'destination-webhook', version: '1.2.0' },
    timestamp: new Date(Date.now() - 1000 * 60 * 5).toISOString(),
    environment: 'production',
    tags: { 'dag.hash': 'abc123', 'node.region': 'us-west-1' },
    count: 42,
    firstSeen: new Date(Date.now() - 1000 * 60 * 60 * 24 * 3).toISOString(),
    lastSeen: new Date(Date.now() - 1000 * 60 * 5).toISOString(),
  }),
  createTracedEvent({
    id: '01HQX123456789ABCDEFGHJK02',
    projectId: '01HQXK5M9ZYXWVTSRQPNMKP001',
    title: 'TimeoutError: DAG traversal exceeded maximum duration',
    message: 'Traversal 01HQX... timed out after 30000ms',
    severity: 'warning',
    status: 'unresolved',
    source: { type: 'dag', name: 'payment-processing', version: '2.0.0' },
    timestamp: new Date(Date.now() - 1000 * 60 * 15).toISOString(),
    environment: 'production',
    tags: { 'dag.hash': 'def456', 'traversal.id': '01HQX789' },
    count: 7,
    firstSeen: new Date(Date.now() - 1000 * 60 * 60 * 2).toISOString(),
    lastSeen: new Date(Date.now() - 1000 * 60 * 15).toISOString(),
  }),
  createTracedEvent({
    id: '01HQX123456789ABCDEFGHJK03',
    projectId: '01HQXK5M9ZYXWVTSRQPNMKP001',
    title: 'RetryExhausted: All retry attempts failed for edge',
    message: 'Edge from origin-http to processor-transform failed after 3 retries',
    severity: 'error',
    status: 'resolved',
    source: { type: 'node', name: 'origin-http', version: '1.0.0' },
    timestamp: new Date(Date.now() - 1000 * 60 * 60).toISOString(),
    environment: 'production',
    tags: { 'retry.policy': 'exponential', 'retry.count': '3' },
    count: 156,
    firstSeen: new Date(Date.now() - 1000 * 60 * 60 * 24 * 7).toISOString(),
    lastSeen: new Date(Date.now() - 1000 * 60 * 60).toISOString(),
  }),
  createTracedEvent({
    id: '01HQX123456789ABCDEFGHJK04',
    projectId: '01HQXK5M9ZYXWVTSRQPNMKP001',
    title: 'ValidationError: Invalid payload schema',
    message: 'Payload does not match expected JSON schema for event type "order.created"',
    severity: 'info',
    status: 'ignored',
    source: { type: 'system', name: 'schema-validator' },
    timestamp: new Date(Date.now() - 1000 * 60 * 60 * 3).toISOString(),
    environment: 'staging',
    tags: { 'schema.version': 'v2', 'event.type': 'order.created' },
    count: 23,
    firstSeen: new Date(Date.now() - 1000 * 60 * 60 * 24).toISOString(),
    lastSeen: new Date(Date.now() - 1000 * 60 * 60 * 3).toISOString(),
  }),
  createTracedEvent({
    id: '01HQX123456789ABCDEFGHJK05',
    projectId: '01HQXK5M9ZYXWVTSRQPNMKP001',
    title: 'FatalError: Kafka consumer group rebalance failed',
    message: 'Consumer group waygrid-origin lost all partitions during rebalance',
    severity: 'fatal',
    status: 'unresolved',
    source: { type: 'system', name: 'kafka-consumer' },
    timestamp: new Date(Date.now() - 1000 * 60 * 2).toISOString(),
    environment: 'production',
    tags: { 'kafka.group': 'waygrid-origin', 'kafka.topic': 'events' },
    count: 1,
    firstSeen: new Date(Date.now() - 1000 * 60 * 2).toISOString(),
    lastSeen: new Date(Date.now() - 1000 * 60 * 2).toISOString(),
  }),
]

function formatRelativeTime(timestamp: string): string {
  const now = Date.now()
  const then = new Date(timestamp).getTime()
  const diff = now - then

  const seconds = Math.floor(diff / 1000)
  const minutes = Math.floor(seconds / 60)
  const hours = Math.floor(minutes / 60)
  const days = Math.floor(hours / 24)

  if (days > 0) return `${days}d ago`
  if (hours > 0) return `${hours}h ago`
  if (minutes > 0) return `${minutes}m ago`
  return 'just now'
}

function IssueRow({
  issue,
  selected,
  onSelect,
}: {
  issue: TracedEvent
  selected: boolean
  onSelect: (checked: boolean) => void
}) {
  const severityColor = getSeverityColor(issue.severity)

  return (
    <div
      className={cn(
        'group flex items-start gap-4 border-b px-4 py-3 transition-colors hover:bg-muted/50',
        selected && 'bg-muted/30'
      )}
    >
      <Checkbox checked={selected} onCheckedChange={onSelect} className="mt-1" />

      <div
        className="mt-1.5 h-3 w-3 shrink-0 rounded-full"
        style={{ backgroundColor: severityColor }}
        title={getSeverityLabel(issue.severity)}
      />

      <div className="min-w-0 flex-1">
        <div className="flex items-start justify-between gap-4">
          <div className="min-w-0 flex-1">
            <Link
              to="/history"
              search={{ eventId: issue.id }}
              className="block truncate font-medium text-foreground hover:text-primary"
            >
              {issue.title}
            </Link>
            <p className="mt-0.5 truncate text-sm text-muted-foreground">{issue.message}</p>
          </div>

          <div className="flex shrink-0 items-center gap-2">
            <div className="text-right text-xs text-muted-foreground">
              <div className="flex items-center gap-1">
                <Clock className="h-3 w-3" />
                {formatRelativeTime(issue.lastSeen as string)}
              </div>
              <div className="mt-0.5 flex items-center gap-1">
                <TrendingUp className="h-3 w-3" />
                {issue.count} events
              </div>
            </div>

            <DropdownMenu>
              <DropdownMenuTrigger asChild>
                <Button
                  variant="ghost"
                  size="icon"
                  className="h-8 w-8 opacity-0 group-hover:opacity-100"
                >
                  <MoreHorizontal className="h-4 w-4" />
                </Button>
              </DropdownMenuTrigger>
              <DropdownMenuContent align="end">
                <DropdownMenuItem>
                  <CheckCircle2 className="mr-2 h-4 w-4" />
                  Resolve
                </DropdownMenuItem>
                <DropdownMenuItem>
                  <Eye className="mr-2 h-4 w-4" />
                  Ignore
                </DropdownMenuItem>
                <DropdownMenuItem>
                  <Archive className="mr-2 h-4 w-4" />
                  Archive
                </DropdownMenuItem>
                <DropdownMenuSeparator />
                <DropdownMenuItem className="text-destructive">Delete</DropdownMenuItem>
              </DropdownMenuContent>
            </DropdownMenu>
          </div>
        </div>

        <div className="mt-2 flex flex-wrap items-center gap-2">
          <Badge variant="outline" className="text-xs">
            {issue.source.name}
          </Badge>
          <Badge variant="secondary" className="text-xs">
            {issue.environment}
          </Badge>
          {issue.status === 'resolved' && (
            <Badge variant="success" className="text-xs">
              <CheckCircle2 className="mr-1 h-3 w-3" />
              Resolved
            </Badge>
          )}
          {issue.status === 'ignored' && (
            <Badge variant="secondary" className="text-xs">
              <Eye className="mr-1 h-3 w-3" />
              Ignored
            </Badge>
          )}
          <span className="text-xs text-muted-foreground">
            {getEventDisplayId(issue)}
          </span>
        </div>
      </div>
    </div>
  )
}

export function IssuesPage() {
  const [selectedIds, setSelectedIds] = useState<Set<string>>(new Set())
  const [searchQuery, setSearchQuery] = useState('')
  const [statusFilter, setStatusFilter] = useState<'all' | 'unresolved' | 'resolved' | 'ignored'>('all')

  const filteredIssues = MOCK_ISSUES.filter((issue) => {
    if (statusFilter !== 'all' && issue.status !== statusFilter) return false
    if (searchQuery && !issue.title.toLowerCase().includes(searchQuery.toLowerCase())) return false
    return true
  })

  const unresolvedCount = MOCK_ISSUES.filter((i) => i.status === 'unresolved').length

  const toggleSelect = (id: string, checked: boolean) => {
    const newSelected = new Set(selectedIds)
    if (checked) {
      newSelected.add(id)
    } else {
      newSelected.delete(id)
    }
    setSelectedIds(newSelected)
  }

  const selectAll = (checked: boolean) => {
    if (checked) {
      setSelectedIds(new Set(filteredIssues.map((i) => i.id as string)))
    } else {
      setSelectedIds(new Set())
    }
  }

  return (
    <div className="space-y-6">
      <PageHeader
        title="Issues"
        description="Track and resolve errors across your DAG traversals."
        actions={
          <Button>
            <AlertCircle className="mr-2 h-4 w-4" />
            Configure Alerts
          </Button>
        }
      />

      {/* Stats Cards */}
      <div className="grid gap-4 md:grid-cols-4">
        <Card>
          <CardContent className="pt-4">
            <div className="text-2xl font-bold">{unresolvedCount}</div>
            <p className="text-xs text-muted-foreground">Unresolved Issues</p>
          </CardContent>
        </Card>
        <Card>
          <CardContent className="pt-4">
            <div className="text-2xl font-bold">
              {MOCK_ISSUES.filter((i) => i.severity === 'fatal' || i.severity === 'error').length}
            </div>
            <p className="text-xs text-muted-foreground">Errors (24h)</p>
          </CardContent>
        </Card>
        <Card>
          <CardContent className="pt-4">
            <div className="text-2xl font-bold">
              {MOCK_ISSUES.reduce((sum, i) => sum + i.count, 0)}
            </div>
            <p className="text-xs text-muted-foreground">Total Events</p>
          </CardContent>
        </Card>
        <Card>
          <CardContent className="pt-4">
            <div className="text-2xl font-bold">98.2%</div>
            <p className="text-xs text-muted-foreground">DAG Success Rate</p>
          </CardContent>
        </Card>
      </div>

      {/* Filters and Search */}
      <div className="flex items-center gap-4">
        <div className="relative flex-1">
          <Search className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
          <Input
            placeholder="Search issues..."
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
            variant={statusFilter === 'unresolved' ? 'secondary' : 'ghost'}
            size="sm"
            onClick={() => setStatusFilter('unresolved')}
          >
            Unresolved
          </Button>
          <Button
            variant={statusFilter === 'resolved' ? 'secondary' : 'ghost'}
            size="sm"
            onClick={() => setStatusFilter('resolved')}
          >
            Resolved
          </Button>
          <Button
            variant={statusFilter === 'ignored' ? 'secondary' : 'ghost'}
            size="sm"
            onClick={() => setStatusFilter('ignored')}
          >
            Ignored
          </Button>
        </div>
        <Button variant="outline" size="sm">
          <Filter className="mr-2 h-4 w-4" />
          More Filters
        </Button>
      </div>

      {/* Issues List */}
      <Card>
        <div className="flex items-center justify-between border-b px-4 py-2">
          <div className="flex items-center gap-2">
            <Checkbox
              checked={selectedIds.size === filteredIssues.length && filteredIssues.length > 0}
              onCheckedChange={selectAll}
            />
            <span className="text-sm text-muted-foreground">
              {selectedIds.size > 0
                ? `${selectedIds.size} selected`
                : `${filteredIssues.length} issues`}
            </span>
          </div>
          {selectedIds.size > 0 && (
            <div className="flex items-center gap-2">
              <Button variant="ghost" size="sm">
                <CheckCircle2 className="mr-2 h-4 w-4" />
                Resolve
              </Button>
              <Button variant="ghost" size="sm">
                <Eye className="mr-2 h-4 w-4" />
                Ignore
              </Button>
            </div>
          )}
        </div>
        <div>
          {filteredIssues.map((issue) => (
            <IssueRow
              key={issue.id as string}
              issue={issue}
              selected={selectedIds.has(issue.id as string)}
              onSelect={(checked) => toggleSelect(issue.id as string, checked)}
            />
          ))}
          {filteredIssues.length === 0 && (
            <div className="py-12 text-center text-muted-foreground">
              <AlertCircle className="mx-auto h-12 w-12 opacity-50" />
              <p className="mt-4">No issues found</p>
              <p className="text-sm">Try adjusting your filters or search query</p>
            </div>
          )}
        </div>
      </Card>
    </div>
  )
}
