package com.monadial.waygrid.common.application.interpreter

import com.monadial.waygrid.common.application.algebra.ThisNode
import com.monadial.waygrid.common.domain.model.node.Value.{
  NodeClusterId,
  NodeDescriptor,
  NodeId,
  NodeRegion,
  NodeRuntime
}
import com.monadial.waygrid.common.domain.syntax.StringSyntax.toDomain
import cats.*
import cats.data.*
import cats.effect.*
import cats.effect.std.Env
import cats.syntax.all.*
import com.monadial.waygrid.common.domain.model.node.Node

object EnvHasNodeInterpreter:
  def resource[F[+_]: {Async, Env}](descriptor: NodeDescriptor): Resource[F, ThisNode[F]] =
    Resource
      .eval:
        for
          startedAt <- Clock[F].realTimeInstant
          clusterId <- OptionT(Env[F].get("WAYGRID_CLUSTER_ID"))
            .map(_.toDomain[NodeClusterId])
            .getOrRaise(new RuntimeException("Missing WAYGRID_CLUSTER_ID env parameter."))
          region <- OptionT(Env[F].get("WAYGRID_REGION"))
            .toRight(new RuntimeException("Missing WAYGRID_REGION env parameter."))
            .subflatMap: raw =>
              NodeRegion.from(raw.trim.toLowerCase)
                .leftMap(err => new RuntimeException(s"Invalid WAYGRID_REGION '$raw': $err"))
            .rethrowT
          nodeId <- NodeId.next[F]
        yield new ThisNode[F]:
          override def get: F[Node] = Node(
            nodeId,
            descriptor,
            clusterId,
            region,
            startedAt,
            NodeRuntime()
          ).pure[F]
