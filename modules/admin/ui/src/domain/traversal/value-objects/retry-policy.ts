/**
 * RetryPolicy - How to retry failed operations
 * Mirrors: com.monadial.waygrid.common.domain.model.routing.RetryPolicy
 */
export type RetryPolicy =
  | { readonly type: 'NoRetry' }
  | { readonly type: 'Linear'; readonly delay: string; readonly maxRetries: number }
  | { readonly type: 'Exponential'; readonly baseDelay: string; readonly maxRetries: number }
  | {
      readonly type: 'BoundedExponential'
      readonly baseDelay: string
      readonly maxDelay: string
      readonly maxRetries: number
    }

export const RetryPolicies = {
  NoRetry: (): RetryPolicy => ({ type: 'NoRetry' }),
  Linear: (delay: string, maxRetries: number): RetryPolicy => ({
    type: 'Linear',
    delay,
    maxRetries,
  }),
  Exponential: (baseDelay: string, maxRetries: number): RetryPolicy => ({
    type: 'Exponential',
    baseDelay,
    maxRetries,
  }),
  BoundedExponential: (
    baseDelay: string,
    maxDelay: string,
    maxRetries: number
  ): RetryPolicy => ({
    type: 'BoundedExponential',
    baseDelay,
    maxDelay,
    maxRetries,
  }),
}

export function formatRetryPolicy(policy: RetryPolicy): string {
  switch (policy.type) {
    case 'NoRetry':
      return 'No retry'
    case 'Linear':
      return `Linear: ${policy.delay}, max ${policy.maxRetries}`
    case 'Exponential':
      return `Exponential: ${policy.baseDelay}, max ${policy.maxRetries}`
    case 'BoundedExponential':
      return `Bounded exp: ${policy.baseDelay}-${policy.maxDelay}, max ${policy.maxRetries}`
  }
}
