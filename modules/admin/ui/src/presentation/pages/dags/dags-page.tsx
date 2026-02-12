import { Link } from '@tanstack/react-router'
import { Database, Plus, GitBranch, Clock, Activity } from 'lucide-react'

import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/presentation/components/ui/card'
import { PageHeader } from '@/presentation/components/shared/page-header'
import { Button } from '@/presentation/components/ui/button'
import { Badge } from '@/presentation/components/ui/badge'

// Mock data for demonstration
const sampleDags = [
  {
    id: 'dag-1',
    name: 'payment-flow',
    nodeCount: 4,
    edgeCount: 3,
    status: 'active' as const,
    lastModified: '2 hours ago',
  },
  {
    id: 'dag-2',
    name: 'user-onboarding',
    nodeCount: 6,
    edgeCount: 5,
    status: 'draft' as const,
    lastModified: '1 day ago',
  },
]

const statusColors = {
  active: 'bg-emerald-100 text-emerald-700 dark:bg-emerald-900/50 dark:text-emerald-300',
  draft: 'bg-amber-100 text-amber-700 dark:bg-amber-900/50 dark:text-amber-300',
  inactive: 'bg-slate-100 text-slate-700 dark:bg-slate-900/50 dark:text-slate-300',
}

export function DagsPage() {
  return (
    <div className="space-y-6">
      <PageHeader
        title="DAG Registry"
        description="Manage your Directed Acyclic Graphs for event routing."
        actions={
          <Link to="/dags/editor">
            <Button>
              <Plus className="mr-2 h-4 w-4" />
              Create DAG
            </Button>
          </Link>
        }
      />

      <Card>
        <CardHeader>
          <CardTitle className="flex items-center gap-2">
            <Database className="h-5 w-5" />
            Registered DAGs
          </CardTitle>
          <CardDescription>View and manage your routing DAGs</CardDescription>
        </CardHeader>
        <CardContent>
          {sampleDags.length === 0 ? (
            <div className="flex flex-col items-center justify-center py-12 text-center">
              <GitBranch className="mb-4 h-12 w-12 text-muted-foreground/50" />
              <h3 className="mb-2 text-lg font-medium">No DAGs registered yet</h3>
              <p className="mb-4 text-sm text-muted-foreground">
                Create a DAG to define your event routing topology.
              </p>
              <Link to="/dags/editor">
                <Button>
                  <Plus className="mr-2 h-4 w-4" />
                  Create your first DAG
                </Button>
              </Link>
            </div>
          ) : (
            <div className="space-y-3">
              {sampleDags.map((dag) => (
                <Link key={dag.id} to="/dags/editor">
                  <div className="flex items-center justify-between rounded-lg border p-4 transition-colors hover:bg-accent">
                    <div className="flex items-center gap-4">
                      <div className="rounded-lg bg-primary/10 p-2">
                        <GitBranch className="h-5 w-5 text-primary" />
                      </div>
                      <div>
                        <div className="flex items-center gap-2">
                          <span className="font-medium">{dag.name}</span>
                          <Badge variant="secondary" className={statusColors[dag.status]}>
                            {dag.status}
                          </Badge>
                        </div>
                        <div className="flex items-center gap-3 text-xs text-muted-foreground">
                          <span className="flex items-center gap-1">
                            <Activity className="h-3 w-3" />
                            {dag.nodeCount} nodes, {dag.edgeCount} edges
                          </span>
                          <span className="flex items-center gap-1">
                            <Clock className="h-3 w-3" />
                            {dag.lastModified}
                          </span>
                        </div>
                      </div>
                    </div>
                    <Button variant="ghost" size="sm">
                      Edit
                    </Button>
                  </div>
                </Link>
              ))}
            </div>
          )}
        </CardContent>
      </Card>
    </div>
  )
}
