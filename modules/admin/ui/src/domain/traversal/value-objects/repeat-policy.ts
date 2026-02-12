/**
 * RepeatPolicy - How often to repeat DAG execution
 * Mirrors: com.monadial.waygrid.common.domain.model.routing.RepeatPolicy
 */
export type RepeatPolicy =
  | { readonly type: 'NoRepeat' }
  | { readonly type: 'Indefinitely'; readonly every: string }
  | { readonly type: 'Times'; readonly every: string; readonly times: number }
  | { readonly type: 'Until'; readonly every: string; readonly until: string }

export const RepeatPolicies = {
  NoRepeat: (): RepeatPolicy => ({ type: 'NoRepeat' }),
  Indefinitely: (every: string): RepeatPolicy => ({ type: 'Indefinitely', every }),
  Times: (every: string, times: number): RepeatPolicy => ({ type: 'Times', every, times }),
  Until: (every: string, until: string): RepeatPolicy => ({ type: 'Until', every, until }),
}

export function formatRepeatPolicy(policy: RepeatPolicy): string {
  switch (policy.type) {
    case 'NoRepeat':
      return 'No repeat'
    case 'Indefinitely':
      return `Every ${policy.every}`
    case 'Times':
      return `${policy.times}x every ${policy.every}`
    case 'Until':
      return `Every ${policy.every} until ${policy.until}`
  }
}
