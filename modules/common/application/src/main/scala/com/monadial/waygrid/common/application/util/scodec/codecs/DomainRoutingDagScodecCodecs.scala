package com.monadial.waygrid.common.application.util.scodec.codecs

import cats.data.NonEmptyList
import com.monadial.waygrid.common.application.instances.DurationInstances.given
import com.monadial.waygrid.common.application.util.scodec.codecs.DomainRoutingScodecCodecs.given
import com.monadial.waygrid.common.domain.model.resiliency.RetryPolicy
import com.monadial.waygrid.common.domain.model.routing.Value.{ DeliveryStrategy, RepeatPolicy }
import com.monadial.waygrid.common.domain.model.traversal.condition.Condition
import com.monadial.waygrid.common.domain.model.traversal.dag.{ Dag, Edge, JoinStrategy, Node, NodeType }
import com.monadial.waygrid.common.domain.model.traversal.dag.Value.{ BranchId, DagHash, EdgeGuard, ForkId, NodeId }
import com.monadial.waygrid.common.domain.value.Address.ServiceAddress
import org.http4s.Uri
import scodec.*
import scodec.bits.*
import scodec.codecs.*
import wvlet.airframe.ulid.ULID

import scala.concurrent.duration.FiniteDuration

/**
 * Scodec binary codecs for DAG-related domain types.
 *
 * Type discriminators:
 * - EdgeGuard: 0x00=OnSuccess, 0x01=OnFailure, 0x02=Always, 0x03=OnAny, 0x04=OnTimeout, 0x05=Conditional
 * - Condition: 0x00=Always, 0x01=Not, 0x02=And, 0x03=Or
 * - JoinStrategy: 0x00=And, 0x01=Or, 0x02=Quorum
 * - NodeType: 0x00=Standard, 0x01=Fork, 0x02=Join
 */
