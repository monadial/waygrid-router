import { useCallback, useEffect, useState } from 'react'
import Editor from '@monaco-editor/react'
import { AlertCircle, Check, Code, X } from 'lucide-react'

import { Button } from '@/presentation/components/ui/button'
import { cn } from '@/lib/utils'
import { EditorDag } from '@/domain/dag-editor/entities/editor-dag'
import { generateDSL } from '@/domain/dag-editor/services/dsl-generator'
import { parseDSL } from '@/domain/dag-editor/services/dsl-parser'
import { ParseError } from '@/domain/dag-editor/value-objects/parse-error'

interface DslEditorPanelProps {
  dag: EditorDag
  onDagUpdate: (dag: EditorDag) => void
  onClose: () => void
}

export function DslEditorPanel({ dag, onDagUpdate, onClose }: DslEditorPanelProps) {
  const [code, setCode] = useState(() => generateDSL(dag))
  const [errors, setErrors] = useState<ParseError[]>([])
  const [isDirty, setIsDirty] = useState(false)

  // Regenerate DSL when dag changes externally
  useEffect(() => {
    if (!isDirty) {
      setCode(generateDSL(dag))
    }
  }, [dag, isDirty])

  const handleCodeChange = useCallback((value: string | undefined) => {
    if (value !== undefined) {
      setCode(value)
      setIsDirty(true)

      // Parse and validate
      const result = parseDSL(value)
      setErrors(result.errors)
    }
  }, [])

  const handleApply = useCallback(() => {
    const result = parseDSL(code)
    if (result.dag && result.errors.filter((e) => e.severity === 'error').length === 0) {
      onDagUpdate(result.dag)
      setIsDirty(false)
      setErrors([])
    }
  }, [code, onDagUpdate])

  const handleReset = useCallback(() => {
    setCode(generateDSL(dag))
    setIsDirty(false)
    setErrors([])
  }, [dag])

  const hasErrors = errors.filter((e) => e.severity === 'error').length > 0
  const hasWarnings = errors.filter((e) => e.severity === 'warning').length > 0

  return (
    <div className="flex h-full w-[500px] flex-col border-l bg-card">
      {/* Header */}
      <div className="flex items-center justify-between border-b px-4 py-3">
        <div className="flex items-center gap-2">
          <Code className="h-4 w-4 text-muted-foreground" />
          <span className="font-semibold">DSL Editor</span>
          {isDirty && (
            <span className="rounded-full bg-amber-100 px-2 py-0.5 text-xs text-amber-700 dark:bg-amber-900 dark:text-amber-300">
              Modified
            </span>
          )}
        </div>
        <Button variant="ghost" size="icon" onClick={onClose}>
          <X className="h-4 w-4" />
        </Button>
      </div>

      {/* Editor */}
      <div className="flex-1 overflow-hidden">
        <Editor
          height="100%"
          defaultLanguage="plaintext"
          value={code}
          onChange={handleCodeChange}
          theme="vs-dark"
          options={{
            minimap: { enabled: false },
            fontSize: 13,
            lineNumbers: 'on',
            scrollBeyondLastLine: false,
            wordWrap: 'on',
            automaticLayout: true,
            tabSize: 2,
            padding: { top: 12 },
          }}
        />
      </div>

      {/* Errors/Warnings */}
      {errors.length > 0 && (
        <div className="max-h-32 overflow-y-auto border-t bg-muted/30 p-2">
          {errors.map((error, index) => (
            <div
              key={index}
              className={cn(
                'flex items-start gap-2 rounded px-2 py-1 text-xs',
                error.severity === 'error'
                  ? 'text-red-600 dark:text-red-400'
                  : error.severity === 'warning'
                    ? 'text-amber-600 dark:text-amber-400'
                    : 'text-blue-600 dark:text-blue-400'
              )}
            >
              <AlertCircle className="mt-0.5 h-3 w-3 shrink-0" />
              <span>
                Line {error.line}: {error.message}
              </span>
            </div>
          ))}
        </div>
      )}

      {/* Status Bar */}
      <div className="flex items-center justify-between border-t px-4 py-2">
        <div className="flex items-center gap-2 text-xs">
          {hasErrors ? (
            <span className="flex items-center gap-1 text-red-600">
              <AlertCircle className="h-3 w-3" />
              {errors.filter((e) => e.severity === 'error').length} error(s)
            </span>
          ) : hasWarnings ? (
            <span className="flex items-center gap-1 text-amber-600">
              <AlertCircle className="h-3 w-3" />
              {errors.filter((e) => e.severity === 'warning').length} warning(s)
            </span>
          ) : (
            <span className="flex items-center gap-1 text-emerald-600">
              <Check className="h-3 w-3" />
              Valid
            </span>
          )}
        </div>
        <div className="flex items-center gap-2">
          <Button variant="outline" size="sm" onClick={handleReset} disabled={!isDirty}>
            Reset
          </Button>
          <Button size="sm" onClick={handleApply} disabled={hasErrors || !isDirty}>
            Apply Changes
          </Button>
        </div>
      </div>
    </div>
  )
}
