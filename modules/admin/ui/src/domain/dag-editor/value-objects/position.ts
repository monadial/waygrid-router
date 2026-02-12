/**
 * Position - Represents x,y coordinates for visual node placement
 */
export interface Position {
  readonly x: number
  readonly y: number
}

export function createPosition(x: number, y: number): Position {
  return { x, y }
}

export function positionEquals(a: Position, b: Position): boolean {
  return a.x === b.x && a.y === b.y
}

export const DEFAULT_POSITION: Position = { x: 0, y: 0 }
