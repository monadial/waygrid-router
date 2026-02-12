import { Timestamp } from '@/domain/shared'
import { OrganizationId, createOrganizationId } from '../value-objects/organization-id'
import { OrganizationSlug, createOrganizationSlug } from '../value-objects/organization-slug'

/**
 * Organization - Top-level tenant in the Waygrid platform
 * Contains projects, manages billing and access
 */
export interface Organization {
  readonly id: OrganizationId
  readonly slug: OrganizationSlug
  readonly name: string
  readonly avatarUrl?: string
  readonly plan: OrganizationPlan
  readonly createdAt: Timestamp
  readonly updatedAt: Timestamp
}

export type OrganizationPlan = 'free' | 'team' | 'business' | 'enterprise'

export function createOrganization(params: {
  id: string
  slug: string
  name: string
  avatarUrl?: string
  plan: OrganizationPlan
  createdAt: string
  updatedAt: string
}): Organization {
  return {
    id: createOrganizationId(params.id),
    slug: createOrganizationSlug(params.slug),
    name: params.name,
    avatarUrl: params.avatarUrl,
    plan: params.plan,
    createdAt: params.createdAt as Timestamp,
    updatedAt: params.updatedAt as Timestamp,
  }
}

export function getOrganizationInitials(org: Organization): string {
  return org.name
    .split(' ')
    .map((word) => word[0])
    .join('')
    .toUpperCase()
    .slice(0, 2)
}
