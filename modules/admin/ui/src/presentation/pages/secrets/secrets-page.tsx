import { Key, Plus } from 'lucide-react'

import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/presentation/components/ui/card'
import { PageHeader } from '@/presentation/components/shared/page-header'
import { Button } from '@/presentation/components/ui/button'

export function SecretsPage() {
  return (
    <div className="space-y-6">
      <PageHeader
        title="Secrets"
        description="Manage secure credentials and API keys."
        actions={
          <Button>
            <Plus className="mr-2 h-4 w-4" />
            Add Secret
          </Button>
        }
      />

      <Card>
        <CardHeader>
          <CardTitle className="flex items-center gap-2">
            <Key className="h-5 w-5" />
            Secret Store
          </CardTitle>
          <CardDescription>Securely stored credentials</CardDescription>
        </CardHeader>
        <CardContent>
          <p className="text-sm text-muted-foreground">
            No secrets stored. Add secrets to use in your DAG configurations.
          </p>
        </CardContent>
      </Card>
    </div>
  )
}
