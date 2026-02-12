import { ULID, isValidULID } from '@/domain/shared'

/**
 * OrganizationId - Unique identifier for an organization
 */
export type OrganizationId = ULID & { readonly __organizationId: unique symbol }

export function createOrganizationId(value: string): OrganizationId {
  if (!isValidULID(value)) {
    throw new Error(`Invalid OrganizationId: ${value}`)
  }
  return value as OrganizationId
}
