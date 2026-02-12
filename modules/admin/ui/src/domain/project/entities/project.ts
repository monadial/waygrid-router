import { Timestamp } from '@/domain/shared'
import { OrganizationId } from '@/domain/organization'
import { ProjectId, createProjectId } from '../value-objects/project-id'
import { ProjectSlug, createProjectSlug } from '../value-objects/project-slug'

/**
 * Project - Container for DAGs, secrets, and history within an organization
 * Similar to Sentry projects
 */
export interface Project {
  readonly id: ProjectId
  readonly organizationId: OrganizationId
  readonly slug: ProjectSlug
  readonly name: string
  readonly platform?: ProjectPlatform
  readonly color: string
  readonly createdAt: Timestamp
  readonly updatedAt: Timestamp
}

export type ProjectPlatform =
  | 'nodejs'
  | 'python'
  | 'java'
  | 'scala'
  | 'go'
  | 'rust'
  | 'ruby'
  | 'php'
  | 'dotnet'
  | 'other'

export const PLATFORM_COLORS: Record<ProjectPlatform, string> = {
  nodejs: '#68A063',
  python: '#3776AB',
  java: '#ED8B00',
  scala: '#DC322F',
  go: '#00ADD8',
  rust: '#DEA584',
  ruby: '#CC342D',
  php: '#777BB4',
  dotnet: '#512BD4',
  other: '#6B7280',
}

export function createProject(params: {
  id: string
  organizationId: string
  slug: string
  name: string
  platform?: ProjectPlatform
  color?: string
  createdAt: string
  updatedAt: string
}): Project {
  return {
    id: createProjectId(params.id),
    organizationId: params.organizationId as OrganizationId,
    slug: createProjectSlug(params.slug),
    name: params.name,
    platform: params.platform,
    color: params.color ?? (params.platform ? PLATFORM_COLORS[params.platform] : '#6B7280'),
    createdAt: params.createdAt as Timestamp,
    updatedAt: params.updatedAt as Timestamp,
  }
}

export function getProjectInitials(project: Project): string {
  return project.name
    .split(/[\s-_]+/)
    .map((word) => word[0])
    .join('')
    .toUpperCase()
    .slice(0, 2)
}
