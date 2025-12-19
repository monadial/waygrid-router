package com.monadial.waygrid.common.domain.model.traversal.spec

import scala.concurrent.duration.FiniteDuration

import com.monadial.waygrid.common.domain.model.parameter.ParameterValue
import com.monadial.waygrid.common.domain.model.resiliency.RetryPolicy
import com.monadial.waygrid.common.domain.model.routing.Value.DeliveryStrategy
import com.monadial.waygrid.common.domain.model.traversal.condition.Condition
import com.monadial.waygrid.common.domain.model.traversal.dag.JoinStrategy
import com.monadial.waygrid.common.domain.value.Address.ServiceAddress

/**
 * Specification node for DAG definition.
 *
 * A node represents a service to invoke during traversal. Nodes can be:
 * - Standard: Linear node with optional success/failure continuations
 * - Fork: Fan-out node that initiates parallel branches
 * - Join: Fan-in node that synchronizes branches with configurable strategy
 *
 * Each node can carry parameters that are passed to the service at invocation time.
 * Parameters can be literal values or references to secrets in the secure store.
 */
sealed trait Node:
  def address: ServiceAddress
  def retryPolicy: RetryPolicy
  def deliveryStrategy: DeliveryStrategy
  def parameters: Node.NodeParameters
  def label: Option[String]

object Node:

  /** Type alias for node parameters map */
  type NodeParameters = Map[String, ParameterValue]

  final case class ConditionalEdge(
    condition: Condition,
    to: Node
  )

  /**
   * Standard linear node (current behavior).
   *
   * Supports binary branching via onSuccess/onFailure edges.
   * This is the default node type for simple linear DAG specifications.
   *
   * @param address          Service address to invoke
   * @param retryPolicy      Retry policy for this node
   * @param deliveryStrategy Delivery timing (immediate, scheduled)
   * @param parameters       Parameters to pass to the service
   * @param onSuccess        Next node on successful completion
   * @param onFailure        Next node on failure (after retries exhausted)
   * @param onConditions     Conditional edges evaluated on success
   * @param label            Optional human-readable label
   */
  final case class Standard(
    address: ServiceAddress,
    retryPolicy: RetryPolicy,
    deliveryStrategy: DeliveryStrategy,
    parameters: NodeParameters = Map.empty,
    onSuccess: Option[Node],
    onFailure: Option[Node],
    onConditions: List[ConditionalEdge] = Nil,
    label: Option[String] = None
  ) extends Node

  /**
   * Fork node for parallel branch execution (fan-out).
   *
   * Initiates multiple parallel branches, each executing independently.
   * All branches must converge at a corresponding Join node with matching joinNodeId.
   *
   * @param address          Service address for the fork coordination point
   * @param retryPolicy      Retry policy for the fork node itself
   * @param deliveryStrategy Delivery timing
   * @param parameters       Parameters to pass to the fork service
   * @param branches         Named branches to execute in parallel (name -> entry node)
   * @param joinNodeId       Identifier linking this Fork to its corresponding Join
   * @param label            Optional human-readable label
   */
  final case class Fork(
    address: ServiceAddress,
    retryPolicy: RetryPolicy,
    deliveryStrategy: DeliveryStrategy,
    parameters: NodeParameters = Map.empty,
    branches: Map[String, Node],
    joinNodeId: String,
    label: Option[String] = None
  ) extends Node

  /**
   * Join node for branch synchronization (fan-in).
   *
   * Waits for branches from a corresponding Fork node to complete.
   * The join strategy determines when execution continues:
   * - And: All branches must complete successfully
   * - Or: First branch to complete triggers continuation (others are canceled)
   * - Quorum(n): At least n branches must complete successfully
   *
   * @param address          Service address for the join coordination point
   * @param retryPolicy      Retry policy for the join node itself
   * @param deliveryStrategy Delivery timing for continuation after join
   * @param parameters       Parameters to pass to the join service
   * @param joinNodeId       Identifier linking this Join to its corresponding Fork
   * @param strategy         Join completion strategy (And, Or, Quorum)
   * @param timeout          Optional timeout for branch completion
   * @param onSuccess        Next node after successful join
   * @param onFailure        Next node if join fails (not enough branches)
   * @param onTimeout        Next node if join times out
   * @param label            Optional human-readable label
   */
  final case class Join(
    address: ServiceAddress,
    retryPolicy: RetryPolicy,
    deliveryStrategy: DeliveryStrategy,
    parameters: NodeParameters = Map.empty,
    joinNodeId: String,
    strategy: JoinStrategy,
    timeout: Option[FiniteDuration],
    onSuccess: Option[Node],
    onFailure: Option[Node],
    onTimeout: Option[Node],
    label: Option[String] = None
  ) extends Node

  // ---------------------------------------------------------------------------
  // Companion Helpers
  // ---------------------------------------------------------------------------

  /**
   * Create a standard linear node with immediate delivery and no retries.
   */
  def standard(
    address: ServiceAddress,
    onSuccess: Option[Node] = None,
    onFailure: Option[Node] = None,
    onConditions: List[ConditionalEdge] = Nil,
    parameters: NodeParameters = Map.empty,
    label: Option[String] = None
  ): Standard =
    Standard(
      address = address,
      retryPolicy = RetryPolicy.None,
      deliveryStrategy = DeliveryStrategy.Immediate,
      parameters = parameters,
      onSuccess = onSuccess,
      onFailure = onFailure,
      onConditions = onConditions,
      label = label
    )

  def when(condition: Condition, to: Node): ConditionalEdge =
    ConditionalEdge(condition, to)

  /**
   * Create a fork node with the given branches.
   */
  def fork(
    address: ServiceAddress,
    branches: Map[String, Node],
    joinNodeId: String,
    parameters: NodeParameters = Map.empty,
    label: Option[String] = None
  ): Fork =
    Fork(
      address = address,
      retryPolicy = RetryPolicy.None,
      deliveryStrategy = DeliveryStrategy.Immediate,
      parameters = parameters,
      branches = branches,
      joinNodeId = joinNodeId,
      label = label
    )

  /**
   * Create a join node with the given strategy.
   */
  def join(
    address: ServiceAddress,
    joinNodeId: String,
    strategy: JoinStrategy,
    timeout: Option[FiniteDuration] = None,
    onSuccess: Option[Node] = None,
    onFailure: Option[Node] = None,
    onTimeout: Option[Node] = None,
    parameters: NodeParameters = Map.empty,
    label: Option[String] = None
  ): Join =
    Join(
      address = address,
      retryPolicy = RetryPolicy.None,
      deliveryStrategy = DeliveryStrategy.Immediate,
      parameters = parameters,
      joinNodeId = joinNodeId,
      strategy = strategy,
      timeout = timeout,
      onSuccess = onSuccess,
      onFailure = onFailure,
      onTimeout = onTimeout,
      label = label
    )
