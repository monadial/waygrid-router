import {
  Activity,
  ArrowDown,
  ArrowUp,
  Clock,
  GitBranch,
  Server,
  TrendingUp,
  Zap,
} from 'lucide-react'

import { cn } from '@/lib/utils'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/presentation/components/ui/card'
import { PageHeader } from '@/presentation/components/shared/page-header'
import { Button } from '@/presentation/components/ui/button'
import { Badge } from '@/presentation/components/ui/badge'

interface StatCardProps {
  title: string
  value: string
  description: string
  trend?: {
    value: number
    label: string
  }
  icon: React.ReactNode
}

function StatCard({ title, value, description, trend, icon }: StatCardProps) {
  return (
    <Card>
      <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
        <CardTitle className="text-sm font-medium">{title}</CardTitle>
        {icon}
      </CardHeader>
      <CardContent>
        <div className="text-2xl font-bold">{value}</div>
        <p className="text-xs text-muted-foreground">{description}</p>
        {trend && (
          <div className="mt-2 flex items-center text-xs">
            {trend.value >= 0 ? (
              <ArrowUp className="mr-1 h-3 w-3 text-green-500" />
            ) : (
              <ArrowDown className="mr-1 h-3 w-3 text-red-500" />
            )}
            <span className={cn(trend.value >= 0 ? 'text-green-500' : 'text-red-500')}>
              {Math.abs(trend.value)}%
            </span>
            <span className="ml-1 text-muted-foreground">{trend.label}</span>
          </div>
        )}
      </CardContent>
    </Card>
  )
}

interface ServiceStat {
  name: string
  component: string
  requests: number
  avgLatency: number
  errorRate: number
  status: 'healthy' | 'degraded' | 'down'
}

const MOCK_SERVICES: ServiceStat[] = [
  { name: 'origin-http', component: 'origin', requests: 12450, avgLatency: 45, errorRate: 0.2, status: 'healthy' },
  { name: 'origin-kafka', component: 'origin', requests: 8920, avgLatency: 12, errorRate: 0.1, status: 'healthy' },
  { name: 'waystation', component: 'system', requests: 21370, avgLatency: 23, errorRate: 0.15, status: 'healthy' },
  { name: 'processor-transform', component: 'processor', requests: 15230, avgLatency: 89, errorRate: 0.5, status: 'degraded' },
  { name: 'processor-openai', component: 'processor', requests: 3420, avgLatency: 1234, errorRate: 1.2, status: 'healthy' },
  { name: 'destination-webhook', component: 'destination', requests: 18650, avgLatency: 156, errorRate: 2.3, status: 'degraded' },
  { name: 'destination-websocket', component: 'destination', requests: 2890, avgLatency: 5, errorRate: 0.05, status: 'healthy' },
]

function ServiceRow({ service }: { service: ServiceStat }) {
  return (
    <div className="flex items-center justify-between border-b px-4 py-3 last:border-0">
      <div className="flex items-center gap-3">
        <div
          className={cn(
            'h-2 w-2 rounded-full',
            service.status === 'healthy' && 'bg-green-500',
            service.status === 'degraded' && 'bg-yellow-500',
            service.status === 'down' && 'bg-red-500'
          )}
        />
        <div>
          <div className="font-medium">{service.name}</div>
          <div className="text-xs text-muted-foreground">{service.component}</div>
        </div>
      </div>
      <div className="flex items-center gap-6 text-sm">
        <div className="text-right">
          <div className="font-medium">{service.requests.toLocaleString()}</div>
          <div className="text-xs text-muted-foreground">requests</div>
        </div>
        <div className="text-right">
          <div className="font-medium">{service.avgLatency}ms</div>
          <div className="text-xs text-muted-foreground">avg latency</div>
        </div>
        <div className="text-right">
          <Badge
            variant={
              service.errorRate < 0.5 ? 'success' : service.errorRate < 2 ? 'warning' : 'destructive'
            }
          >
            {service.errorRate}% errors
          </Badge>
        </div>
      </div>
    </div>
  )
}

// Simple sparkline component for placeholder
function Sparkline({ data, color }: { data: number[]; color: string }) {
  const max = Math.max(...data)
  const min = Math.min(...data)
  const range = max - min || 1

  return (
    <div className="flex h-8 items-end gap-0.5">
      {data.map((value, i) => (
        <div
          key={i}
          className="w-1 rounded-t"
          style={{
            height: `${((value - min) / range) * 100}%`,
            minHeight: '4px',
            backgroundColor: color,
            opacity: 0.7 + (i / data.length) * 0.3,
          }}
        />
      ))}
    </div>
  )
}

