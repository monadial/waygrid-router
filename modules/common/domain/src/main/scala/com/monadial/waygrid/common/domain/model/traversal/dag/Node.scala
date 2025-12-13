package com.monadial.waygrid.common.domain.model.traversal.dag

import com.monadial.waygrid.common.domain.model.resiliency.RetryPolicy
import com.monadial.waygrid.common.domain.model.routing.Value.DeliveryStrategy
import com.monadial.waygrid.common.domain.model.traversal.dag.Value.NodeId
import com.monadial.waygrid.common.domain.value.Address.ServiceAddress

/**
 * A node in a DAG representing a processing step in the traversal.
 *
 * @param id Unique identifier for this node within the DAG
 * @param label Optional human-readable label for debugging/display
 * @param retryPolicy Defines retry behavior on failure
 * @param deliveryStrategy When to execute (immediate, scheduled, etc.)
 * @param address Service address for the processing endpoint
 * @param nodeType Type of node (Standard, Fork, or Join) - defaults to Standard
 *                 for backward compatibility with linear DAGs
 */
final case class Node(
  id: NodeId,
  label: Option[String],
  retryPolicy: RetryPolicy,
  deliveryStrategy: DeliveryStrategy,
  address: ServiceAddress,
  nodeType: NodeType = NodeType.Standard
):
  /** Returns true if this is a Fork node */
  inline def isFork: Boolean = nodeType match
    case NodeType.Fork(_) => true
    case _                => false

  /** Returns true if this is a Join node */
  inline def isJoin: Boolean = nodeType match
    case NodeType.Join(_, _, _) => true
    case _                      => false

  /** Returns true if this is a Standard node */
  inline def isStandard: Boolean = nodeType == NodeType.Standard

  /** Extract ForkId if this is a Fork node */
  def forkId: Option[Value.ForkId] = nodeType match
    case NodeType.Fork(id)       => Some(id)
    case NodeType.Join(id, _, _) => Some(id)
    case _                       => None
