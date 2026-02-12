/**
 * Base domain error class for all bounded contexts
 */
export abstract class DomainError extends Error {
  abstract readonly code: string
  abstract readonly context: string

  constructor(message: string) {
    super(message)
    this.name = this.constructor.name
  }

  toJSON() {
    return {
      name: this.name,
      code: this.code,
      context: this.context,
      message: this.message,
    }
  }
}

export class ValidationError extends DomainError {
  readonly code = 'VALIDATION_ERROR'
  readonly context = 'shared'
  readonly field: string

  constructor(field: string, message: string) {
    super(`Validation error on ${field}: ${message}`)
    this.field = field
  }
}

export class NotFoundError extends DomainError {
  readonly code = 'NOT_FOUND'
  readonly context = 'shared'
  readonly entityType: string
  readonly entityId: string

  constructor(entityType: string, entityId: string) {
    super(`${entityType} with id ${entityId} not found`)
    this.entityType = entityType
    this.entityId = entityId
  }
}