export function StatsPage() {
  // Mock sparkline data
  const throughputData = [45, 52, 48, 65, 72, 68, 85, 92, 88, 95, 102, 98]
  const latencyData = [23, 25, 22, 28, 32, 29, 35, 38, 34, 40, 42, 39]
  const errorData = [2, 3, 2, 4, 3, 5, 4, 3, 2, 3, 4, 3]

  return (
    <div className="space-y-6">
      <PageHeader
        title="Stats"
        description="Monitor performance and throughput across your Waygrid services."
        actions={
          <div className="flex items-center gap-2">
            <Button variant="outline" size="sm">
              Last 24 hours
            </Button>
            <Button variant="outline" size="sm">
              Export
            </Button>
          </div>
        }
      />

      {/* Overview Stats */}
      <div className="grid gap-4 md:grid-cols-2 lg:grid-cols-4">
        <StatCard
          title="Throughput"
          value="24.5K/min"
          description="Events processed per minute"
          trend={{ value: 12.5, label: 'vs last hour' }}
          icon={<Zap className="h-4 w-4 text-muted-foreground" />}
        />
        <StatCard
          title="Avg Latency"
          value="45ms"
          description="End-to-end traversal time"
          trend={{ value: -8.2, label: 'vs last hour' }}
          icon={<Clock className="h-4 w-4 text-muted-foreground" />}
        />
        <StatCard
          title="Active DAGs"
          value="156"
          description="Currently deployed DAGs"
          trend={{ value: 3, label: 'new today' }}
          icon={<GitBranch className="h-4 w-4 text-muted-foreground" />}
        />
        <StatCard
          title="Error Rate"
          value="0.42%"
          description="Failed traversals"
          trend={{ value: -15.3, label: 'vs last hour' }}
          icon={<Activity className="h-4 w-4 text-muted-foreground" />}
        />
      </div>

      {/* Charts Row */}
      <div className="grid gap-4 md:grid-cols-3">
        <Card>
          <CardHeader className="pb-2">
            <CardTitle className="text-sm font-medium">Throughput (24h)</CardTitle>
            <CardDescription>Events per minute</CardDescription>
          </CardHeader>
          <CardContent>
            <div className="flex items-end justify-between">
              <div>
                <div className="text-2xl font-bold">24.5K</div>
                <div className="text-xs text-muted-foreground">current</div>
              </div>
              <Sparkline data={throughputData} color="hsl(var(--primary))" />
            </div>
          </CardContent>
        </Card>

        <Card>
          <CardHeader className="pb-2">
            <CardTitle className="text-sm font-medium">Latency (24h)</CardTitle>
            <CardDescription>p50 response time</CardDescription>
          </CardHeader>
          <CardContent>
            <div className="flex items-end justify-between">
              <div>
                <div className="text-2xl font-bold">45ms</div>
                <div className="text-xs text-muted-foreground">current</div>
              </div>
              <Sparkline data={latencyData} color="hsl(var(--chart-2))" />
            </div>
          </CardContent>
        </Card>

        <Card>
          <CardHeader className="pb-2">
            <CardTitle className="text-sm font-medium">Errors (24h)</CardTitle>
            <CardDescription>Failed events</CardDescription>
          </CardHeader>
          <CardContent>
            <div className="flex items-end justify-between">
              <div>
                <div className="text-2xl font-bold">342</div>
                <div className="text-xs text-muted-foreground">total</div>
              </div>
              <Sparkline data={errorData} color="hsl(var(--destructive))" />
            </div>
          </CardContent>
        </Card>
      </div>

      {/* Services Table */}
      <Card>
        <CardHeader>
          <CardTitle className="flex items-center gap-2">
            <Server className="h-5 w-5" />
            Service Health
          </CardTitle>
          <CardDescription>Real-time status of all Waygrid services</CardDescription>
        </CardHeader>
        <div>
          {MOCK_SERVICES.map((service) => (
            <ServiceRow key={service.name} service={service} />
          ))}
        </div>
      </Card>

      {/* Top DAGs */}
      <Card>
        <CardHeader>
          <CardTitle className="flex items-center gap-2">
            <TrendingUp className="h-5 w-5" />
            Top DAGs by Volume
          </CardTitle>
          <CardDescription>Most active DAG traversals in the last 24 hours</CardDescription>
        </CardHeader>
        <CardContent>
          <div className="space-y-4">
            {[
              { name: 'payment-processing', hash: 'abc123', count: 45230, avgLatency: 89 },
              { name: 'notification-dispatch', hash: 'def456', count: 32150, avgLatency: 156 },
              { name: 'order-fulfillment', hash: 'ghi789', count: 28900, avgLatency: 234 },
              { name: 'user-onboarding', hash: 'jkl012', count: 15670, avgLatency: 67 },
              { name: 'analytics-pipeline', hash: 'mno345', count: 12340, avgLatency: 1890 },
            ].map((dag, index) => (
              <div key={dag.hash} className="flex items-center gap-4">
                <div className="flex h-8 w-8 items-center justify-center rounded-md bg-muted text-sm font-bold">
                  {index + 1}
                </div>
                <div className="flex-1">
                  <div className="font-medium">{dag.name}</div>
                  <div className="text-xs text-muted-foreground font-mono">{dag.hash}</div>
                </div>
                <div className="text-right text-sm">
                  <div className="font-medium">{dag.count.toLocaleString()}</div>
                  <div className="text-xs text-muted-foreground">traversals</div>
                </div>
                <div className="text-right text-sm">
                  <div className="font-medium">{dag.avgLatency}ms</div>
                  <div className="text-xs text-muted-foreground">avg latency</div>
                </div>
              </div>
            ))}
          </div>
        </CardContent>
      </Card>
    </div>
  )
}
