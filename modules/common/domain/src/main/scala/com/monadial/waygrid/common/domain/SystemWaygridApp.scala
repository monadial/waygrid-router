package com.monadial.waygrid.common.domain

import com.monadial.waygrid.common.domain.model.node.Value.{ NodeDescriptor, NodeService }

/**
 * The `SystemWaygridApp` object defines the core system services required for the Waygrid application.
 * Each service is represented as a `NodeDescriptor.System` with a specific `NodeService` name.
 * These services are essential for the operation of the distributed, event-driven message router.
 */
object SystemWaygridApp:

  /**
   * Represents the billing service, which is responsible for tracking usage and costs.
   * This service enables usage-based accounting, quota enforcement, and integration
   * with external billing and invoicing systems.
   */
  transparent inline def Billing: NodeDescriptor = NodeDescriptor.System(NodeService("billing"))

  /**
   * Represents the DAG registry service, which maintains the directed acyclic graphs (DAGs)
   * that define routing and processing pipelines. This service ensures that message flows
   * are versioned, validated, and accessible to all nodes in the system.
   */
  transparent inline def DagRegistry: NodeDescriptor = NodeDescriptor.System(NodeService("dag-registry"))

  /**
   * Represents the history service, which is responsible for managing and storing historical data.
   * This service plays a critical role in providing insights and maintaining an audit trail of system events.
   */
  transparent inline def History: NodeDescriptor = NodeDescriptor.System(NodeService("history"))

  /**
   * Represents the IAM (Identity and Access Management) service, which is responsible for
   * authentication, authorization, and account management. This service enforces security
   * policies and ensures that only trusted identities can interact with the system.
   */
  transparent inline def IAM: NodeDescriptor = NodeDescriptor.System(NodeService("iam"))

  /**
   * Represents the KMS (Key Management Service), which is responsible for cryptographic
   * operations such as key storage, signing, and validation. This service ensures that
   * sensitive data and communications remain secure within the system.
   */
  transparent inline def KMS: NodeDescriptor = NodeDescriptor.System(NodeService("kms"))

  /**
   * Represents the scheduler service, which is responsible for scheduling and orchestrating tasks.
   * This service ensures that tasks are executed in a timely and coordinated manner across the system.
   */
  transparent inline def Scheduler: NodeDescriptor = NodeDescriptor.System(NodeService("scheduler"))

  /**
   * Represents the secure store service, which is responsible for securely storing
   * sensitive values such as credentials, certificates, and secrets. This service
   * provides controlled access and auditability for secret management.
   */
  transparent inline def SecureStore: NodeDescriptor = NodeDescriptor.System(NodeService("secure-store"))

  /**
   * Represents the topology service, which is responsible for managing the system's node topology.
   * This service maintains the structure and relationships between nodes in the distributed system.
   */
  transparent inline def Topology: NodeDescriptor = NodeDescriptor.System(NodeService("topology"))

  /**
   * Represents the waystation service, which is responsible for intermediate message processing.
   * This service acts as a temporary holding area for messages, enabling efficient routing and processing.
   */
  transparent inline def Waystation: NodeDescriptor = NodeDescriptor.System(NodeService("waystation"))

  /**
   * Represents the blobstore service, which is responsible for storing and retrieving large binary objects.
   */
  transparent inline def BlobStore: NodeDescriptor = NodeDescriptor.System(NodeService("blob-store"))

  /**
   * A list of all required system services for the Waygrid application.
   * These services are essential for the proper functioning of the distributed message router.
   */
  inline def required = List(History, Scheduler, Topology, Waystation)
