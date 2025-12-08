package com.monadial.waygrid.system.topology.interpreter

import cats.effect.{ Async, Resource }
import com.monadial.waygrid.common.application.algebra.Logger
import com.monadial.waygrid.common.application.domain.model.settings.RedisSettings
import com.monadial.waygrid.common.application.syntax.BackoffSyntax.retryOnFailure
import com.monadial.waygrid.common.application.util.redis4cats.Redis
import com.monadial.waygrid.common.domain.model.node.Value.NodeId
import com.monadial.waygrid.common.domain.model.resiliency.RetryPolicy.Linear
import com.monadial.waygrid.system.topology.algebra.{ LeadershipProvider, NodeLost, NodeWon }
import com.monadial.waygrid.system.topology.domain.model.election.Value.FencingToken
import dev.profunktor.redis4cats.data.RedisCodec

import scala.concurrent.duration.*

object RedisTopologyLeaderElection:

  def behavior[F[+_]: {Async, Logger}](settings: RedisSettings): Resource[F, LeadershipProvider[F]] =
    inline given RedisCodec[String, String] = RedisCodec.Utf8
    for
      redis <- Redis
        .connection(settings)
        .retryOnFailure(Linear(1.seconds, 6))
    yield new LeadershipProvider[F]:
      override def acquire(nodeId: NodeId, lease: Duration): F[Either[NodeLost, NodeWon]] = ???
      override def renew(nodeId: NodeId, token: FencingToken): F[Unit]                    = ???

      override def release(nodeId: NodeId, token: FencingToken): F[Unit] = ???
