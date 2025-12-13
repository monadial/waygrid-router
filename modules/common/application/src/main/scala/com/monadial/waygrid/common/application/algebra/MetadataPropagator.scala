package com.monadial.waygrid.common.application.algebra

trait MetadataPropagator[F[_]]:
  def savepoint[A](value: A): F[Unit]


