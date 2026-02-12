import { Settings, Clock, Repeat, Info } from 'lucide-react'

import { Button } from '@/presentation/components/ui/button'
import { Input } from '@/presentation/components/ui/input'
import { Label } from '@/presentation/components/ui/label'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/presentation/components/ui/select'
import { RepeatPolicy } from '@/domain/dag-editor/entities/editor-dag'

export interface DagSettings {
  name: string
  timeout?: string
  repeatPolicy: RepeatPolicy
}

interface DagSettingsPanelProps {
  settings: DagSettings
  onChange: (settings: DagSettings) => void
  collapsed?: boolean
  onToggleCollapsed?: () => void
}

type RepeatType = RepeatPolicy['type']

// Helper to build repeat policy from parts
function buildRepeatPolicy(
  type: RepeatType,
  every: string,
  count: string,
  until: string
): RepeatPolicy {
  switch (type) {
    case 'no-repeat':
      return { type: 'no-repeat' }
    case 'indefinitely':
      return { type: 'indefinitely', every }
    case 'times':
      return { type: 'times', every, count: parseInt(count, 10) || 1 }
    case 'until':
      return { type: 'until', every, until }
  }
}

// Helper to extract values from repeat policy
function getRepeatPolicyValues(policy: RepeatPolicy) {
  return {
    type: policy.type,
    every: policy.type !== 'no-repeat' ? policy.every : '1h',
    count: policy.type === 'times' ? policy.count.toString() : '5',
    until: policy.type === 'until' ? policy.until : '',
  }
}

export function DagSettingsPanel({
  settings,
  onChange,
  collapsed = false,
  onToggleCollapsed,
}: DagSettingsPanelProps) {
  const repeatValues = getRepeatPolicyValues(settings.repeatPolicy)

  // Update helpers that call onChange directly
  const updateName = (name: string) => {
    onChange({ ...settings, name })
  }

  const updateTimeout = (timeout: string) => {
    onChange({ ...settings, timeout: timeout || undefined })
  }

  const updateRepeatType = (type: RepeatType) => {
    onChange({
      ...settings,
      repeatPolicy: buildRepeatPolicy(type, repeatValues.every, repeatValues.count, repeatValues.until),
    })
  }

  const updateRepeatEvery = (every: string) => {
    onChange({
      ...settings,
      repeatPolicy: buildRepeatPolicy(repeatValues.type, every, repeatValues.count, repeatValues.until),
    })
  }

  const updateRepeatCount = (count: string) => {
    onChange({
      ...settings,
      repeatPolicy: buildRepeatPolicy(repeatValues.type, repeatValues.every, count, repeatValues.until),
    })
  }

  const updateRepeatUntil = (until: string) => {
    onChange({
      ...settings,
      repeatPolicy: buildRepeatPolicy(repeatValues.type, repeatValues.every, repeatValues.count, until),
    })
  }

  if (collapsed) {
    return (
      <div className="absolute right-4 top-4 z-10">
        <Button
          variant="outline"
          size="sm"
          onClick={onToggleCollapsed}
          className="gap-2 bg-background shadow-sm"
        >
          <Settings className="h-4 w-4" />
          DAG Settings
        </Button>
      </div>
    )
  }

  return (
    <div className="absolute right-4 top-4 z-10 w-72 rounded-lg border bg-card p-4 shadow-lg">
      <div className="mb-4 flex items-center justify-between">
        <div className="flex items-center gap-2">
          <Settings className="h-4 w-4 text-muted-foreground" />
          <span className="font-semibold">DAG Settings</span>
        </div>
        {onToggleCollapsed && (
          <Button variant="ghost" size="sm" onClick={onToggleCollapsed}>
            Collapse
          </Button>
        )}
      </div>

      <div className="space-y-4">
        {/* DAG Name */}
        <div className="space-y-2">
          <Label htmlFor="dagName" className="text-xs">
            DAG Name
          </Label>
          <Input
            id="dagName"
            value={settings.name}
            onChange={(e) => updateName(e.target.value)}
            placeholder="my-dag"
          />
        </div>

        {/* Timeout */}
        <div className="space-y-2">
          <Label htmlFor="timeout" className="flex items-center gap-1.5 text-xs">
            <Clock className="h-3.5 w-3.5" />
            Timeout
          </Label>
          <Input
            id="timeout"
            value={settings.timeout ?? '30s'}
            onChange={(e) => updateTimeout(e.target.value)}
            placeholder="30s"
          />
          <p className="text-[10px] text-muted-foreground">
            Max execution time (e.g., 30s, 5m, 1h)
          </p>
        </div>

        {/* Repeat Policy */}
        <div className="space-y-2">
          <Label className="flex items-center gap-1.5 text-xs">
            <Repeat className="h-3.5 w-3.5" />
            Repeat Policy
          </Label>
          <Select value={repeatValues.type} onValueChange={(v) => updateRepeatType(v as RepeatType)}>
            <SelectTrigger>
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="no-repeat">No Repeat (Run Once)</SelectItem>
              <SelectItem value="indefinitely">Repeat Indefinitely</SelectItem>
              <SelectItem value="times">Repeat N Times</SelectItem>
              <SelectItem value="until">Repeat Until</SelectItem>
            </SelectContent>
          </Select>

          {repeatValues.type !== 'no-repeat' && (
            <div className="space-y-2 rounded-md bg-muted/50 p-2">
              <div className="space-y-1">
                <Label htmlFor="repeatEvery" className="text-[10px]">
                  Repeat Every
                </Label>
                <Input
                  id="repeatEvery"
                  value={repeatValues.every}
                  onChange={(e) => updateRepeatEvery(e.target.value)}
                  placeholder="1h"
                  className="h-8 text-sm"
                />
              </div>

              {repeatValues.type === 'times' && (
                <div className="space-y-1">
                  <Label htmlFor="repeatCount" className="text-[10px]">
                    Number of Times
                  </Label>
                  <Input
                    id="repeatCount"
                    type="number"
                    min="1"
                    value={repeatValues.count}
                    onChange={(e) => updateRepeatCount(e.target.value)}
                    className="h-8 text-sm"
                  />
                </div>
              )}

              {repeatValues.type === 'until' && (
                <div className="space-y-1">
                  <Label htmlFor="repeatUntil" className="text-[10px]">
                    Until (ISO datetime)
                  </Label>
                  <Input
                    id="repeatUntil"
                    type="datetime-local"
                    value={repeatValues.until}
                    onChange={(e) => updateRepeatUntil(e.target.value)}
                    className="h-8 text-sm"
                  />
                </div>
              )}
            </div>
          )}
        </div>

        {/* Info */}
        <div className="flex items-start gap-2 rounded-md bg-blue-50 p-2 text-[10px] text-blue-700 dark:bg-blue-950 dark:text-blue-300">
          <Info className="mt-0.5 h-3 w-3 shrink-0" />
          <span>
            These settings apply to the entire DAG execution. Individual nodes can have their own
            retry policies.
          </span>
        </div>
      </div>
    </div>
  )
}
