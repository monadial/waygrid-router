import { useState } from 'react'
import { Check, ChevronsUpDown, Plus, Building2 } from 'lucide-react'

import { Button } from '../ui/button'
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from '../ui/dropdown-menu'
import { useWorkspaceStore } from '@/presentation/stores/workspace-store'
import { getOrganizationInitials } from '@/domain/organization'
import { getProjectInitials } from '@/domain/project'

export function ProjectSwitcher() {
  const {
    currentOrganization,
    currentProject,
    organizations,
    setCurrentOrganization,
    setCurrentProject,
    getProjectsForOrganization,
  } = useWorkspaceStore()

  const [open, setOpen] = useState(false)

  return (
    <DropdownMenu open={open} onOpenChange={setOpen}>
      <DropdownMenuTrigger asChild>
        <Button
          variant="ghost"
          className="w-full justify-between px-3 py-6 text-left"
        >
          <div className="flex items-center gap-3">
            {currentProject ? (
              <>
                <div
                  className="flex h-8 w-8 items-center justify-center rounded-md text-xs font-bold text-white"
                  style={{ backgroundColor: currentProject.color }}
                >
                  {getProjectInitials(currentProject)}
                </div>
                <div className="flex flex-col">
                  <span className="text-sm font-medium">{currentProject.name}</span>
                  <span className="text-xs text-muted-foreground">
                    {currentOrganization?.name}
                  </span>
                </div>
              </>
            ) : (
              <>
                <div className="flex h-8 w-8 items-center justify-center rounded-md bg-muted">
                  <Building2 className="h-4 w-4" />
                </div>
                <span className="text-sm">Select a project</span>
              </>
            )}
          </div>
          <ChevronsUpDown className="h-4 w-4 shrink-0 opacity-50" />
        </Button>
      </DropdownMenuTrigger>

      <DropdownMenuContent className="w-64" align="start" side="bottom">
        {organizations.map((org) => (
          <div key={org.id}>
            <DropdownMenuLabel className="flex items-center gap-2 text-xs text-muted-foreground">
              <div className="flex h-5 w-5 items-center justify-center rounded bg-muted text-[10px] font-bold">
                {getOrganizationInitials(org)}
              </div>
              {org.name}
            </DropdownMenuLabel>

            {getProjectsForOrganization(org.id).map((project) => (
              <DropdownMenuItem
                key={project.id}
                onSelect={() => {
                  setCurrentOrganization(org)
                  setCurrentProject(project)
                  setOpen(false)
                }}
                className="cursor-pointer"
              >
                <div
                  className="mr-2 flex h-6 w-6 items-center justify-center rounded text-[10px] font-bold text-white"
                  style={{ backgroundColor: project.color }}
                >
                  {getProjectInitials(project)}
                </div>
                <span className="flex-1">{project.name}</span>
                {currentProject?.id === project.id && (
                  <Check className="h-4 w-4" />
                )}
              </DropdownMenuItem>
            ))}

            <DropdownMenuSeparator />
          </div>
        ))}

        <DropdownMenuItem className="cursor-pointer">
          <Plus className="mr-2 h-4 w-4" />
          Create New Project
        </DropdownMenuItem>
      </DropdownMenuContent>
    </DropdownMenu>
  )
}
