import { Network } from 'lucide-react'

import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/presentation/components/ui/card'
import { PageHeader } from '@/presentation/components/shared/page-header'
import { useCurrentNode } from '@/presentation/hooks'
import { Skeleton } from '@/presentation/components/ui/skeleton'
import { Badge } from '@/presentation/components/ui/badge'
import { formatUptime } from '@/lib/utils'

export function TopologyPage() {
  const { data: node, isLoading, error } = useCurrentNode()

  return (
    <div className="space-y-6">
      <PageHeader
        title="Topology"
        description="View and manage nodes in your Waygrid network."
      />

      <div className="grid gap-6 md:grid-cols-2">
        {/* Current Node Info */}
        <Card>
          <CardHeader>
            <CardTitle className="flex items-center gap-2">
              <Network className="h-5 w-5" />
              Current Node
            </CardTitle>
            <CardDescription>Information about the connected node</CardDescription>
          </CardHeader>
          <CardContent>
            {isLoading ? (
              <div className="space-y-3">
                <Skeleton className="h-4 w-full" />
                <Skeleton className="h-4 w-3/4" />
                <Skeleton className="h-4 w-1/2" />
              </div>
            ) : error ? (
              <p className="text-sm text-muted-foreground">
                Unable to connect to topology service
              </p>
            ) : node ? (
              <dl className="space-y-3 text-sm">
                <div className="flex justify-between">
                  <dt className="text-muted-foreground">Node ID</dt>
                  <dd className="font-mono">{node.id}</dd>
                </div>
                <div className="flex justify-between">
                  <dt className="text-muted-foreground">Service</dt>
                  <dd>
                    <Badge variant="outline">
                      {node.descriptor.component}.{node.descriptor.service}
                    </Badge>
                  </dd>
                </div>
                <div className="flex justify-between">
                  <dt className="text-muted-foreground">Region</dt>
                  <dd>{node.region}</dd>
                </div>
                <div className="flex justify-between">
                  <dt className="text-muted-foreground">Cluster ID</dt>
                  <dd className="font-mono text-xs">{node.clusterId}</dd>
                </div>
                <div className="flex justify-between">
                  <dt className="text-muted-foreground">Uptime</dt>
                  <dd>{formatUptime(node.uptime)}</dd>
                </div>
              </dl>
            ) : null}
          </CardContent>
        </Card>

        {/* Cluster Overview */}
        <Card>
          <CardHeader>
            <CardTitle>Cluster Overview</CardTitle>
            <CardDescription>Nodes in your cluster</CardDescription>
          </CardHeader>
          <CardContent>
            <p className="text-sm text-muted-foreground">
              Cluster node discovery coming soon. This will show all nodes in the current cluster.
            </p>
          </CardContent>
        </Card>
      </div>
    </div>
  )
}
