package com.monadial.waygrid.common.application.util.scodec.codecs

import cats.Show
import cats.data.NonEmptyList
import com.monadial.waygrid.common.application.domain.model.envelope.TransportEnvelope
import com.monadial.waygrid.common.application.domain.model.envelope.Value.{ MessageContent, MessageContentData, MessageContentType }
import com.monadial.waygrid.common.domain.model.envelope.Value.EnvelopeId
import com.monadial.waygrid.common.application.util.scodec.codecs.ApplicationTransportEnvelopeScodecCodecs.given
import com.monadial.waygrid.common.application.util.scodec.codecs.DomainAddressScodecCodecs.given
import com.monadial.waygrid.common.application.util.scodec.codecs.DomainRoutingDagScodecCodecs.given
import com.monadial.waygrid.common.application.util.scodec.codecs.DomainRoutingScodecCodecs.given
import com.monadial.waygrid.common.application.util.scodec.codecs.DomainStampScodecCodecs.given
import com.monadial.waygrid.common.application.util.scodec.codecs.DomainVectorClockScodecCodecs.given
import com.monadial.waygrid.common.domain.model.envelope.EnvelopeStamps
import com.monadial.waygrid.common.domain.model.envelope.Value.{ Stamp, TraversalRefStamp, TraversalStamp }
import com.monadial.waygrid.common.domain.model.node.Value.{ NodeComponent, NodeDescriptor, NodeId as ModelNodeId, NodeService }
import com.monadial.waygrid.common.domain.model.resiliency.RetryPolicy
import com.monadial.waygrid.common.domain.model.routing.Value.{ DeliveryStrategy, RepeatPolicy, TraversalId }
import com.monadial.waygrid.common.domain.model.traversal.condition.Condition
import com.monadial.waygrid.common.domain.model.traversal.dag.{ Dag, Edge, JoinStrategy, Node, NodeType }
import com.monadial.waygrid.common.domain.model.traversal.dag.Value.{ DagHash, EdgeGuard, ForkId, NodeId }
import com.monadial.waygrid.common.domain.model.traversal.state.{ BranchResult, BranchStatus, TraversalState }
import com.monadial.waygrid.common.domain.model.traversal.state.Event.*
import com.monadial.waygrid.common.domain.model.traversal.state.Value.RemainingNodes
import com.monadial.waygrid.common.domain.model.vectorclock.VectorClock
import com.monadial.waygrid.common.domain.value.Address.{ Endpoint, EndpointDirection, LogicalEndpoint, NodeAddress, PhysicalEndpoint, ServiceAddress }
import org.scalacheck.Gen
import scodec.{ Attempt, Codec }
import scodec.bits.ByteVector
import weaver.SimpleIOSuite
import weaver.scalacheck.Checkers
import wvlet.airframe.ulid.ULID

import java.time.Instant
import scala.collection.immutable.SortedMap
import scala.concurrent.duration.*

/**
 * Property-based tests for scodec TransportEnvelope and related codecs.
 * Tests roundtrip encoding/decoding for all types.
 */
