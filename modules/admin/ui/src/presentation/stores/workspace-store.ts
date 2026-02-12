import { create } from 'zustand'
import { persist } from 'zustand/middleware'
import { Organization, createOrganization } from '@/domain/organization'
import { Project, createProject } from '@/domain/project'

interface WorkspaceState {
  // Current selections
  currentOrganization: Organization | null
  currentProject: Project | null

  // Available data (mock for now)
  organizations: Organization[]
  projects: Project[]

  // Actions
  setCurrentOrganization: (org: Organization | null) => void
  setCurrentProject: (project: Project | null) => void
  getProjectsForOrganization: (orgId: string) => Project[]
}

// Mock data for development
// Note: ULIDs use Crockford's Base32 (excludes I, L, O, U)
const MOCK_ORGANIZATIONS: Organization[] = [
  createOrganization({
    id: '01HQXK5M9ZYXWVTSRQPNMKJ001',
    slug: 'acme-corp',
    name: 'Acme Corporation',
    plan: 'business',
    createdAt: '2024-01-15T10:00:00Z',
    updatedAt: '2024-01-15T10:00:00Z',
  }),
  createOrganization({
    id: '01HQXK5M9ZYXWVTSRQPNMKJ002',
    slug: 'personal',
    name: 'Personal',
    plan: 'free',
    createdAt: '2024-01-10T10:00:00Z',
    updatedAt: '2024-01-10T10:00:00Z',
  }),
]

const MOCK_PROJECTS: Project[] = [
  createProject({
    id: '01HQXK5M9ZYXWVTSRQPNMKP001',
    organizationId: '01HQXK5M9ZYXWVTSRQPNMKJ001',
    slug: 'payment-service',
    name: 'Payment Service',
    platform: 'scala',
    createdAt: '2024-01-15T11:00:00Z',
    updatedAt: '2024-01-15T11:00:00Z',
  }),
  createProject({
    id: '01HQXK5M9ZYXWVTSRQPNMKP002',
    organizationId: '01HQXK5M9ZYXWVTSRQPNMKJ001',
    slug: 'notification-hub',
    name: 'Notification Hub',
    platform: 'nodejs',
    createdAt: '2024-01-16T10:00:00Z',
    updatedAt: '2024-01-16T10:00:00Z',
  }),
  createProject({
    id: '01HQXK5M9ZYXWVTSRQPNMKP003',
    organizationId: '01HQXK5M9ZYXWVTSRQPNMKJ001',
    slug: 'analytics-pipeline',
    name: 'Analytics Pipeline',
    platform: 'python',
    createdAt: '2024-01-17T10:00:00Z',
    updatedAt: '2024-01-17T10:00:00Z',
  }),
  createProject({
    id: '01HQXK5M9ZYXWVTSRQPNMKP004',
    organizationId: '01HQXK5M9ZYXWVTSRQPNMKJ002',
    slug: 'side-project',
    name: 'Side Project',
    platform: 'go',
    createdAt: '2024-01-18T10:00:00Z',
    updatedAt: '2024-01-18T10:00:00Z',
  }),
]

/**
 * Workspace Store - Manages organization and project selection
 * Persists the last selected org/project
 */
export const useWorkspaceStore = create<WorkspaceState>()(
  persist(
    (set, get) => ({
      currentOrganization: MOCK_ORGANIZATIONS[0],
      currentProject: MOCK_PROJECTS[0],
      organizations: MOCK_ORGANIZATIONS,
      projects: MOCK_PROJECTS,

      setCurrentOrganization: (org) => {
        set({ currentOrganization: org })
        // Reset project when org changes
        if (org) {
          const orgProjects = get().projects.filter((p) => p.organizationId === org.id)
          set({ currentProject: orgProjects[0] ?? null })
        } else {
          set({ currentProject: null })
        }
      },

      setCurrentProject: (project) => set({ currentProject: project }),

      getProjectsForOrganization: (orgId) => {
        return get().projects.filter((p) => p.organizationId === orgId)
      },
    }),
    {
      name: 'waygrid-workspace',
      partialize: (state) => ({
        currentOrganization: state.currentOrganization,
        currentProject: state.currentProject,
      }),
    }
  )
)
