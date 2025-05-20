package com.monadial.waygrid.common.application.interpreter

import cats.*
import cats.data.*
import cats.effect.*
import cats.effect.std.Env
import cats.syntax.all.*
import com.monadial.waygrid.common.application.algebra.HasNode
import com.monadial.waygrid.common.domain.model.node.{ Node, NodeClusterId, NodeDescriptor, NodeRuntime }
import com.monadial.waygrid.common.domain.syntax.StringSyntax.toDomain

object EnvHasNodeInterpreter:
  def resource[F[+_]: {Async, Env}](descriptor: NodeDescriptor): Resource[F, HasNode[F]] =
    Resource
      .eval:
        for
          startedAt <- Clock[F].realTimeInstant
          clusterId <- OptionT(Env[F].get("WAYGRID_CLUSTER_ID"))
            .map(_.toDomain[NodeClusterId])
            .getOrRaise(new RuntimeException("Missing WAYGRID_CLUSTER_ID env parameter."))
        yield new HasNode[F]:
          override def get: F[Node] = Node(
            descriptor,
            clusterId,
            startedAt,
            NodeRuntime()
          ).pure[F]
