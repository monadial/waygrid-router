import { useCallback, useState } from 'react'
import { Link } from '@tanstack/react-router'
import { ArrowLeft, Save, Play, Code, X } from 'lucide-react'

import { Button } from '@/presentation/components/ui/button'
import { DagCanvas } from '@/presentation/components/dag-editor'
import { DslEditorPanel } from '@/presentation/components/dag-editor/dsl-editor-panel'
import { createSampleDag, EditorDag } from '@/domain/dag-editor/entities/editor-dag'

export function DagEditorPage() {
  const [dag, setDag] = useState<EditorDag>(() => createSampleDag())
  const [showDslEditor, setShowDslEditor] = useState(false)

  const handleDagChange = useCallback((updatedDag: EditorDag) => {
    setDag(updatedDag)
  }, [])

  const handleDslUpdate = useCallback((updatedDag: EditorDag) => {
    setDag(updatedDag)
  }, [])

  const handleSave = useCallback(() => {
    // TODO: Implement save to backend
    console.log('Saving DAG:', dag)
  }, [dag])

  return (
    <div className="flex h-[calc(100vh-4rem)] flex-col">
      {/* Header */}
      <div className="flex items-center justify-between border-b px-4 py-3">
        <div className="flex items-center gap-4">
          <Link to="/dags">
            <Button variant="ghost" size="sm">
              <ArrowLeft className="mr-2 h-4 w-4" />
              Back to DAGs
            </Button>
          </Link>
          <div className="h-6 w-px bg-border" />
          <div className="flex items-center gap-2">
            <span className="text-lg font-semibold">{dag.name}</span>
          </div>
        </div>
        <div className="flex items-center gap-2">
          <Button
            variant={showDslEditor ? 'default' : 'outline'}
            size="sm"
            onClick={() => setShowDslEditor(!showDslEditor)}
          >
            {showDslEditor ? (
              <>
                <X className="mr-2 h-4 w-4" />
                Close DSL
              </>
            ) : (
              <>
                <Code className="mr-2 h-4 w-4" />
                View DSL
              </>
            )}
          </Button>
          <Button variant="outline" size="sm">
            <Play className="mr-2 h-4 w-4" />
            Test
          </Button>
          <Button size="sm" onClick={handleSave}>
            <Save className="mr-2 h-4 w-4" />
            Save
          </Button>
        </div>
      </div>

      {/* Main Content */}
      <div className="flex flex-1 overflow-hidden">
        {/* Canvas */}
        <div className="flex-1">
          <DagCanvas initialDag={dag} onChange={handleDagChange} />
        </div>

        {/* DSL Editor Panel */}
        {showDslEditor && (
          <DslEditorPanel
            dag={dag}
            onDagUpdate={handleDslUpdate}
            onClose={() => setShowDslEditor(false)}
          />
        )}
      </div>
    </div>
  )
}