object TransportEnvelopeScodecSuite extends SimpleIOSuite with Checkers:

  // ---------------------------------------------------------------------------
  // Show instances for ScalaCheck
  // ---------------------------------------------------------------------------

  given Show[Int]                = Show.fromToString
  given Show[Long]               = Show.fromToString
  given Show[String]             = Show.fromToString
  given Show[RetryPolicy]        = Show.fromToString
  given Show[RepeatPolicy]       = Show.fromToString
  given Show[DeliveryStrategy]   = Show.fromToString
  given Show[EdgeGuard]          = Show.fromToString
  given Show[JoinStrategy]       = Show.fromToString
  given Show[NodeType]           = Show.fromToString
  given Show[Condition]          = Show.fromToString
  given Show[Node]               = Show.fromToString
  given Show[Edge]               = Show.fromToString
  given Show[Dag]                = Show.fromToString
  given Show[VectorClock]        = Show.fromToString
  given Show[Endpoint]           = Show.fromToString
  given Show[Stamp]              = Show.fromToString
  given Show[MessageContent]     = Show.fromToString
  given Show[TransportEnvelope]  = Show.fromToString
  given Show[TraversalState]     = Show.fromToString
  given Show[StateEvent]         = Show.fromToString
  given Show[BranchStatus]       = Show.fromToString
  given Show[BranchResult]       = Show.fromToString
  given Show[EnvelopeStamps]     = Show.fromToString

  // ---------------------------------------------------------------------------
  // Generators
  // ---------------------------------------------------------------------------

  private val genULID: Gen[ULID] = Gen.delay(Gen.const(ULID.newULID))

  private val genNodeId: Gen[NodeId] =
    Gen.alphaNumStr.map(s => NodeId(s.take(20)))

  private val genDagHash: Gen[DagHash] =
    Gen.alphaNumStr.map(s => DagHash(s"hash-${s.take(20)}"))

  private val genForkId: Gen[ForkId] =
    Gen.alphaNumStr.map(s => ForkId.unsafeFrom(s.take(10)))

  private val genTraversalId: Gen[TraversalId] =
    genULID.map(TraversalId(_))

  private val genModelNodeId: Gen[ModelNodeId] =
    genULID.map(ModelNodeId(_))

  private val genEnvelopeId: Gen[EnvelopeId] =
    genULID.map(EnvelopeId(_))

  private val genFiniteDuration: Gen[FiniteDuration] =
    Gen.choose(1L, 3600000L).map(_.millis)

  private val genInstant: Gen[Instant] =
    Gen.choose(0L, System.currentTimeMillis() * 2).map(Instant.ofEpochMilli)

  private val genNodeComponent: Gen[NodeComponent] =
    Gen.oneOf(NodeComponent.Origin, NodeComponent.Processor, NodeComponent.Destination, NodeComponent.System)

  private val genNodeService: Gen[NodeService] =
    Gen.alphaLowerStr.map(s => NodeService(s.take(10).nonEmpty match { case true => s.take(10); case false => "svc" }))

  private val genNodeDescriptor: Gen[NodeDescriptor] =
    for
      component <- genNodeComponent
      service   <- genNodeService
    yield NodeDescriptor(component, service)

  private val genServiceAddress: Gen[ServiceAddress] =
    genNodeDescriptor.map(ServiceAddress(_))

  private val genNodeAddress: Gen[NodeAddress] =
    for
      descriptor <- genNodeDescriptor
      nodeId     <- genModelNodeId
    yield NodeAddress(descriptor, nodeId)

  private val genEndpointDirection: Gen[EndpointDirection] =
    Gen.oneOf(EndpointDirection.Inbound, EndpointDirection.Outbound)

  private val genLogicalEndpoint: Gen[LogicalEndpoint] =
    for
      descriptor <- genNodeDescriptor
      direction  <- genEndpointDirection
    yield LogicalEndpoint(descriptor, direction)

  private val genPhysicalEndpoint: Gen[PhysicalEndpoint] =
    for
      descriptor <- genNodeDescriptor
      nodeId     <- genModelNodeId
      direction  <- genEndpointDirection
    yield PhysicalEndpoint(descriptor, nodeId, direction)

  private val genEndpoint: Gen[Endpoint] =
    Gen.oneOf(genLogicalEndpoint, genPhysicalEndpoint)

  private val genRetryPolicy: Gen[RetryPolicy] =
    Gen.oneOf(
      Gen.const(RetryPolicy.None),
      for
        base       <- genFiniteDuration
        maxRetries <- Gen.choose(1, 10)
      yield RetryPolicy.Linear(base, maxRetries),
      for
        base       <- genFiniteDuration
        maxRetries <- Gen.choose(1, 10)
      yield RetryPolicy.Exponential(base, maxRetries),
      for
        base       <- genFiniteDuration
        cap        <- genFiniteDuration
        maxRetries <- Gen.choose(1, 10)
      yield RetryPolicy.BoundedExponential(base, cap, maxRetries),
      for
        base       <- genFiniteDuration
        maxRetries <- Gen.choose(1, 5)
      yield RetryPolicy.Fibonacci(base, maxRetries)
    )

  private val genRepeatPolicy: Gen[RepeatPolicy] =
    Gen.oneOf(
      Gen.const(RepeatPolicy.NoRepeat),
      genFiniteDuration.map(RepeatPolicy.Indefinitely(_))
    )

  private val genDeliveryStrategy: Gen[DeliveryStrategy] =
    Gen.oneOf(
      Gen.const(DeliveryStrategy.Immediate),
      genFiniteDuration.map(DeliveryStrategy.ScheduleAfter(_)),
      genInstant.map(DeliveryStrategy.ScheduleAt(_))
    )

  private val genCondition: Gen[Condition] =
    Gen.oneOf(
      Gen.const(Condition.Always),
      Gen.const(Condition.Not(Condition.Always)),
      Gen.const(Condition.And(List(Condition.Always))),
      Gen.const(Condition.Or(List(Condition.Always)))
    )

  private val genEdgeGuard: Gen[EdgeGuard] =
    Gen.oneOf(
      Gen.const(EdgeGuard.OnSuccess),
      Gen.const(EdgeGuard.OnFailure),
      Gen.const(EdgeGuard.Always),
      Gen.const(EdgeGuard.OnAny),
      Gen.const(EdgeGuard.OnTimeout),
      genCondition.map(EdgeGuard.Conditional(_))
    )

  private val genJoinStrategy: Gen[JoinStrategy] =
    Gen.oneOf(
      Gen.const(JoinStrategy.And),
      Gen.const(JoinStrategy.Or),
      Gen.choose(1, 10).map(JoinStrategy.Quorum(_))
    )

  private val genNodeType: Gen[NodeType] =
    Gen.oneOf(
      Gen.const(NodeType.Standard),
      genForkId.map(NodeType.Fork(_)),
      for
        forkId   <- genForkId
        strategy <- genJoinStrategy
        timeout  <- Gen.option(genFiniteDuration)
      yield NodeType.Join(forkId, strategy, timeout)
    )

  private val genNode: Gen[Node] =
    for
      id       <- genNodeId
      label    <- Gen.option(Gen.alphaNumStr.map(_.take(20)))
      retry    <- genRetryPolicy
      delivery <- genDeliveryStrategy
      address  <- genServiceAddress
      nodeType <- genNodeType
    yield Node(id, label, retry, delivery, address, nodeType)

  private val genEdge: Gen[Edge] =
    for
      from  <- genNodeId
      to    <- genNodeId
      guard <- genEdgeGuard
    yield Edge(from, to, guard)

  private val genVectorClock: Gen[VectorClock] =
    for
      size    <- Gen.choose(0, 3)
      entries <- Gen.listOfN(size, genNodeAddress.flatMap(addr => Gen.choose(1L, 100L).map(addr -> _)))
    yield VectorClock(SortedMap.from(entries))

  private val genSimpleDag: Gen[Dag] =
    for
      hash         <- genDagHash
      entryNodeId  <- genNodeId
      repeatPolicy <- genRepeatPolicy
      node         <- genNode.map(_.copy(id = entryNodeId))
      timeout      <- Gen.option(genFiniteDuration)
    yield Dag(
      hash = hash,
      entryPoints = NonEmptyList.one(entryNodeId),
      repeatPolicy = repeatPolicy,
      nodes = Map(entryNodeId -> node),
      edges = List.empty,
      timeout = timeout
    )

  private val genMessageContent: Gen[MessageContent] =
    for
      contentType <- Gen.alphaNumStr.map(s => MessageContentType(s"type-${s.take(10)}"))
      contentData <- Gen.listOf(Gen.choose(0, 255).map(_.toByte)).map(bytes => MessageContentData(ByteVector(bytes.toArray)))
    yield MessageContent(contentType, contentData)

  private val genTraversalStamp: Gen[TraversalStamp] =
    for
      dag   <- genSimpleDag
      state <- genSimpleTraversalState(dag)
    yield TraversalStamp(state, dag)

  private val genTraversalRefStamp: Gen[TraversalRefStamp] =
    genDagHash.map(TraversalRefStamp(_))

  private val genStamp: Gen[Stamp] =
    Gen.oneOf(genTraversalStamp, genTraversalRefStamp)

  private val genEnvelopeStamps: Gen[EnvelopeStamps] =
    Gen.listOf(genStamp).map { stamps =>
      stamps.groupBy {
        case _: TraversalStamp    => classOf[TraversalStamp]
        case _: TraversalRefStamp => classOf[TraversalRefStamp]
      }.asInstanceOf[EnvelopeStamps]
    }

  private def genSimpleTraversalState(dag: Dag): Gen[TraversalState] =
    for
      traversalId <- genTraversalId
      vectorClock <- genVectorClock
    yield TraversalState(
      traversalId = traversalId,
      active = Set.empty,
      completed = Set.empty,
      failed = Set.empty,
      retries = Map.empty,
      vectorClock = vectorClock,
      history = Vector.empty,
      remainingNodes = RemainingNodes(dag.nodes.size)
    )

  private val genTransportEnvelope: Gen[TransportEnvelope] =
    for
      id       <- genEnvelopeId
      sender   <- genNodeAddress
      endpoint <- genEndpoint
      message  <- genMessageContent
      stamps   <- genEnvelopeStamps
    yield TransportEnvelope(id, sender, endpoint, message, stamps)

  // ---------------------------------------------------------------------------
  // Helper: Roundtrip test
  // ---------------------------------------------------------------------------

  private def roundtrip[A](value: A)(using codec: Codec[A]): Boolean =
    codec.encode(value).flatMap(bits => codec.decode(bits).map(_.value)) == Attempt.successful(value)

  // ---------------------------------------------------------------------------
  // Tests
  // ---------------------------------------------------------------------------

  test("RetryPolicy roundtrip"):
    forall(genRetryPolicy)(policy => expect(roundtrip(policy)))

  test("RepeatPolicy roundtrip"):
    forall(genRepeatPolicy)(policy => expect(roundtrip(policy)))

  test("DeliveryStrategy roundtrip"):
    forall(genDeliveryStrategy)(strategy => expect(roundtrip(strategy)))

  test("EdgeGuard roundtrip"):
    forall(genEdgeGuard)(guard => expect(roundtrip(guard)))

  pureTest("EdgeGuard debug OnFailure"):
    val guard: EdgeGuard = EdgeGuard.OnFailure
    val codec            = summon[Codec[EdgeGuard]]
    codec.encode(guard) match
      case Attempt.Failure(err) =>
        failure(s"Encode failed: $err")
      case Attempt.Successful(bits) =>
        codec.decode(bits) match
          case Attempt.Failure(err) =>
            failure(s"Decode failed: $err")
          case Attempt.Successful(result) =>
            if result.value == guard && result.remainder.isEmpty then success
            else
              failure(
                s"bits=${bits.toHex}, decoded=${result.value} (expected $guard), remainder=${result.remainder.size}"
              )

  test("JoinStrategy roundtrip"):
    forall(genJoinStrategy)(strategy => expect(roundtrip(strategy)))

  test("NodeType roundtrip"):
    forall(genNodeType)(nodeType => expect(roundtrip(nodeType)))

  test("Node roundtrip"):
    forall(genNode)(node => expect(roundtrip(node)))

  test("Edge roundtrip"):
    forall(genEdge)(edge => expect(roundtrip(edge)))

  test("VectorClock roundtrip"):
    forall(genVectorClock)(vc => expect(roundtrip(vc)))

  test("Dag roundtrip"):
    forall(genSimpleDag)(dag => expect(roundtrip(dag)))

  test("Endpoint roundtrip"):
    forall(genEndpoint)(endpoint => expect(roundtrip(endpoint)))

  test("Stamp roundtrip"):
    forall(genStamp)(stamp => expect(roundtrip(stamp)))

  test("MessageContent roundtrip"):
    forall(genMessageContent)(mc => expect(roundtrip(mc)))

  pureTest("EnvelopeStamps roundtrip (empty)"):
    val emptyStamps: EnvelopeStamps = Map.empty
    expect(roundtrip[EnvelopeStamps](emptyStamps))

  test("EnvelopeStamps roundtrip"):
    forall(genEnvelopeStamps)(stamps => expect(roundtrip(stamps)))

  test("TransportEnvelope roundtrip"):
    forall(genTransportEnvelope)(envelope => expect(roundtrip(envelope)))

  // ---------------------------------------------------------------------------
  // Edge case tests
  // ---------------------------------------------------------------------------

  pureTest("TransportEnvelope with empty stamps roundtrips"):
    val envelope = TransportEnvelope(
      id = EnvelopeId(ULID.newULID),
      sender = NodeAddress(NodeDescriptor.Origin(NodeService("http")), ModelNodeId(ULID.newULID)),
      endpoint = LogicalEndpoint(NodeDescriptor.Destination(NodeService("webhook")), EndpointDirection.Outbound),
      message = MessageContent(MessageContentType("test"), MessageContentData(ByteVector.empty)),
      stamps = Map.empty
    )
    expect(roundtrip[TransportEnvelope](envelope))

  pureTest("TransportEnvelope with TraversalRefStamp roundtrips"):
    val stamp    = TraversalRefStamp(DagHash("test-hash"))
    val stamps   = Map(classOf[TraversalRefStamp] -> List(stamp)).asInstanceOf[EnvelopeStamps]
    val envelope = TransportEnvelope(
      id = EnvelopeId(ULID.newULID),
      sender = NodeAddress(NodeDescriptor.Origin(NodeService("http")), ModelNodeId(ULID.newULID)),
      endpoint = PhysicalEndpoint(NodeDescriptor.System(NodeService("topology")), ModelNodeId(ULID.newULID), EndpointDirection.Inbound),
      message = MessageContent(MessageContentType("event"), MessageContentData(ByteVector(1, 2, 3))),
      stamps = stamps
    )
    expect(roundtrip[TransportEnvelope](envelope))

  pureTest("Condition.Not roundtrips"):
    val condition = Condition.Not(Condition.Always)
    expect(roundtrip(condition))

  pureTest("Condition.And with multiple conditions roundtrips"):
    val condition = Condition.And(List(Condition.Always, Condition.Not(Condition.Always)))
    expect(roundtrip(condition))

  pureTest("Nested Condition roundtrips"):
    val condition = Condition.Or(List(
      Condition.And(List(Condition.Always)),
      Condition.Not(Condition.Or(List(Condition.Always)))
    ))
    expect(roundtrip(condition))
