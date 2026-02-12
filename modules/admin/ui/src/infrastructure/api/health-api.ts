/**
 * Health check API for all services
 * Cross-cutting concern for monitoring
 */

interface ServiceHealth {
  name: string
  url: string
  healthy: boolean
}

const SERVICES = [
  { name: 'topology', url: '/api/topology' },
  { name: 'waystation', url: '/api/waystation' },
  { name: 'scheduler', url: '/api/scheduler' },
  { name: 'iam', url: '/api/iam' },
  { name: 'history', url: '/api/history' },
  { name: 'dag-registry', url: '/api/dag-registry' },
  { name: 'secure-store', url: '/api/secure-store' },
  { name: 'billing', url: '/api/billing' },
  { name: 'origin-http', url: '/api/origin' },
] as const

export const HealthApiClient = {
  /**
   * Check health of a single service
   */
  async checkService(url: string): Promise<boolean> {
    try {
      const response = await fetch(`${url}/health-check`, {
        method: 'GET',
        headers: { Accept: 'application/json' },
      })
      return response.ok
    } catch {
      return false
    }
  },

  /**
   * Check health of all known services
   */
  async checkAllServices(): Promise<ServiceHealth[]> {
    const results = await Promise.allSettled(
      SERVICES.map(async (service) => {
        const healthy = await this.checkService(service.url)
        return { ...service, healthy }
      })
    )

    return results.map((result, index) => ({
      ...SERVICES[index],
      healthy: result.status === 'fulfilled' ? result.value.healthy : false,
    }))
  },

  /**
   * Get list of all service endpoints
   */
  getServices() {
    return SERVICES
  },
}
