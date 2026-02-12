import { CreditCard } from 'lucide-react'

import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/presentation/components/ui/card'
import { PageHeader } from '@/presentation/components/shared/page-header'

export function BillingPage() {
  return (
    <div className="space-y-6">
      <PageHeader
        title="Billing"
        description="View usage metrics and billing information."
      />

      <Card>
        <CardHeader>
          <CardTitle className="flex items-center gap-2">
            <CreditCard className="h-5 w-5" />
            Usage & Billing
          </CardTitle>
          <CardDescription>Track your resource usage</CardDescription>
        </CardHeader>
        <CardContent>
          <p className="text-sm text-muted-foreground">
            Usage metrics and billing information will be available when the billing service is configured.
          </p>
        </CardContent>
      </Card>
    </div>
  )
}
