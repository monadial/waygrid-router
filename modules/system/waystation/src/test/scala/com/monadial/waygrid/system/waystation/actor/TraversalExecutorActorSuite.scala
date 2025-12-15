package com.monadial.waygrid.system.waystation.actor

import cats.effect.{ IO, Ref }
import cats.implicits.*
import cats.data.NonEmptyList
import com.monadial.waygrid.common.application.algebra.{ EventSink, Logger, ThisNode }
import com.monadial.waygrid.common.application.algebra.LoggerContext
import com.monadial.waygrid.common.application.interpreter.storage.InMemoryDagRepository
import com.monadial.waygrid.common.application.interpreter.storage.InMemoryTraversalStateRepository
import com.monadial.waygrid.common.domain.algebra.messaging.message.Value.MessageId
import com.monadial.waygrid.common.domain.algebra.storage.DagRepository
import com.monadial.waygrid.common.domain.algebra.storage.TraversalStateRepository
import com.monadial.waygrid.common.domain.model.node.{ Node as SystemNode }
import com.monadial.waygrid.common.domain.model.node.Value.{ NodeClusterId, NodeDescriptor, NodeId as SystemNodeId, NodeRegion, NodeRuntime }
import com.monadial.waygrid.common.domain.model.resiliency.RetryPolicy
import com.monadial.waygrid.common.domain.model.routing.Value.{ DeliveryStrategy, RepeatPolicy, TraversalId }
import com.monadial.waygrid.common.domain.model.traversal.Event.{ NodeTraversalRequested, NodeTraversalSucceeded, TraversalRequested }
import com.monadial.waygrid.common.domain.model.traversal.dag.Value.{ DagHash, EdgeGuard, ForkId, NodeId }
import com.monadial.waygrid.common.domain.model.traversal.dag.{ Dag, Edge, JoinStrategy, Node, NodeType }
import com.monadial.waygrid.common.domain.model.envelope.Value.TraversalRefStamp
import com.monadial.waygrid.common.domain.value.Address.ServiceAddress
import org.http4s.Uri
import org.typelevel.otel4s.trace.SpanContext
import weaver.SimpleIOSuite

import java.time.Instant

