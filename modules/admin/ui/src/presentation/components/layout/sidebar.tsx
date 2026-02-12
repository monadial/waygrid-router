import { Link, useLocation } from '@tanstack/react-router'
import {
  AlertCircle,
  GitBranch,
  History,
  Key,
  Settings,
  BarChart3,
  Network,
  Workflow,
  HelpCircle,
  ChevronLeft,
  ChevronRight,
} from 'lucide-react'

import { cn } from '@/lib/utils'
import { Button } from '../ui/button'
import { ScrollArea } from '../ui/scroll-area'
import { Separator } from '../ui/separator'
import { Tooltip, TooltipContent, TooltipTrigger, TooltipProvider } from '../ui/tooltip'
import { useUIStore } from '@/presentation/stores'
import { ProjectSwitcher } from './project-switcher'

/**
 * Sentry-style navigation items
 * Organized by primary functions
 */
const primaryNavItems = [
  { title: 'Issues', href: '/issues', icon: AlertCircle, badge: 12 },
  { title: 'DAGs', href: '/dags', icon: Workflow },
  { title: 'Traces', href: '/traces', icon: GitBranch },
  { title: 'History', href: '/history', icon: History },
]

const secondaryNavItems = [
  { title: 'Topology', href: '/topology', icon: Network },
  { title: 'Stats', href: '/stats', icon: BarChart3 },
]

const settingsNavItems = [
  { title: 'Secrets', href: '/secrets', icon: Key },
  { title: 'Settings', href: '/settings', icon: Settings },
]

interface NavItemType {
  title: string
  href: string
  icon: React.ComponentType<{ className?: string }>
  badge?: number
}

export function Sidebar() {
  const location = useLocation()
  const { sidebarCollapsed, setSidebarCollapsed } = useUIStore()

  const NavItem = ({
    item,
    isActive,
  }: {
    item: NavItemType
    isActive: boolean
  }) => {
    const content = (
      <Link
        to={item.href}
        className={cn(
          'flex items-center gap-3 rounded-md px-3 py-2 text-sm font-medium transition-colors',
          isActive
            ? 'bg-primary/10 text-primary'
            : 'text-muted-foreground hover:bg-muted hover:text-foreground'
        )}
      >
        <item.icon className="h-4 w-4 shrink-0" />
        {!sidebarCollapsed && (
          <>
            <span className="flex-1">{item.title}</span>
            {item.badge && (
              <span className="flex h-5 min-w-5 items-center justify-center rounded-full bg-destructive px-1.5 text-[10px] font-bold text-destructive-foreground">
                {item.badge}
              </span>
            )}
          </>
        )}
      </Link>
    )

    if (sidebarCollapsed) {
      return (
        <Tooltip>
          <TooltipTrigger asChild>{content}</TooltipTrigger>
          <TooltipContent side="right" className="flex items-center gap-2">
            {item.title}
            {item.badge && (
              <span className="rounded-full bg-destructive px-1.5 py-0.5 text-[10px] font-bold text-destructive-foreground">
                {item.badge}
              </span>
            )}
          </TooltipContent>
        </Tooltip>
      )
    }

    return content
  }

  return (
    <TooltipProvider delayDuration={0}>
      <aside
        className={cn(
          'fixed left-0 top-0 z-40 flex h-screen flex-col border-r bg-card transition-all duration-300',
          sidebarCollapsed ? 'w-16' : 'w-64'
        )}
      >
        {/* Project Switcher */}
        <div className="border-b">
          {sidebarCollapsed ? (
            <div className="flex h-[72px] items-center justify-center">
              <div className="flex h-8 w-8 items-center justify-center rounded-md bg-primary text-primary-foreground">
                <Network className="h-5 w-5" />
              </div>
            </div>
          ) : (
            <ProjectSwitcher />
          )}
        </div>

        {/* Navigation */}
        <ScrollArea className="flex-1 px-3 py-4">
          <nav className="space-y-1">
            {primaryNavItems.map((item) => (
              <NavItem
                key={item.href}
                item={item}
                isActive={location.pathname === item.href}
              />
            ))}
          </nav>

          <Separator className="my-4" />

          <nav className="space-y-1">
            {secondaryNavItems.map((item) => (
              <NavItem
                key={item.href}
                item={item}
                isActive={location.pathname === item.href}
              />
            ))}
          </nav>

          <Separator className="my-4" />

          <nav className="space-y-1">
            {settingsNavItems.map((item) => (
              <NavItem
                key={item.href}
                item={item}
                isActive={location.pathname === item.href}
              />
            ))}
          </nav>
        </ScrollArea>

        {/* Footer */}
        <div className="border-t p-3">
          <div className="flex items-center justify-between">
            {!sidebarCollapsed && (
              <Button variant="ghost" size="sm" className="gap-2 text-muted-foreground">
                <HelpCircle className="h-4 w-4" />
                Help
              </Button>
            )}
            <Button
              variant="ghost"
              size="icon"
              className="ml-auto"
              onClick={() => setSidebarCollapsed(!sidebarCollapsed)}
            >
              {sidebarCollapsed ? (
                <ChevronRight className="h-4 w-4" />
              ) : (
                <ChevronLeft className="h-4 w-4" />
              )}
            </Button>
          </div>
        </div>
      </aside>
    </TooltipProvider>
  )
}
