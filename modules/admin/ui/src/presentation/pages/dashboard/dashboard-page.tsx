import { Activity, CheckCircle, XCircle, GitBranch, Clock, Network } from 'lucide-react'

import { Card, CardContent, CardHeader, CardTitle } from '@/presentation/components/ui/card'
import { Badge } from '@/presentation/components/ui/badge'
import { Skeleton } from '@/presentation/components/ui/skeleton'
import { PageHeader } from '@/presentation/components/shared/page-header'
import { useServicesHealth } from '@/presentation/hooks'

export function DashboardPage() {
  const { data: services, isLoading, error } = useServicesHealth()

  return (
    <div className="space-y-6">
      <PageHeader
        title="Dashboard"
        description="Monitor your Waygrid Router system health and performance."
      />

      {/* Service Health Grid */}
      <div>
        <h2 className="mb-4 text-lg font-semibold">Service Health</h2>
        <div className="grid gap-4 md:grid-cols-2 lg:grid-cols-3">
          {isLoading ? (
            Array.from({ length: 6 }).map((_, i) => (
              <Card key={i}>
                <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
                  <Skeleton className="h-4 w-24" />
                  <Skeleton className="h-4 w-4 rounded-full" />
                </CardHeader>
                <CardContent>
                  <Skeleton className="h-6 w-16" />
                </CardContent>
              </Card>
            ))
          ) : error ? (
            <Card className="col-span-full">
              <CardContent className="pt-6">
                <p className="text-sm text-muted-foreground">
                  Failed to load service health. Make sure backend services are running.
                </p>
              </CardContent>
            </Card>
          ) : (
            services?.map((service) => (
              <Card key={service.name}>
                <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
                  <CardTitle className="text-sm font-medium capitalize">
                    {service.name.replace('-', ' ')}
                  </CardTitle>
                  {service.healthy ? (
                    <CheckCircle className="h-4 w-4 text-green-500" />
                  ) : (
                    <XCircle className="h-4 w-4 text-destructive" />
                  )}
                </CardHeader>
                <CardContent>
                  <Badge variant={service.healthy ? 'success' : 'destructive'}>
                    {service.healthy ? 'Healthy' : 'Unhealthy'}
                  </Badge>
                </CardContent>
              </Card>
            ))
          )}
        </div>
      </div>

      {/* Quick Stats */}
      <div>
        <h2 className="mb-4 text-lg font-semibold">Quick Stats</h2>
        <div className="grid gap-4 md:grid-cols-2 lg:grid-cols-4">
          <Card>
            <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
              <CardTitle className="text-sm font-medium">Active Traversals</CardTitle>
              <Activity className="h-4 w-4 text-muted-foreground" />
            </CardHeader>
            <CardContent>
              <div className="text-2xl font-bold">--</div>
              <p className="text-xs text-muted-foreground">Connect to backend to view</p>
            </CardContent>
          </Card>

          <Card>
            <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
              <CardTitle className="text-sm font-medium">Registered DAGs</CardTitle>
              <GitBranch className="h-4 w-4 text-muted-foreground" />
            </CardHeader>
            <CardContent>
              <div className="text-2xl font-bold">--</div>
              <p className="text-xs text-muted-foreground">Connect to backend to view</p>
            </CardContent>
          </Card>

          <Card>
            <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
              <CardTitle className="text-sm font-medium">Scheduled Tasks</CardTitle>
              <Clock className="h-4 w-4 text-muted-foreground" />
            </CardHeader>
            <CardContent>
              <div className="text-2xl font-bold">--</div>
              <p className="text-xs text-muted-foreground">Connect to backend to view</p>
            </CardContent>
          </Card>

          <Card>
            <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
              <CardTitle className="text-sm font-medium">Active Nodes</CardTitle>
              <Network className="h-4 w-4 text-muted-foreground" />
            </CardHeader>
            <CardContent>
              <div className="text-2xl font-bold">--</div>
              <p className="text-xs text-muted-foreground">Connect to backend to view</p>
            </CardContent>
          </Card>
        </div>
      </div>
    </div>
  )
}