object TraversalExecutorActorSuite extends SimpleIOSuite:

  given Logger[IO] with
    def info(msg: => String)(using LoggerContext): IO[Unit]                                = IO.unit
    def info(msg: => String, ctx: Map[String, String])(using LoggerContext): IO[Unit]     = IO.unit
    def info(msg: => String, t: Throwable)(using LoggerContext): IO[Unit]                 = IO.unit
    def info(msg: => String, ctx: Map[String, String], t: Throwable)(using LoggerContext) = IO.unit
    def error(msg: => String)(using LoggerContext): IO[Unit]                               = IO.unit
    def error(msg: => String, ctx: Map[String, String])(using LoggerContext): IO[Unit]    = IO.unit
    def error(msg: => String, t: Throwable)(using LoggerContext): IO[Unit]                = IO.unit
    def error(msg: => String, ctx: Map[String, String], t: Throwable)(using LoggerContext) = IO.unit
    def debug(msg: => String)(using LoggerContext): IO[Unit]                               = IO.unit
    def debug(msg: => String, ctx: Map[String, String])(using LoggerContext): IO[Unit]    = IO.unit
    def debug(msg: => String, t: Throwable)(using LoggerContext): IO[Unit]                = IO.unit
    def debug(msg: => String, ctx: Map[String, String], t: Throwable)(using LoggerContext) = IO.unit
    def warn(msg: => String)(using LoggerContext): IO[Unit]                                = IO.unit
    def warn(msg: => String, ctx: Map[String, String])(using LoggerContext): IO[Unit]     = IO.unit
    def warn(msg: => String, t: Throwable)(using LoggerContext): IO[Unit]                 = IO.unit
    def warn(msg: => String, ctx: Map[String, String], t: Throwable)(using LoggerContext) = IO.unit
    def trace(msg: => String)(using LoggerContext): IO[Unit]                               = IO.unit
    def trace(msg: => String, ctx: Map[String, String])(using LoggerContext): IO[Unit]    = IO.unit
    def trace(msg: => String, t: Throwable)(using LoggerContext): IO[Unit]                = IO.unit
    def trace(msg: => String, ctx: Map[String, String], t: Throwable)(using LoggerContext) = IO.unit

  private val svcA  = ServiceAddress(Uri.unsafeFromString("waygrid://processor/a"))
  private val nodeA = NodeId("A")

  private val dag = Dag(
    hash = DagHash("executor-dag"),
    entryPoints = NonEmptyList.one(nodeA),
    repeatPolicy = RepeatPolicy.NoRepeat,
    nodes = Map(nodeA -> Node(nodeA, None, RetryPolicy.None, DeliveryStrategy.Immediate, svcA)),
    edges = Nil
  )

  private val forkId: ForkId =
    ForkId.fromStringUnsafe[cats.Id]("01ARZ3NDEKTSV4RRFFQ69G5FAV")

  private val forkDag = Dag(
    hash = DagHash("executor-fork-dag"),
    entryPoints = NonEmptyList.one(nodeA),
    repeatPolicy = RepeatPolicy.NoRepeat,
    nodes = Map(
      nodeA -> Node(nodeA, None, RetryPolicy.None, DeliveryStrategy.Immediate, svcA),
      NodeId("F") -> Node(NodeId("F"), None, RetryPolicy.None, DeliveryStrategy.Immediate, svcA, NodeType.Fork(forkId)),
      NodeId("J") -> Node(NodeId("J"), None, RetryPolicy.None, DeliveryStrategy.Immediate, svcA, NodeType.Join(forkId, JoinStrategy.And, None)),
      NodeId("B") -> Node(NodeId("B"), None, RetryPolicy.None, DeliveryStrategy.Immediate, svcA),
      NodeId("C") -> Node(NodeId("C"), None, RetryPolicy.None, DeliveryStrategy.Immediate, svcA)
    ),
    edges = List(
      Edge(nodeA, NodeId("F"), EdgeGuard.OnSuccess),
      Edge(NodeId("F"), NodeId("B"), EdgeGuard.OnSuccess),
      Edge(NodeId("F"), NodeId("C"), EdgeGuard.OnSuccess),
      Edge(NodeId("B"), NodeId("J"), EdgeGuard.OnSuccess),
      Edge(NodeId("C"), NodeId("J"), EdgeGuard.OnSuccess)
    )
  )

  private def mkThisNode: SystemNode =
    val id = SystemNodeId.fromStringUnsafe[cats.Id]("01ARZ3NDEKTSV4RRFFQ69G5FAV")
    SystemNode(
      id = id,
      descriptor = NodeDescriptor.System(com.monadial.waygrid.common.domain.model.node.Value.NodeService("waystation")),
      clusterId = NodeClusterId("local"),
      region = NodeRegion.unsafeFrom("us-east-1"),
      startedAt = Instant.now(),
      runtime = NodeRuntime()
    )

  test("TraversalExecutorActor does not persist state for linear DAG and emits NodeTraversalRequested on Begin"):
    for
      sent <- Ref.of[IO, List[(NodeTraversalRequested, com.monadial.waygrid.common.domain.model.envelope.EnvelopeStamps)]](Nil)
      repo <- InMemoryTraversalStateRepository.make[IO]
      dagRepo <- InMemoryDagRepository.make[IO]
      traversalId <- TraversalId.next[IO]
      messageId   <- MessageId.next[IO]
      _ <- locally {
        given TraversalStateRepository[IO] = repo
        given DagRepository[IO]           = dagRepo
        given ThisNode[IO] with
          def get: IO[SystemNode] = IO.pure(mkThisNode)
        given EventSink[IO] =
          new EventSink[IO]:
            def send(
              endpoint: com.monadial.waygrid.common.domain.value.Address.Endpoint,
              event: com.monadial.waygrid.common.domain.model.envelope.DomainEnvelope[? <: com.monadial.waygrid.common.domain.algebra.messaging.event.Event],
              ctx: Option[SpanContext]
            ): IO[Unit] =
              event.message match
                case m: NodeTraversalRequested => sent.update(_ :+ (m -> event.stamps))
                case _                        => IO.unit

            def sendBatch(
              events: List[
                (
                  com.monadial.waygrid.common.domain.value.Address.Endpoint,
                  com.monadial.waygrid.common.domain.model.envelope.DomainEnvelope[? <: com.monadial.waygrid.common.domain.algebra.messaging.event.Event],
                  Option[SpanContext]
                )
              ]
            ): IO[Unit] =
              events.traverse_ { case (_, env, _) =>
                env.message match
                  case m: NodeTraversalRequested => sent.update(_ :+ (m -> env.stamps))
                  case _                        => IO.unit
              }

        for
          _ <- dagRepo.save(dag)
          _ <- TraversalExecutorActor.behavior[IO].use { actor =>
              actor.receive.apply(
                ExecuteTraversal(
                  traversalId = traversalId,
                  dagHash = dag.hash,
                  event = TraversalRequested(messageId, traversalId),
                  spanCtx = SpanContext.invalid
                )
              )
            }
        yield ()
      }

      saved <- repo.load(traversalId)
      msgs  <- sent.get
    yield
      // Linear DAG should not require persistent state storage.
      expect(saved.isEmpty) &&
        expect(msgs.exists(_._1.nodeId == nodeA)) &&
        expect(msgs.exists { case (_, stamps) => stamps.contains(classOf[TraversalRefStamp]) })

  test("TraversalExecutorActor persists state for non-linear DAG (fork/join)"):
    for
      repo <- InMemoryTraversalStateRepository.make[IO]
      dagRepo <- InMemoryDagRepository.make[IO]
      traversalId <- TraversalId.next[IO]
      messageId   <- MessageId.next[IO]
      _ <- locally {
        given TraversalStateRepository[IO] = repo
        given DagRepository[IO]           = dagRepo
        given ThisNode[IO] with
          def get: IO[SystemNode] = IO.pure(mkThisNode)
        given EventSink[IO] =
          new EventSink[IO]:
            def send(
              endpoint: com.monadial.waygrid.common.domain.value.Address.Endpoint,
              event: com.monadial.waygrid.common.domain.model.envelope.DomainEnvelope[? <: com.monadial.waygrid.common.domain.algebra.messaging.event.Event],
              ctx: Option[SpanContext]
            ): IO[Unit] = IO.unit

            def sendBatch(
              events: List[
                (
                  com.monadial.waygrid.common.domain.value.Address.Endpoint,
                  com.monadial.waygrid.common.domain.model.envelope.DomainEnvelope[? <: com.monadial.waygrid.common.domain.algebra.messaging.event.Event],
                  Option[SpanContext]
                )
              ]
            ): IO[Unit] = IO.unit

        for
          _ <- dagRepo.save(forkDag)
          _ <- TraversalExecutorActor.behavior[IO].use { actor =>
              actor.receive.apply(
                ExecuteTraversal(
                  traversalId = traversalId,
                  dagHash = forkDag.hash,
                  event = TraversalRequested(messageId, traversalId),
                  spanCtx = SpanContext.invalid
                )
              )
            }
        yield ()
      }

      saved <- repo.load(traversalId)
    yield
      expect(saved.nonEmpty)

  test("TraversalExecutorActor restores non-linear state from repository across events"):
    for
      sent <- Ref.of[IO, List[NodeTraversalRequested]](Nil)
      repo <- InMemoryTraversalStateRepository.make[IO]
      dagRepo <- InMemoryDagRepository.make[IO]
      traversalId <- TraversalId.next[IO]
      messageId1  <- MessageId.next[IO]
      messageId2  <- MessageId.next[IO]
      _ <- locally {
        given TraversalStateRepository[IO] = repo
        given DagRepository[IO]           = dagRepo
        given ThisNode[IO] with
          def get: IO[SystemNode] = IO.pure(mkThisNode)
        given EventSink[IO] =
          new EventSink[IO]:
            def send(
              endpoint: com.monadial.waygrid.common.domain.value.Address.Endpoint,
              event: com.monadial.waygrid.common.domain.model.envelope.DomainEnvelope[? <: com.monadial.waygrid.common.domain.algebra.messaging.event.Event],
              ctx: Option[SpanContext]
            ): IO[Unit] =
              event.message match
                case m: NodeTraversalRequested => sent.update(_ :+ m)
                case _                        => IO.unit

            def sendBatch(
              events: List[
                (
                  com.monadial.waygrid.common.domain.value.Address.Endpoint,
                  com.monadial.waygrid.common.domain.model.envelope.DomainEnvelope[? <: com.monadial.waygrid.common.domain.algebra.messaging.event.Event],
                  Option[SpanContext]
                )
              ]
            ): IO[Unit] = IO.unit

        for
          _ <- dagRepo.save(forkDag)
          _ <- TraversalExecutorActor.behavior[IO].use { actor =>
              for
                _ <- actor.receive.apply(
                    ExecuteTraversal(
                      traversalId = traversalId,
                      dagHash = forkDag.hash,
                      event = TraversalRequested(messageId1, traversalId),
                      spanCtx = SpanContext.invalid
                    )
                  )
                _ <- actor.receive.apply(
                    ExecuteTraversal(
                      traversalId = traversalId,
                      dagHash = forkDag.hash,
                      event = NodeTraversalSucceeded(messageId2, traversalId, nodeA),
                      spanCtx = SpanContext.invalid
                    )
                  )
              yield ()
            }
        yield ()
      }

      saved <- repo.load(traversalId)
      msgs  <- sent.get
    yield
      expect(saved.exists(_.stateVersion.unwrap >= 2)) &&
        expect(msgs.map(_.nodeId).contains(nodeA)) &&
        expect(msgs.map(_.nodeId).contains(NodeId("F")))
