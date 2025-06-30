package com.monadial.waygrid.common.application.interpreter

import com.monadial.waygrid.common.application.algebra.HasNode
import com.monadial.waygrid.common.domain.model.node.Value.{ NodeClusterId, NodeDescriptor, NodeId, NodeRuntime }
import com.monadial.waygrid.common.domain.syntax.StringSyntax.toDomain

import cats.*
import cats.data.*
import cats.effect.*
import cats.effect.std.Env
import cats.syntax.all.*
import com.monadial.waygrid.common.domain.model.node.Node

object EnvHasNodeInterpreter:
  def resource[F[+_]: {Async, Env}](descriptor: NodeDescriptor): Resource[F, HasNode[F]] =
    Resource
      .eval:
        for
          startedAt <- Clock[F].realTimeInstant
          clusterId <- OptionT(Env[F].get("WAYGRID_CLUSTER_ID"))
            .map(_.toDomain[NodeClusterId])
            .getOrRaise(new RuntimeException("Missing WAYGRID_CLUSTER_ID env parameter."))
          nodeId <- NodeId.next[F]
        yield new HasNode[F]:
          override def get: F[Node] = Node(
            descriptor,
            clusterId,
            nodeId,
            startedAt,
            NodeRuntime()
          ).pure[F]
