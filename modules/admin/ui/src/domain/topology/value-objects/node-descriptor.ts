import { NodeComponent } from './node-component'
import { NodeService, createNodeService } from './node-service'

/**
 * NodeDescriptor - Component + Service identifier (e.g., "origin.http")
 * Mirrors: com.monadial.waygrid.common.domain.model.node.NodeDescriptor
 */
export interface NodeDescriptor {
  readonly component: NodeComponent
  readonly service: NodeService
}

export function createNodeDescriptor(
  component: NodeComponent,
  service: string
): NodeDescriptor {
  return {
    component,
    service: createNodeService(service),
  }
}

export function parseNodeDescriptor(value: string): NodeDescriptor {
  const [component, service] = value.split('.')
  if (!component || !service) {
    throw new Error(`Invalid NodeDescriptor format: ${value}`)
  }

  const parsedComponent = component.toLowerCase()
  if (
    parsedComponent !== 'origin' &&
    parsedComponent !== 'processor' &&
    parsedComponent !== 'destination' &&
    parsedComponent !== 'system'
  ) {
    throw new Error(`Invalid component in NodeDescriptor: ${component}`)
  }

  return createNodeDescriptor(parsedComponent, service)
}

export function formatNodeDescriptor(descriptor: NodeDescriptor): string {
  return `${descriptor.component}.${descriptor.service}`
}

export const NodeDescriptors = {
  Origin: (service: string) => createNodeDescriptor(NodeComponent.Origin, service),
  Processor: (service: string) => createNodeDescriptor(NodeComponent.Processor, service),
  Destination: (service: string) => createNodeDescriptor(NodeComponent.Destination, service),
  System: (service: string) => createNodeDescriptor(NodeComponent.System, service),
}
