/**
 * EdgeGuard - Conditional routing between nodes
 * Mirrors: com.monadial.waygrid.common.domain.model.routing.dag.EdgeGuard
 */
export type EdgeGuard =
  | { readonly type: 'OnSuccess' }
  | { readonly type: 'OnFailure' }
  | { readonly type: 'Always' }
  | { readonly type: 'Conditional'; readonly conditionId: string }

export const EdgeGuards = {
  OnSuccess: (): EdgeGuard => ({ type: 'OnSuccess' }),
  OnFailure: (): EdgeGuard => ({ type: 'OnFailure' }),
  Always: (): EdgeGuard => ({ type: 'Always' }),
  Conditional: (conditionId: string): EdgeGuard => ({ type: 'Conditional', conditionId }),
}

export function formatEdgeGuard(guard: EdgeGuard): string {
  switch (guard.type) {
    case 'OnSuccess':
      return 'On Success'
    case 'OnFailure':
      return 'On Failure'
    case 'Always':
      return 'Always'
    case 'Conditional':
      return `Condition: ${guard.conditionId}`
  }
}

export function getEdgeGuardColor(guard: EdgeGuard): string {
  switch (guard.type) {
    case 'OnSuccess':
      return 'text-green-500'
    case 'OnFailure':
      return 'text-red-500'
    case 'Always':
      return 'text-blue-500'
    case 'Conditional':
      return 'text-yellow-500'
  }
}
