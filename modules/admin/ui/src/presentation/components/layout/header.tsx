import { Moon, Sun, User } from 'lucide-react'

import { Button } from '../ui/button'
import { useTheme } from '@/presentation/providers/theme-provider'
import { useSystemHealth } from '@/presentation/hooks'
import { Badge } from '../ui/badge'

export function Header() {
  const { theme, setTheme, resolvedTheme } = useTheme()
  const { data: systemHealth } = useSystemHealth()

  const toggleTheme = () => {
    if (theme === 'system') {
      setTheme(resolvedTheme === 'dark' ? 'light' : 'dark')
    } else {
      setTheme(theme === 'dark' ? 'light' : 'dark')
    }
  }

  return (
    <header className="sticky top-0 z-30 flex h-14 items-center justify-between border-b bg-background px-6">
      <div className="flex items-center gap-4">
        <h1 className="text-lg font-semibold">Waygrid Router</h1>
        {systemHealth && (
          <Badge
            variant={
              systemHealth === 'healthy'
                ? 'success'
                : systemHealth === 'degraded'
                  ? 'warning'
                  : 'destructive'
            }
          >
            {systemHealth === 'healthy'
              ? 'All systems operational'
              : systemHealth === 'degraded'
                ? 'Degraded performance'
                : 'System issues'}
          </Badge>
        )}
      </div>

      <div className="flex items-center gap-2">
        <Button variant="ghost" size="icon" onClick={toggleTheme}>
          {resolvedTheme === 'dark' ? (
            <Sun className="h-4 w-4" />
          ) : (
            <Moon className="h-4 w-4" />
          )}
          <span className="sr-only">Toggle theme</span>
        </Button>

        <Button variant="ghost" size="icon">
          <User className="h-4 w-4" />
          <span className="sr-only">User menu</span>
        </Button>
      </div>
    </header>
  )
}
