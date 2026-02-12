import { ULID, isValidULID } from '@/domain/shared'

/**
 * ProjectId - Unique identifier for a project
 */
export type ProjectId = ULID & { readonly __projectId: unique symbol }

export function createProjectId(value: string): ProjectId {
  if (!isValidULID(value)) {
    throw new Error(`Invalid ProjectId: ${value}`)
  }
  return value as ProjectId
}
