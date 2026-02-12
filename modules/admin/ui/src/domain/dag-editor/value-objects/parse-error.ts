/**
 * ParseError - Represents an error during DSL parsing
 * Used for Monaco editor diagnostics and validation feedback
 */
export interface ParseError {
  readonly message: string
  readonly line: number
  readonly column: number
  readonly endLine?: number
  readonly endColumn?: number
  readonly severity: 'error' | 'warning' | 'info'
}

export function createParseError(
  message: string,
  line: number,
  column: number,
  severity: 'error' | 'warning' | 'info' = 'error'
): ParseError {
  return { message, line, column, severity }
}

export function createParseErrorWithRange(
  message: string,
  line: number,
  column: number,
  endLine: number,
  endColumn: number,
  severity: 'error' | 'warning' | 'info' = 'error'
): ParseError {
  return { message, line, column, endLine, endColumn, severity }
}
