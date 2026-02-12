/**
 * Waygrid DSL Lexer - Tokenizes the DSL for parsing and syntax highlighting
 */

export type TokenType =
  // Keywords
  | 'KEYWORD_DAG'
  | 'KEYWORD_ENTRY'
  | 'KEYWORD_FORK'
  | 'KEYWORD_JOIN'
  | 'KEYWORD_TIMEOUT'
  | 'KEYWORD_RETRY'
  | 'KEYWORD_REPEAT'
  | 'KEYWORD_ALL'
  // Arrow guards
  | 'ARROW_SUCCESS'
  | 'ARROW_FAILURE'
  | 'ARROW_ANY'
  | 'ARROW_TIMEOUT'
  | 'ARROW_CONDITION'
  // Node components
  | 'COMPONENT_ORIGIN'
  | 'COMPONENT_PROCESSOR'
  | 'COMPONENT_DESTINATION'
  | 'COMPONENT_SYSTEM'
  // Literals
  | 'STRING'
  | 'NUMBER'
  | 'IDENTIFIER'
  // Punctuation
  | 'LBRACE'
  | 'RBRACE'
  | 'COLON'
  | 'DOT'
  // Other
  | 'COMMENT'
  | 'NEWLINE'
  | 'WHITESPACE'
  | 'ERROR'
  | 'EOF'

export interface Token {
  type: TokenType
  value: string
  line: number
  column: number
  length: number
}

export interface LexerState {
  input: string
  pos: number
  line: number
  column: number
  tokens: Token[]
}

const KEYWORDS: Record<string, TokenType> = {
  dag: 'KEYWORD_DAG',
  entry: 'KEYWORD_ENTRY',
  fork: 'KEYWORD_FORK',
  join: 'KEYWORD_JOIN',
  timeout: 'KEYWORD_TIMEOUT',
  retry: 'KEYWORD_RETRY',
  repeat: 'KEYWORD_REPEAT',
  all: 'KEYWORD_ALL',
}

const COMPONENTS: Record<string, TokenType> = {
  origin: 'COMPONENT_ORIGIN',
  processor: 'COMPONENT_PROCESSOR',
  destination: 'COMPONENT_DESTINATION',
  system: 'COMPONENT_SYSTEM',
}

function createLexerState(input: string): LexerState {
  return {
    input,
    pos: 0,
    line: 1,
    column: 1,
    tokens: [],
  }
}

function peek(state: LexerState, offset = 0): string {
  return state.input[state.pos + offset] ?? ''
}

function peekString(state: LexerState, length: number): string {
  return state.input.slice(state.pos, state.pos + length)
}

function advance(state: LexerState): string {
  const char = state.input[state.pos] ?? ''
  state.pos++
  if (char === '\n') {
    state.line++
    state.column = 1
  } else {
    state.column++
  }
  return char
}

function addToken(state: LexerState, type: TokenType, value: string, startLine: number, startColumn: number): void {
  state.tokens.push({
    type,
    value,
    line: startLine,
    column: startColumn,
    length: value.length,
  })
}

function isWhitespace(char: string): boolean {
  return char === ' ' || char === '\t' || char === '\r'
}

function isDigit(char: string): boolean {
  return char >= '0' && char <= '9'
}

function isAlpha(char: string): boolean {
  return (char >= 'a' && char <= 'z') || (char >= 'A' && char <= 'Z') || char === '_' || char === '-'
}

function isAlphaNumeric(char: string): boolean {
  return isAlpha(char) || isDigit(char)
}

function scanString(state: LexerState): void {
  const startLine = state.line
  const startColumn = state.column
  advance(state) // consume opening quote

  let value = ''
  while (state.pos < state.input.length && peek(state) !== '"' && peek(state) !== '\n') {
    if (peek(state) === '\\' && peek(state, 1) === '"') {
      advance(state)
      value += '"'
    } else {
      value += advance(state)
    }
  }

  if (peek(state) === '"') {
    advance(state) // consume closing quote
    addToken(state, 'STRING', `"${value}"`, startLine, startColumn)
  } else {
    addToken(state, 'ERROR', `"${value}`, startLine, startColumn)
  }
}

function scanNumber(state: LexerState): void {
  const startLine = state.line
  const startColumn = state.column
  let value = ''

  while (state.pos < state.input.length && (isDigit(peek(state)) || peek(state) === '.')) {
    value += advance(state)
  }

  // Handle duration suffix (s, ms, m, h)
  if (['s', 'm', 'h'].includes(peek(state)) || peekString(state, 2) === 'ms') {
    if (peek(state) === 'm' && peek(state, 1) === 's') {
      value += advance(state) + advance(state)
    } else {
      value += advance(state)
    }
  }

  addToken(state, 'NUMBER', value, startLine, startColumn)
}

