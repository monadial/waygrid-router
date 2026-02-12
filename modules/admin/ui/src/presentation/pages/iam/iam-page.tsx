import { Shield } from 'lucide-react'

import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/presentation/components/ui/card'
import { PageHeader } from '@/presentation/components/shared/page-header'

export function IamPage() {
  return (
    <div className="space-y-6">
      <PageHeader
        title="Identity & Access Management"
        description="Manage users, roles, and permissions."
      />

      <Card>
        <CardHeader>
          <CardTitle className="flex items-center gap-2">
            <Shield className="h-5 w-5" />
            Access Control
          </CardTitle>
          <CardDescription>IAM service integration coming soon</CardDescription>
        </CardHeader>
        <CardContent>
          <p className="text-sm text-muted-foreground">
            User management and role-based access control will be available when the IAM service is implemented.
          </p>
        </CardContent>
      </Card>
    </div>
  )
}
