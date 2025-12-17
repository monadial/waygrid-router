package com.monadial.waygrid.common.application.interpreter.storage

import cats.effect.kernel.{ Async, Ref }
import cats.implicits.*
import com.monadial.waygrid.common.domain.algebra.storage.DagRepository
import com.monadial.waygrid.common.domain.model.traversal.dag.Dag
import com.monadial.waygrid.common.domain.model.traversal.dag.Value.DagHash

final class InMemoryDagRepository[F[_]: Async] private (
  dags: Ref[F, Map[DagHash, Dag]]
) extends DagRepository[F]:

  override def save(dag: Dag): F[Unit] =
    dags.update(_.updated(dag.hash, dag))

  override def load(hash: DagHash): F[Option[Dag]] =
    dags.get.map(_.get(hash))

object InMemoryDagRepository:
  def make[F[_]: Async]: F[DagRepository[F]] =
    Ref.of[F, Map[DagHash, Dag]](Map.empty).map(new InMemoryDagRepository[F](_))
