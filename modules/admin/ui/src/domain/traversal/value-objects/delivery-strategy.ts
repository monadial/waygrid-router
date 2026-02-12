/**
 * DeliveryStrategy - When to deliver events
 * Mirrors: com.monadial.waygrid.common.domain.model.routing.DeliveryStrategy
 */
export type DeliveryStrategy =
  | { readonly type: 'Immediate' }
  | { readonly type: 'ScheduleAfter'; readonly delay: string }

export const DeliveryStrategies = {
  Immediate: (): DeliveryStrategy => ({ type: 'Immediate' }),
  ScheduleAfter: (delay: string): DeliveryStrategy => ({ type: 'ScheduleAfter', delay }),
}

export function formatDeliveryStrategy(strategy: DeliveryStrategy): string {
  switch (strategy.type) {
    case 'Immediate':
      return 'Immediate'
    case 'ScheduleAfter':
      return `After ${strategy.delay}`
  }
}