object DomainRoutingDagScodecCodecs:

  // ---------------------------------------------------------------------------
  // Value type codecs (private to avoid conflicts with auto-derived codecs)
  // ---------------------------------------------------------------------------

  // Using private vals to avoid implicit conflicts with Value-derived codecs
  private val nodeIdCodec: Codec[NodeId] =
    variableSizeBytes(int32, utf8).xmap(NodeId(_), _.unwrap)

  private val dagHashCodec: Codec[DagHash] =
    variableSizeBytes(int32, utf8).xmap(DagHash(_), _.unwrap)

  private val forkIdCodec: Codec[ForkId] =
    variableSizeBytes(int32, utf8).xmap(ForkId.unsafeFrom, _.unwrap)

  private val branchIdCodec: Codec[BranchId] =
    bytes(16).xmap(
      bytes => BranchId(ULID.fromBytes(bytes.toArray)),
      branchId => ByteVector(branchId.unwrap.toBytes)
    )

  private val serviceAddressCodec: Codec[ServiceAddress] =
    variableSizeBytes(int32, utf8).xmap(
      str => ServiceAddress(Uri.unsafeFromString(str)),
      addr => addr.unwrap.renderString
    )

  // Expose as givens for external use
  given Codec[NodeId]         = nodeIdCodec
  given Codec[DagHash]        = dagHashCodec
  given Codec[ForkId]         = forkIdCodec
  given Codec[BranchId]       = branchIdCodec
  given Codec[ServiceAddress] = serviceAddressCodec

  // ---------------------------------------------------------------------------
  // Condition codec (recursive enum)
  // ---------------------------------------------------------------------------

  given conditionCodec: Codec[Condition] = lazily {
    Codec[Condition](
      (cond: Condition) =>
        cond match
          case Condition.Always =>
            uint8.encode(0)
          case Condition.Not(inner) =>
            uint8.encode(1).flatMap(b => conditionCodec.encode(inner).map(b ++ _))
          case Condition.And(all) =>
            uint8.encode(2).flatMap(b => listOfN(int32, conditionCodec).encode(all).map(b ++ _))
          case Condition.Or(any) =>
            uint8.encode(3).flatMap(b => listOfN(int32, conditionCodec).encode(any).map(b ++ _))
      ,
      (bits: BitVector) =>
        uint8.decode(bits).flatMap { result =>
          result.value match
            case 0 => Attempt.successful(DecodeResult(Condition.Always, result.remainder))
            case 1 => conditionCodec.decode(result.remainder).map(_.map(Condition.Not(_)))
            case 2 => listOfN(int32, conditionCodec).decode(result.remainder).map(_.map(Condition.And(_)))
            case 3 => listOfN(int32, conditionCodec).decode(result.remainder).map(_.map(Condition.Or(_)))
            case n => Attempt.failure(Err(s"Unknown Condition discriminator: $n"))
        }
    )
  }

  // ---------------------------------------------------------------------------
  // EdgeGuard codec (enum with Conditional variant)
  // ---------------------------------------------------------------------------

  given Codec[EdgeGuard] = Codec[EdgeGuard](
    (guard: EdgeGuard) =>
      guard match
        case EdgeGuard.OnSuccess      => uint8.encode(0)
        case EdgeGuard.OnFailure      => uint8.encode(1)
        case EdgeGuard.Always         => uint8.encode(2)
        case EdgeGuard.OnAny          => uint8.encode(3)
        case EdgeGuard.OnTimeout      => uint8.encode(4)
        case c: EdgeGuard.Conditional => uint8.encode(5).flatMap(b => conditionCodec.encode(c.condition).map(b ++ _))
    ,
    (bits: BitVector) =>
      uint8.decode(bits).flatMap { result =>
        result.value match
          case 0 => Attempt.successful(DecodeResult(EdgeGuard.OnSuccess, result.remainder))
          case 1 => Attempt.successful(DecodeResult(EdgeGuard.OnFailure, result.remainder))
          case 2 => Attempt.successful(DecodeResult(EdgeGuard.Always, result.remainder))
          case 3 => Attempt.successful(DecodeResult(EdgeGuard.OnAny, result.remainder))
          case 4 => Attempt.successful(DecodeResult(EdgeGuard.OnTimeout, result.remainder))
          case 5 => conditionCodec.decode(result.remainder).map(_.map(EdgeGuard.Conditional(_)))
          case n => Attempt.failure(Err(s"Unknown EdgeGuard discriminator: $n"))
      }
  )

  // ---------------------------------------------------------------------------
  // JoinStrategy codec
  // ---------------------------------------------------------------------------

  given Codec[JoinStrategy] = Codec[JoinStrategy](
    (strategy: JoinStrategy) =>
      strategy match
        case JoinStrategy.And       => uint8.encode(0)
        case JoinStrategy.Or        => uint8.encode(1)
        case JoinStrategy.Quorum(n) => uint8.encode(2).flatMap(b => int32.encode(n).map(b ++ _))
    ,
    (bits: BitVector) =>
      uint8.decode(bits).flatMap { result =>
        result.value match
          case 0 => Attempt.successful(DecodeResult(JoinStrategy.And, result.remainder))
          case 1 => Attempt.successful(DecodeResult(JoinStrategy.Or, result.remainder))
          case 2 => int32.decode(result.remainder).map(_.map(JoinStrategy.Quorum(_)))
          case n => Attempt.failure(Err(s"Unknown JoinStrategy discriminator: $n"))
      }
  )

  // ---------------------------------------------------------------------------
  // NodeType codec
  // ---------------------------------------------------------------------------

  private val nodeTypeJoinDataCodec: Codec[(ForkId, JoinStrategy, Option[FiniteDuration])] =
    (forkIdCodec :: summon[Codec[JoinStrategy]] :: optional(bool, summon[Codec[FiniteDuration]]))

  given Codec[NodeType] = Codec[NodeType](
    (nodeType: NodeType) =>
      nodeType match
        case NodeType.Standard =>
          uint8.encode(0)
        case NodeType.Fork(forkId) =>
          uint8.encode(1).flatMap(b => forkIdCodec.encode(forkId).map(b ++ _))
        case NodeType.Join(forkId, strategy, timeout) =>
          uint8.encode(2).flatMap(b => nodeTypeJoinDataCodec.encode((forkId, strategy, timeout)).map(b ++ _))
    ,
    (bits: BitVector) =>
      uint8.decode(bits).flatMap { result =>
        result.value match
          case 0 => Attempt.successful(DecodeResult(NodeType.Standard, result.remainder))
          case 1 => forkIdCodec.decode(result.remainder).map(_.map(NodeType.Fork(_)))
          case 2 =>
            nodeTypeJoinDataCodec.decode(result.remainder).map(_.map { case (forkId, strategy, timeout) =>
              NodeType.Join(forkId, strategy, timeout)
            })
          case n => Attempt.failure(Err(s"Unknown NodeType discriminator: $n"))
      }
  )

  // ---------------------------------------------------------------------------
  // Edge codec
  // ---------------------------------------------------------------------------

  given Codec[Edge] =
    (nodeIdCodec :: nodeIdCodec :: summon[Codec[EdgeGuard]]).xmap(
      { case (from, to, guard) => Edge(from, to, guard) },
      edge => (edge.from, edge.to, edge.guard)
    )

  // ---------------------------------------------------------------------------
  // Node codec
  // ---------------------------------------------------------------------------

  given Codec[Node] =
    (nodeIdCodec ::
      optional(bool, variableSizeBytes(int32, utf8)) ::
      summon[Codec[RetryPolicy]] ::
      summon[Codec[DeliveryStrategy]] ::
      serviceAddressCodec ::
      summon[Codec[NodeType]]).xmap(
      { case (id, label, retry, delivery, address, nodeType) =>
        Node(id, label, retry, delivery, address, nodeType)
      },
      node => (node.id, node.label, node.retryPolicy, node.deliveryStrategy, node.address, node.nodeType)
    )

  // ---------------------------------------------------------------------------
  // Map[NodeId, Node] codec
  // ---------------------------------------------------------------------------

  given Codec[Map[NodeId, Node]] = new Codec[Map[NodeId, Node]]:
    private val entryCodec: Codec[(NodeId, Node)] =
      (nodeIdCodec :: summon[Codec[Node]]).xmap(
        { case (id, node) => (id, node) },
        { case (id, node) => (id, node) }
      )

    override def sizeBound: SizeBound = SizeBound.unknown

    override def encode(value: Map[NodeId, Node]): Attempt[BitVector] =
      for
        countBits <- int32.encode(value.size)
        entriesBits <- value.toList.foldLeft(Attempt.successful(BitVector.empty)) { (acc, entry) =>
          acc.flatMap(bits => entryCodec.encode(entry).map(bits ++ _))
        }
      yield countBits ++ entriesBits

    override def decode(bits: BitVector): Attempt[DecodeResult[Map[NodeId, Node]]] =
      for
        countResult <- int32.decode(bits)
        count     = countResult.value
        remaining = countResult.remainder
        result <- decodeEntries(remaining, count, List.empty)
      yield result.map(_.toMap)

    private def decodeEntries(
      bits: BitVector,
      remaining: Int,
      acc: List[(NodeId, Node)]
    ): Attempt[DecodeResult[List[(NodeId, Node)]]] =
      if remaining <= 0 then Attempt.successful(DecodeResult(acc.reverse, bits))
      else
        entryCodec.decode(bits).flatMap { result =>
          decodeEntries(result.remainder, remaining - 1, result.value :: acc)
        }

  // ---------------------------------------------------------------------------
  // NonEmptyList[NodeId] codec
  // ---------------------------------------------------------------------------

  given Codec[NonEmptyList[NodeId]] = new Codec[NonEmptyList[NodeId]]:
    override def sizeBound: SizeBound = SizeBound.unknown

    override def encode(value: NonEmptyList[NodeId]): Attempt[BitVector] =
      val list = value.toList
      for
        countBits <- int32.encode(list.size)
        itemsBits <- list.foldLeft(Attempt.successful(BitVector.empty)) { (acc, nodeId) =>
          acc.flatMap(bits => nodeIdCodec.encode(nodeId).map(bits ++ _))
        }
      yield countBits ++ itemsBits

    override def decode(bits: BitVector): Attempt[DecodeResult[NonEmptyList[NodeId]]] =
      for
        countResult <- int32.decode(bits)
        count     = countResult.value
        remaining = countResult.remainder
        result <- decodeItems(remaining, count, List.empty)
        nel <- result.value match
          case h :: t => Attempt.successful(DecodeResult(NonEmptyList(h, t), result.remainder))
          case Nil    => Attempt.failure(Err("NonEmptyList cannot be empty"))
      yield nel

    private def decodeItems(
      bits: BitVector,
      remaining: Int,
      acc: List[NodeId]
    ): Attempt[DecodeResult[List[NodeId]]] =
      if remaining <= 0 then Attempt.successful(DecodeResult(acc.reverse, bits))
      else
        nodeIdCodec.decode(bits).flatMap { result =>
          decodeItems(result.remainder, remaining - 1, result.value :: acc)
        }

  // ---------------------------------------------------------------------------
  // Dag codec
  // ---------------------------------------------------------------------------

  given Codec[Dag] =
    (dagHashCodec ::
      summon[Codec[NonEmptyList[NodeId]]] ::
      summon[Codec[RepeatPolicy]] ::
      summon[Codec[Map[NodeId, Node]]] ::
      listOfN(int32, summon[Codec[Edge]]) ::
      optional(bool, summon[Codec[FiniteDuration]])).xmap(
      { case (hash, entryPoints, repeatPolicy, nodes, edges, timeout) =>
        Dag(hash, entryPoints, repeatPolicy, nodes, edges, timeout)
      },
      dag => (dag.hash, dag.entryPoints, dag.repeatPolicy, dag.nodes, dag.edges, dag.timeout)
    )
