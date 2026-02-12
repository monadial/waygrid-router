import { NodeDescriptor, parseNodeDescriptor } from './node-descriptor'
import { NodeId, createNodeId } from './node-id'

/**
 * NodeAddress - Full node URI: waygrid://<component>/<service>?region=<region>&clusterId=<id>&nodeId=<id>
 * Mirrors: com.monadial.waygrid.common.domain.model.node.NodeAddress
 */
export interface NodeAddress {
  readonly descriptor: NodeDescriptor
  readonly region: string
  readonly clusterId: string
  readonly nodeId: NodeId
}

export function createNodeAddress(
  descriptor: NodeDescriptor,
  region: string,
  clusterId: string,
  nodeId: string
): NodeAddress {
  return {
    descriptor,
    region,
    clusterId,
    nodeId: createNodeId(nodeId),
  }
}

export function formatNodeAddress(address: NodeAddress): string {
  const { descriptor, region, clusterId, nodeId } = address
  return `waygrid://${descriptor.component}/${descriptor.service}?region=${region}&clusterId=${clusterId}&nodeId=${nodeId}`
}

export function parseNodeAddress(uri: string): NodeAddress {
  const url = new URL(uri)
  if (url.protocol !== 'waygrid:') {
    throw new Error(`Invalid protocol in NodeAddress: ${url.protocol}`)
  }

  const pathParts = url.pathname.replace(/^\/\//, '').split('/')
  if (pathParts.length !== 2) {
    throw new Error(`Invalid path in NodeAddress: ${url.pathname}`)
  }

  const [component, service] = pathParts
  const descriptor = parseNodeDescriptor(`${component}.${service}`)

  const region = url.searchParams.get('region')
  const clusterId = url.searchParams.get('clusterId')
  const nodeId = url.searchParams.get('nodeId')

  if (!region || !clusterId || !nodeId) {
    throw new Error(`Missing required parameters in NodeAddress: ${uri}`)
  }

  return createNodeAddress(descriptor, region, clusterId, nodeId)
}

/**
 * ServiceAddress - Service-level URI (without node-specific parameters)
 * Mirrors: com.monadial.waygrid.common.domain.model.node.ServiceAddress
 */
export interface ServiceAddress {
  readonly descriptor: NodeDescriptor
}

export function createServiceAddress(descriptor: NodeDescriptor): ServiceAddress {
  return { descriptor }
}

export function formatServiceAddress(address: ServiceAddress): string {
  return `waygrid://${address.descriptor.component}/${address.descriptor.service}`
}

export function parseServiceAddress(uri: string): ServiceAddress {
  const url = new URL(uri)
  if (url.protocol !== 'waygrid:') {
    throw new Error(`Invalid protocol in ServiceAddress: ${url.protocol}`)
  }

  const pathParts = url.pathname.replace(/^\/\//, '').split('/')
  if (pathParts.length !== 2) {
    throw new Error(`Invalid path in ServiceAddress: ${url.pathname}`)
  }

  const [component, service] = pathParts
  const descriptor = parseNodeDescriptor(`${component}.${service}`)

  return createServiceAddress(descriptor)
}
