package com.monadial.waygrid.common.domain.algebra.storage

import com.monadial.waygrid.common.domain.model.traversal.dag.Dag
import com.monadial.waygrid.common.domain.model.traversal.dag.Value.DagHash

/**
 * Repository algebra for storing compiled DAGs by hash.
 *
 * DAGs are immutable and can be safely shared across traversals.
 */
trait DagRepository[F[_]]:
  def save(dag: Dag): F[Unit]
  def load(hash: DagHash): F[Option[Dag]]

object DagRepository:
  def apply[F[_]](using repo: DagRepository[F]): DagRepository[F] = repo

