package com.monadial.waygrid.common.domain.model.traversal.spec

import cats.data.NonEmptyList
import com.monadial.waygrid.common.domain.model.routing.Value.RepeatPolicy
import com.monadial.waygrid.common.domain.model.traversal.spec.Node.NodeParameters
import com.monadial.waygrid.common.domain.value.Address.ServiceAddress

/**
 * High-level specification for a DAG traversal.
 *
 * A Spec defines the structure of a routing DAG through a graph of Node instances.
 * The Spec is compiled into a Dag by the DagCompiler, which generates stable
 * node IDs and hashes for deduplication and caching.
 *
 * @param entryPoints Non-empty list of entry point nodes (supports multiple origins)
 * @param repeatPolicy Policy for repeating the entire traversal
 */
final case class Spec(
  entryPoints: NonEmptyList[Node],
  repeatPolicy: RepeatPolicy,
):

  /**
   * Collect all parameters from all nodes in this Spec.
   *
   * Returns a list of (ServiceAddress, NodeParameters) pairs for nodes that have
   * non-empty parameters. Useful for validation and inspection.
   *
   * @return List of (address, parameters) for all parameterized nodes
   */
  def collectParameters: List[(ServiceAddress, NodeParameters)] =
    Spec.collectParametersFromNodes(entryPoints.toList)

  /**
   * Get all unique service addresses in this Spec.
   */
  def serviceAddresses: Set[ServiceAddress] =
    Spec.collectAddressesFromNodes(entryPoints.toList)

  /**
   * Count of nodes with parameters in this Spec.
   */
  def parameterizedNodeCount: Int =
    collectParameters.size

object Spec:

  /**
   * Create a spec with a single entry point.
   *
   * This is the most common case and provides backward compatibility
   * with the previous single-entry-point API.
   *
   * @param entryPoint   The single entry point node
   * @param repeatPolicy Policy for repeating the traversal
   */
  def single(entryPoint: Node, repeatPolicy: RepeatPolicy): Spec =
    Spec(NonEmptyList.one(entryPoint), repeatPolicy)

  /**
   * Create a spec with multiple entry points.
   *
   * Multiple entry points enable fan-in patterns where multiple origins
   * can trigger independent traversals that may share common sub-graphs.
   *
   * @param first        First entry point (required for non-empty guarantee)
   * @param rest         Additional entry points
   * @param repeatPolicy Policy for repeating the traversal
   */
  def multiple(first: Node, rest: Node*)(repeatPolicy: RepeatPolicy): Spec =
    Spec(NonEmptyList(first, rest.toList), repeatPolicy)

  // ---------------------------------------------------------------------------
  // Private helpers for parameter collection
  // ---------------------------------------------------------------------------

  private def collectParametersFromNodes(nodes: List[Node]): List[(ServiceAddress, NodeParameters)] =
    nodes.flatMap(collectParametersFromNode)

  private def collectParametersFromNode(node: Node): List[(ServiceAddress, NodeParameters)] =
    val nodeParams =
      if node.parameters.nonEmpty then List((node.address, node.parameters))
      else Nil

    val childParams = node match
      case s: Node.Standard =>
        collectParametersFromNodes(
          s.onSuccess.toList ++ s.onFailure.toList ++ s.onConditions.map(_.to)
        )
      case f: Node.Fork =>
        collectParametersFromNodes(f.branches.values.toList)
      case j: Node.Join =>
        collectParametersFromNodes(
          j.onSuccess.toList ++ j.onFailure.toList ++ j.onTimeout.toList
        )

    nodeParams ++ childParams

  private def collectAddressesFromNodes(nodes: List[Node]): Set[ServiceAddress] =
    nodes.flatMap(collectAddressesFromNode).toSet

  private def collectAddressesFromNode(node: Node): List[ServiceAddress] =
    val nodeAddress = List(node.address)
    val childAddresses = node match
      case s: Node.Standard =>
        collectAddressesFromNodes(
          s.onSuccess.toList ++ s.onFailure.toList ++ s.onConditions.map(_.to)
        ).toList
      case f: Node.Fork =>
        collectAddressesFromNodes(f.branches.values.toList).toList
      case j: Node.Join =>
        collectAddressesFromNodes(
          j.onSuccess.toList ++ j.onFailure.toList ++ j.onTimeout.toList
        ).toList

    nodeAddress ++ childAddresses
