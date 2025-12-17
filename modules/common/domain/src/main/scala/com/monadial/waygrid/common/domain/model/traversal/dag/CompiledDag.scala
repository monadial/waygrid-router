package com.monadial.waygrid.common.domain.model.traversal.dag

import com.monadial.waygrid.common.domain.model.traversal.dag.Value.NodeId
import com.monadial.waygrid.common.domain.model.traversal.spec.Node.NodeParameters

/**
 * Result of DAG compilation that separates cacheable structure from variable parameters.
 *
 * ==Why Separate Parameters from DAG?==
 * The DAG structure (nodes, edges, retry policies, etc.) is deterministic and cacheable.
 * The DAG hash uniquely identifies a structural configuration, allowing DAG reuse across
 * multiple traversals.
 *
 * Parameters, however, are variable per invocation:
 * - Different API keys for different tenants
 * - Different model configurations (temperature, max tokens)
 * - Different channel IDs or webhooks
 *
 * By separating parameters from the DAG, we can:
 * 1. Cache and reuse DAGs across traversals with different parameters
 * 2. Avoid recompilation when only parameters change
 * 3. Store parameters separately (e.g., encrypted in database)
 *
 * ==Example==
 * {{{
 * val compiledDag = dagCompiler.compile(spec, salt)
 *
 * // The DAG can be cached by hash
 * val cachedDag = dagCache.getOrCreate(compiledDag.dag.hash) { compiledDag.dag }
 *
 * // Parameters are stored/resolved separately for each traversal
 * traversalState.create(
 *   dagHash = cachedDag.hash,
 *   parameters = compiledDag.parameters
 * )
 * }}}
 *
 * @param dag The compiled DAG structure (cacheable)
 * @param parameters Node parameters keyed by NodeId (variable per invocation)
 */
final case class CompiledDag(
  dag: Dag,
  parameters: Map[NodeId, NodeParameters]
):
  /** Get parameters for a specific node, returns empty map if not found */
  def parametersFor(nodeId: NodeId): NodeParameters =
    parameters.getOrElse(nodeId, Map.empty)

  /** Check if a node has any parameters defined */
  def hasParameters(nodeId: NodeId): Boolean =
    parameters.get(nodeId).exists(_.nonEmpty)

  /** Get all node IDs that have parameters */
  def nodesWithParameters: Set[NodeId] =
    parameters.filter(_._2.nonEmpty).keySet

object CompiledDag:
  /** Create a CompiledDag with no parameters (backward compatibility) */
  def fromDag(dag: Dag): CompiledDag =
    CompiledDag(dag, Map.empty)
