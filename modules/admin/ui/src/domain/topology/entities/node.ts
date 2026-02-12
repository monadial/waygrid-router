import { Timestamp } from '@/domain/shared'
import { NodeId } from '../value-objects/node-id'
import { NodeDescriptor } from '../value-objects/node-descriptor'
import { NodeAddress } from '../value-objects/node-address'

/**
 * Node - Represents a service node in the Waygrid network
 * Mirrors: com.monadial.waygrid.common.domain.model.node entity
 */
export interface Node {
  readonly id: NodeId
  readonly descriptor: NodeDescriptor
  readonly clusterId: string
  readonly region: string
  readonly address: NodeAddress
  readonly startedAt: Timestamp
  readonly uptime: number // seconds
}

export function createNode(params: {
  id: string
  descriptor: NodeDescriptor
  clusterId: string
  region: string
  address: NodeAddress
  startedAt: string
  uptime: number
}): Node {
  return {
    id: params.id as NodeId,
    descriptor: params.descriptor,
    clusterId: params.clusterId,
    region: params.region,
    address: params.address,
    startedAt: params.startedAt as Timestamp,
    uptime: params.uptime,
  }
}

export function isNodeHealthy(node: Node): boolean {
  // Node is considered healthy if it has been up for more than 30 seconds
  return node.uptime > 30
}

export function getNodeDisplayName(node: Node): string {
  return `${node.descriptor.component}.${node.descriptor.service}`
}
