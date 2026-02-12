import { useState } from 'react'

import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/presentation/components/ui/dialog'
import { Button } from '@/presentation/components/ui/button'
import { Input } from '@/presentation/components/ui/input'
import { Label } from '@/presentation/components/ui/label'
import { cn } from '@/lib/utils'
import { EdgeGuard } from '@/domain/dag-editor/entities/editor-dag'

export interface EdgeConfig {
  guard: EdgeGuard
}

interface EdgeConfigDialogProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  initialConfig?: EdgeConfig
  onSave: (config: EdgeConfig) => void
  sourceNodeName?: string
  targetNodeName?: string
}

const guardOptions: { value: EdgeGuard['type']; label: string; description: string; color: string }[] = [
  {
    value: 'success',
    label: 'On Success',
    description: 'Trigger when the source node completes successfully',
    color: 'bg-emerald-100 text-emerald-700 dark:bg-emerald-900 dark:text-emerald-300',
  },
  {
    value: 'failure',
    label: 'On Failure',
    description: 'Trigger when the source node fails or throws an error',
    color: 'bg-red-100 text-red-700 dark:bg-red-900 dark:text-red-300',
  },
  {
    value: 'any',
    label: 'Always',
    description: 'Trigger regardless of success or failure',
    color: 'bg-slate-100 text-slate-700 dark:bg-slate-900 dark:text-slate-300',
  },
  {
    value: 'timeout',
    label: 'On Timeout',
    description: 'Trigger when the source node times out',
    color: 'bg-amber-100 text-amber-700 dark:bg-amber-900 dark:text-amber-300',
  },
  {
    value: 'condition',
    label: 'Conditional',
    description: 'Trigger based on a custom condition expression',
    color: 'bg-violet-100 text-violet-700 dark:bg-violet-900 dark:text-violet-300',
  },
]

export function EdgeConfigDialog({
  open,
  onOpenChange,
  initialConfig,
  onSave,
  sourceNodeName,
  targetNodeName,
}: EdgeConfigDialogProps) {
  const [guardType, setGuardType] = useState<EdgeGuard['type']>(
    initialConfig?.guard.type ?? 'success'
  )
  const [conditionExpression, setConditionExpression] = useState(
    initialConfig?.guard.type === 'condition' ? initialConfig.guard.expression : ''
  )

  const handleSave = () => {
    const guard: EdgeGuard =
      guardType === 'condition'
        ? { type: 'condition', expression: conditionExpression }
        : { type: guardType }

    onSave({ guard })
    onOpenChange(false)
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-md">
        <DialogHeader>
          <DialogTitle>Configure Edge</DialogTitle>
          <DialogDescription>
            {sourceNodeName && targetNodeName ? (
              <>
                Configure the connection from <strong>{sourceNodeName}</strong> to{' '}
                <strong>{targetNodeName}</strong>
              </>
            ) : (
              'Set when this edge should be triggered.'
            )}
          </DialogDescription>
        </DialogHeader>

        <div className="grid gap-4 py-4">
          {/* Guard Type Selection */}
          <div className="space-y-2">
            <Label>Trigger Condition</Label>
            <div className="space-y-2">
              {guardOptions.map((option) => (
                <button
                  key={option.value}
                  onClick={() => setGuardType(option.value)}
                  className={cn(
                    'flex w-full items-start gap-3 rounded-lg border-2 p-3 text-left transition-all',
                    guardType === option.value
                      ? `${option.color} border-current`
                      : 'border-transparent bg-muted/50 hover:bg-muted'
                  )}
                >
                  <div className="flex-1">
                    <div className="font-medium">{option.label}</div>
                    <div className="text-xs opacity-70">{option.description}</div>
                  </div>
                </button>
              ))}
            </div>
          </div>

          {/* Condition Expression (only for condition type) */}
          {guardType === 'condition' && (
            <div className="space-y-2">
              <Label htmlFor="condition">Condition Expression</Label>
              <Input
                id="condition"
                value={conditionExpression}
                onChange={(e) => setConditionExpression(e.target.value)}
                placeholder="event.type === 'payment'"
              />
              <p className="text-xs text-muted-foreground">
                JavaScript expression that evaluates to true/false based on the event data.
              </p>
            </div>
          )}
        </div>

        <DialogFooter>
          <Button variant="outline" onClick={() => onOpenChange(false)}>
            Cancel
          </Button>
          <Button onClick={handleSave}>Save</Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
