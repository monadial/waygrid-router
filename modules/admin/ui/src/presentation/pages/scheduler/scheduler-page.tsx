import { Calendar, Plus } from 'lucide-react'

import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/presentation/components/ui/card'
import { PageHeader } from '@/presentation/components/shared/page-header'
import { Button } from '@/presentation/components/ui/button'

export function SchedulerPage() {
  return (
    <div className="space-y-6">
      <PageHeader
        title="Scheduler"
        description="Manage scheduled tasks and recurring jobs."
        actions={
          <Button>
            <Plus className="mr-2 h-4 w-4" />
            Schedule Task
          </Button>
        }
      />

      <Card>
        <CardHeader>
          <CardTitle className="flex items-center gap-2">
            <Calendar className="h-5 w-5" />
            Scheduled Tasks
          </CardTitle>
          <CardDescription>View and manage scheduled tasks</CardDescription>
        </CardHeader>
        <CardContent>
          <p className="text-sm text-muted-foreground">
            No scheduled tasks. Create a task to run on a schedule.
          </p>
        </CardContent>
      </Card>
    </div>
  )
}