function scanIdentifier(state: LexerState): void {
  const startLine = state.line
  const startColumn = state.column
  let value = ''

  while (state.pos < state.input.length && isAlphaNumeric(peek(state))) {
    value += advance(state)
  }

  // Check for component.service pattern
  if (peek(state) === '.') {
    const component = value.toLowerCase()
    if (component in COMPONENTS) {
      addToken(state, COMPONENTS[component], value, startLine, startColumn)
      // Consume the dot and service
      const dotCol = state.column
      advance(state) // consume dot
      addToken(state, 'DOT', '.', state.line, dotCol)

      // Scan service name
      const serviceStart = state.column
      let service = ''
      while (state.pos < state.input.length && isAlphaNumeric(peek(state))) {
        service += advance(state)
      }
      if (service) {
        addToken(state, 'IDENTIFIER', service, state.line, serviceStart)
      }
      return
    }
  }

  // Check for keywords
  const lower = value.toLowerCase()
  if (lower in KEYWORDS) {
    addToken(state, KEYWORDS[lower], value, startLine, startColumn)
  } else {
    addToken(state, 'IDENTIFIER', value, startLine, startColumn)
  }
}

function scanArrow(state: LexerState): void {
  const startLine = state.line
  const startColumn = state.column
  advance(state) // consume first -
  advance(state) // consume >

  // Skip whitespace
  while (isWhitespace(peek(state))) {
    advance(state)
  }

  // Read the guard type
  let guard = ''
  while (state.pos < state.input.length && isAlpha(peek(state))) {
    guard += advance(state)
  }

  const guardLower = guard.toLowerCase()
  const arrowMap: Record<string, TokenType> = {
    success: 'ARROW_SUCCESS',
    failure: 'ARROW_FAILURE',
    any: 'ARROW_ANY',
    timeout: 'ARROW_TIMEOUT',
    condition: 'ARROW_CONDITION',
  }

  if (guardLower in arrowMap) {
    addToken(state, arrowMap[guardLower], `->${guard}`, startLine, startColumn)
  } else {
    addToken(state, 'ERROR', `->${guard}`, startLine, startColumn)
  }
}

function scanComment(state: LexerState): void {
  const startLine = state.line
  const startColumn = state.column
  let value = ''

  while (state.pos < state.input.length && peek(state) !== '\n') {
    value += advance(state)
  }

  addToken(state, 'COMMENT', value, startLine, startColumn)
}

/**
 * Tokenize Waygrid DSL source code
 */
export function tokenize(input: string): Token[] {
  const state = createLexerState(input)

  while (state.pos < state.input.length) {
    const char = peek(state)

    // Whitespace
    if (isWhitespace(char)) {
      const startCol = state.column
      let ws = ''
      while (state.pos < state.input.length && isWhitespace(peek(state))) {
        ws += advance(state)
      }
      addToken(state, 'WHITESPACE', ws, state.line, startCol)
      continue
    }

    // Newline
    if (char === '\n') {
      addToken(state, 'NEWLINE', '\n', state.line, state.column)
      advance(state)
      continue
    }

    // Comment
    if (char === '#') {
      scanComment(state)
      continue
    }

    // String
    if (char === '"') {
      scanString(state)
      continue
    }

    // Number
    if (isDigit(char)) {
      scanNumber(state)
      continue
    }

    // Arrow
    if (char === '-' && peek(state, 1) === '>') {
      scanArrow(state)
      continue
    }

    // Identifier or keyword
    if (isAlpha(char)) {
      scanIdentifier(state)
      continue
    }

    // Punctuation
    if (char === '{') {
      addToken(state, 'LBRACE', '{', state.line, state.column)
      advance(state)
      continue
    }

    if (char === '}') {
      addToken(state, 'RBRACE', '}', state.line, state.column)
      advance(state)
      continue
    }

    if (char === ':') {
      addToken(state, 'COLON', ':', state.line, state.column)
      advance(state)
      continue
    }

    if (char === '.') {
      addToken(state, 'DOT', '.', state.line, state.column)
      advance(state)
      continue
    }

    // Unknown character
    addToken(state, 'ERROR', char, state.line, state.column)
    advance(state)
  }

  addToken(state, 'EOF', '', state.line, state.column)
  return state.tokens
}

/**
 * Get tokens without whitespace (for parsing)
 */
export function getSignificantTokens(tokens: Token[]): Token[] {
  return tokens.filter(
    (t) => t.type !== 'WHITESPACE' && t.type !== 'NEWLINE' && t.type !== 'COMMENT'
  )
}
