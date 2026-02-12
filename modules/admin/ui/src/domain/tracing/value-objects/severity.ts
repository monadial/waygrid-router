/**
 * Severity - Event severity level (like Sentry)
 */
export type Severity = 'fatal' | 'error' | 'warning' | 'info' | 'debug'

export const SEVERITY_ORDER: Record<Severity, number> = {
  fatal: 0,
  error: 1,
  warning: 2,
  info: 3,
  debug: 4,
}

export const SEVERITY_COLORS: Record<Severity, { bg: string; text: string; border: string }> = {
  fatal: { bg: 'bg-red-950', text: 'text-red-400', border: 'border-red-800' },
  error: { bg: 'bg-red-950/50', text: 'text-red-400', border: 'border-red-900' },
  warning: { bg: 'bg-yellow-950/50', text: 'text-yellow-400', border: 'border-yellow-900' },
  info: { bg: 'bg-blue-950/50', text: 'text-blue-400', border: 'border-blue-900' },
  debug: { bg: 'bg-gray-900', text: 'text-gray-400', border: 'border-gray-800' },
}

export function compareSeverity(a: Severity, b: Severity): number {
  return SEVERITY_ORDER[a] - SEVERITY_ORDER[b]
}

export const SEVERITY_HEX_COLORS: Record<Severity, string> = {
  fatal: '#dc2626',
  error: '#ef4444',
  warning: '#f59e0b',
  info: '#3b82f6',
  debug: '#6b7280',
}

export const SEVERITY_LABELS: Record<Severity, string> = {
  fatal: 'Fatal',
  error: 'Error',
  warning: 'Warning',
  info: 'Info',
  debug: 'Debug',
}

export function getSeverityColor(severity: Severity): string {
  return SEVERITY_HEX_COLORS[severity]
}

export function getSeverityLabel(severity: Severity): string {
  return SEVERITY_LABELS[severity]
}
