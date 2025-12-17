package com.monadial.waygrid.system.topology.actor

import cats.Parallel
import cats.effect.{ Async, Concurrent, Ref, Resource }
import cats.syntax.all.*
import com.monadial.waygrid.common.application.algebra.*
import com.monadial.waygrid.common.application.util.cats.effect.{ FiberT, FiberType }
import com.monadial.waygrid.common.domain.algebra.value.long.LongValue
import com.monadial.waygrid.common.domain.value.Address.NodeAddress
import com.monadial.waygrid.system.topology.domain.model.election.Value.ElectionRole.Candidate
import com.monadial.waygrid.system.topology.domain.model.election.Value.{ ElectionRole, Leader }
import com.monadial.waygrid.system.topology.interpreter.RedisTopologyLeaderElection
import com.monadial.waygrid.system.topology.settings.TopologyServiceSettings
import com.suprnation.actor.Actor.ReplyingReceive

type TopologyServerLeaderActor[F[+_]]    = SupervisedActor[F, TopologyServerLeaderMessage]
type TopologyServerLeaderActorRef[F[+_]] = SupervisedActorRef[F, TopologyServerLeaderMessage]

sealed trait TopologyServerLeaderMessage
case object StartElection extends TopologyServerLeaderMessage

//final case class StartElection[F[+_]](
//  replyTo: TopologyServerActorRef[F]
//) extends TopologyServerLeaderMessage
//
//final case class ElectionWon[F[+_]](
//  fencingToken: FencingToken,
//  replyTo: TopologyServerActorRef[F]
//) extends TopologyServerLeaderMessage
//
//final case class ElectionLost[F[+_]](
//  leader: Leader,
//  replyTo: TopologyServerActorRef[F]
//) extends TopologyServerLeaderMessage

type ElectionEpoch = ElectionEpoch.Type
object ElectionEpoch extends LongValue:
  def first: ElectionEpoch = ElectionEpoch(1L)

  extension (epoch: ElectionEpoch) def next: ElectionEpoch = ElectionEpoch(epoch.unwrap + 1L)

final case class TopologyElectionState(
  epoch: ElectionEpoch,
  leader: Option[Leader],
  followers: Set[NodeAddress],
  role: ElectionRole
):
  def isLeaderElected: Boolean = leader.isDefined

object TopologyElectionState:
  def initial: TopologyElectionState = TopologyElectionState(
    ElectionEpoch.first,
    None,
    Set.empty,
    Candidate // every node is a candidate at the beginning
  )

trait PeriodTimer extends FiberType

object TopologyElectionActor:

  def behavior[F[+_]: {Async, Concurrent, Parallel, ThisNode, Logger}](
    settings: TopologyServiceSettings
  ): Resource[F, TopologyServerLeaderActor[F]] =
    for
      leaderState     <- Resource.eval(Ref.of[F, TopologyElectionState](TopologyElectionState.initial))
      reelectionTimer <- Resource.eval(Ref.of[F, Option[FiberT[F, PeriodTimer, Unit]]](None))
      provider        <- RedisTopologyLeaderElection.behavior(settings.redis)
      thisNode        <- Resource.eval(ThisNode[F].get)
    yield new TopologyServerLeaderActor[F]:
      override def receive: ReplyingReceive[F, TopologyServerLeaderMessage | SupervisedRequest, Any] =
        case StartElection => onStartElection

      def onStartElection: F[Unit] =
        for
          thisNode <- ThisNode[F].get
          _        <- Logger[F].info(s"Node: ${thisNode.id.show} starting attempt to become leader of topology cluster...")
          result   <- provider.acquire(thisNode.id, settings.leadership.lease)
        yield ()

//        case ElectionWon(fencingToken, lease, replyTo) => ???
//        case ElectionLost(electedLeader, replyTo)      => ???
//        case AppendOnElection(lease, replyTo)          => ???
