package com.monadial.waygrid.common.domain.model.traversal.spec

import cats.data.NonEmptyList
import com.monadial.waygrid.common.domain.model.routing.Value.RepeatPolicy

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
)

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
