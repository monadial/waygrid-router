/**
 * API Error types for infrastructure layer
 * These are infrastructure concerns, not domain errors
 */

export class ApiError extends Error {
  constructor(
    message: string,
    public readonly status: number,
    public readonly data?: unknown
  ) {
    super(message)
    this.name = 'ApiError'
  }

  static isApiError(error: unknown): error is ApiError {
    return error instanceof ApiError
  }

  static fromResponse(status: number, data: unknown): ApiError {
    const message = typeof data === 'object' && data !== null && 'error' in data
      ? String((data as { error: unknown }).error)
      : `Request failed with status ${status}`
    return new ApiError(message, status, data)
  }
}

export class NetworkError extends Error {
  constructor(message: string = 'Network error occurred') {
    super(message)
    this.name = 'NetworkError'
  }

  static isNetworkError(error: unknown): error is NetworkError {
    return error instanceof NetworkError
  }
}

export class TimeoutError extends Error {
  constructor(message: string = 'Request timed out') {
    super(message)
    this.name = 'TimeoutError'
  }

  static isTimeoutError(error: unknown): error is TimeoutError {
    return error instanceof TimeoutError
  }
}

export type InfrastructureError = ApiError | NetworkError | TimeoutError

export function isInfrastructureError(error: unknown): error is InfrastructureError {
  return (
    ApiError.isApiError(error) ||
    NetworkError.isNetworkError(error) ||
    TimeoutError.isTimeoutError(error)
  )
}
