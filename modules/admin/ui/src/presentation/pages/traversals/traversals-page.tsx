import { GitBranch } from 'lucide-react'

import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/presentation/components/ui/card'
import { PageHeader } from '@/presentation/components/shared/page-header'
import { Button } from '@/presentation/components/ui/button'

export function TraversalsPage() {
  return (
    <div className="space-y-6">
      <PageHeader
        title="Traversals"
        description="Monitor event traversals through your DAGs."
        actions={
          <Button>
            <GitBranch className="mr-2 h-4 w-4" />
            New Traversal
          </Button>
        }
      />

      <Card>
        <CardHeader>
          <CardTitle>Recent Traversals</CardTitle>
          <CardDescription>View and monitor event traversals</CardDescription>
        </CardHeader>
        <CardContent>
          <p className="text-sm text-muted-foreground">
            No traversals yet. Create a traversal by sending an event to the origin service.
          </p>
        </CardContent>
      </Card>
    </div>
  )
}
