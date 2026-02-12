import { ApiError, NetworkError, TimeoutError } from './errors'

interface RequestConfig extends Omit<RequestInit, 'body'> {
  timeout?: number
  params?: Record<string, string | number | boolean | undefined>
}

interface ApiResponse<T> {
  data: T
  status: number
  headers: Headers
}

const DEFAULT_TIMEOUT = 30000

/**
 * Base HTTP client for API communication
 * Part of the Infrastructure layer - handles all HTTP concerns
 */
class HttpClient {
  constructor(private readonly baseUrl: string = '') {}

  private buildUrl(path: string, params?: RequestConfig['params']): string {
    const url = new URL(path, this.baseUrl || window.location.origin)
    if (params) {
      Object.entries(params).forEach(([key, value]) => {
        if (value !== undefined) {
          url.searchParams.append(key, String(value))
        }
      })
    }
    return url.toString()
  }

  private async request<T>(
    method: string,
    path: string,
    body?: unknown,
    config: RequestConfig = {}
  ): Promise<ApiResponse<T>> {
    const { timeout = DEFAULT_TIMEOUT, params, ...fetchConfig } = config

    const controller = new AbortController()
    const timeoutId = setTimeout(() => controller.abort(), timeout)

    try {
      const response = await fetch(this.buildUrl(path, params), {
        method,
        signal: controller.signal,
        headers: {
          'Content-Type': 'application/json',
          Accept: 'application/json',
          ...fetchConfig.headers,
        },
        body: body ? JSON.stringify(body) : undefined,
        ...fetchConfig,
      })

      clearTimeout(timeoutId)

      if (!response.ok) {
        const errorData = await response.json().catch(() => ({}))
        throw ApiError.fromResponse(response.status, errorData)
      }

      // Handle empty responses
      const text = await response.text()
      const data = text ? JSON.parse(text) : {}

      return { data, status: response.status, headers: response.headers }
    } catch (error) {
      clearTimeout(timeoutId)

      if (error instanceof ApiError) throw error
      if (error instanceof Error && error.name === 'AbortError') {
        throw new TimeoutError(`Request timed out after ${timeout}ms`)
      }
      if (error instanceof TypeError) {
        throw new NetworkError('Network error occurred')
      }
      throw error
    }
  }

  get<T>(path: string, config?: RequestConfig): Promise<ApiResponse<T>> {
    return this.request<T>('GET', path, undefined, config)
  }

  post<T>(path: string, body?: unknown, config?: RequestConfig): Promise<ApiResponse<T>> {
    return this.request<T>('POST', path, body, config)
  }

  put<T>(path: string, body?: unknown, config?: RequestConfig): Promise<ApiResponse<T>> {
    return this.request<T>('PUT', path, body, config)
  }

  patch<T>(path: string, body?: unknown, config?: RequestConfig): Promise<ApiResponse<T>> {
    return this.request<T>('PATCH', path, body, config)
  }

  delete<T>(path: string, config?: RequestConfig): Promise<ApiResponse<T>> {
    return this.request<T>('DELETE', path, undefined, config)
  }
}

/**
 * Service-specific API clients
 * Each maps to a Waygrid backend service
 */
export const topologyApi = new HttpClient('/api/topology')
export const waystationApi = new HttpClient('/api/waystation')
export const schedulerApi = new HttpClient('/api/scheduler')
export const iamApi = new HttpClient('/api/iam')
export const historyApi = new HttpClient('/api/history')
export const dagRegistryApi = new HttpClient('/api/dag-registry')
export const secureStoreApi = new HttpClient('/api/secure-store')
export const billingApi = new HttpClient('/api/billing')
export const kmsApi = new HttpClient('/api/kms')
export const blobStoreApi = new HttpClient('/api/blob-store')
export const originApi = new HttpClient('/api/origin')

export { HttpClient }
