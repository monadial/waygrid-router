/**
 * NodeComponent - Type of component in the Waygrid network
 * Mirrors: com.monadial.waygrid.common.domain.model.node.NodeComponent
 */
export const NodeComponent = {
  Origin: 'origin',
  Processor: 'processor',
  Destination: 'destination',
  System: 'system',
} as const

export type NodeComponent = (typeof NodeComponent)[keyof typeof NodeComponent]

export function isNodeComponent(value: string): value is NodeComponent {
  return Object.values(NodeComponent).includes(value as NodeComponent)
}

export function parseNodeComponent(value: string): NodeComponent {
  const lower = value.toLowerCase()
  if (!isNodeComponent(lower)) {
    throw new Error(`Invalid NodeComponent: ${value}`)
  }
  return lower
}
