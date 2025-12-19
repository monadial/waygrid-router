package com.monadial.waygrid.common.application.util.vulcan.codecs

import cats.data.NonEmptyList
import cats.syntax.all.*
import com.monadial.waygrid.common.application.util.vulcan.VulcanUtils.given
import com.monadial.waygrid.common.application.util.vulcan.codecs.DomainAddressVulcanCodecs.given
import com.monadial.waygrid.common.application.util.vulcan.codecs.DomainPrimitivesVulcanCodecs.given
import com.monadial.waygrid.common.application.util.vulcan.codecs.DomainRoutingVulcanCodecs.given
import com.monadial.waygrid.common.domain.model.traversal.condition.Condition
import com.monadial.waygrid.common.domain.model.traversal.dag.Value.{ EdgeGuard, NodeId }
import com.monadial.waygrid.common.domain.model.traversal.dag.{ Dag, Edge, JoinStrategy, Node, NodeType }
import vulcan.Codec

/**
 * Vulcan Avro codecs for DAG-related domain types.
 *
 * Handles:
 * - Condition (recursive enum: Always | Not | And | Or)
 * - EdgeGuard (enum with Conditional variant containing Condition)
 * - JoinStrategy (enum: And | Or | Quorum)
 * - NodeType (enum: Standard | Fork | Join)
 * - Node, Edge, Dag
 *
 * Note on recursive types:
 * The Condition type is recursive (Not contains Condition, And/Or contain List[Condition]).
 * Vulcan handles this via lazy evaluation in the codec definition.
 *
 * Import `DomainRoutingDagVulcanCodecs.given` to bring these codecs into scope.
 */
object DomainRoutingDagVulcanCodecs:

  // ---------------------------------------------------------------------------
  // Condition (recursive enum) - serialized as JSON string
  // ---------------------------------------------------------------------------

  /**
   * Condition encoded as JSON string.
   *
   * Avro/Vulcan doesn't handle recursive types well (causes infinite schema generation).
   * We serialize Condition as its JSON representation to avoid this issue.
   * The JSON codec is provided by Circe (already available in the codebase).
   */
  given Codec[Condition] =
    import io.circe.syntax.*
    import io.circe.parser.decode
    import com.monadial.waygrid.common.domain.model.traversal.condition.Condition.given

    Codec.string.imapError(jsonStr =>
      decode[Condition](jsonStr).left.map(e => vulcan.AvroError(s"Invalid Condition JSON: ${e.getMessage}"))
    )(_.asJson.noSpaces)

  // ---------------------------------------------------------------------------
  // EdgeGuard (enum with Conditional variant)
  // ---------------------------------------------------------------------------

  /**
   * EdgeGuard.OnSuccess - execute on success.
   */
  private given Codec[EdgeGuard.OnSuccess.type] = Codec.record(
    name = "EdgeGuardOnSuccess",
    namespace = "com.monadial.waygrid.common.domain.model.traversal.dag"
  ) { _ =>
    EdgeGuard.OnSuccess.pure
  }

  /**
   * EdgeGuard.OnFailure - execute on failure.
   */
  private given Codec[EdgeGuard.OnFailure.type] = Codec.record(
    name = "EdgeGuardOnFailure",
    namespace = "com.monadial.waygrid.common.domain.model.traversal.dag"
  ) { _ =>
    EdgeGuard.OnFailure.pure
  }

  /**
   * EdgeGuard.Always - always execute.
   */
  private given edgeGuardAlwaysCodec: Codec[EdgeGuard.Always.type] = Codec.record(
    name = "EdgeGuardAlways",
    namespace = "com.monadial.waygrid.common.domain.model.traversal.dag"
  ) { _ =>
    EdgeGuard.Always.pure
  }

  /**
   * EdgeGuard.OnAny - execute on first result.
   */
  private given Codec[EdgeGuard.OnAny.type] = Codec.record(
    name = "EdgeGuardOnAny",
    namespace = "com.monadial.waygrid.common.domain.model.traversal.dag"
  ) { _ =>
    EdgeGuard.OnAny.pure
  }

  /**
   * EdgeGuard.OnTimeout - execute on timeout.
   */
  private given Codec[EdgeGuard.OnTimeout.type] = Codec.record(
    name = "EdgeGuardOnTimeout",
    namespace = "com.monadial.waygrid.common.domain.model.traversal.dag"
  ) { _ =>
    EdgeGuard.OnTimeout.pure
  }

  /**
   * EdgeGuard.Conditional - conditional execution.
   */
  private given Codec[EdgeGuard.Conditional] = Codec.record(
    name = "EdgeGuardConditional",
    namespace = "com.monadial.waygrid.common.domain.model.traversal.dag"
  ) { field =>
    field("condition", _.condition).map(EdgeGuard.Conditional.apply)
  }

  /**
   * EdgeGuard sealed trait as Avro union.
   * Uses explicit encoding with pattern matching for Scala 3 compatibility.
   */
  given Codec[EdgeGuard] =
    import scala.annotation.nowarn

    val onSuccessCodec   = summon[Codec[EdgeGuard.OnSuccess.type]]
    val onFailureCodec   = summon[Codec[EdgeGuard.OnFailure.type]]
    val alwaysCodec      = edgeGuardAlwaysCodec
    val onAnyCodec       = summon[Codec[EdgeGuard.OnAny.type]]
    val onTimeoutCodec   = summon[Codec[EdgeGuard.OnTimeout.type]]
    val conditionalCodec = summon[Codec[EdgeGuard.Conditional]]

    val unionCodec = Codec.union[EdgeGuard] { alt =>
      alt[EdgeGuard.OnSuccess.type] |+|
        alt[EdgeGuard.OnFailure.type] |+|
        alt[EdgeGuard.Always.type](using edgeGuardAlwaysCodec) |+|
        alt[EdgeGuard.OnAny.type] |+|
        alt[EdgeGuard.OnTimeout.type] |+|
        alt[EdgeGuard.Conditional]
    }

    @nowarn("msg=deprecated")
    val result = Codec.instance(
      unionCodec.schema,
      (eg: EdgeGuard) =>
        eg match
          case EdgeGuard.OnSuccess      => onSuccessCodec.encode(EdgeGuard.OnSuccess)
          case EdgeGuard.OnFailure      => onFailureCodec.encode(EdgeGuard.OnFailure)
          case EdgeGuard.Always         => alwaysCodec.encode(EdgeGuard.Always)
          case EdgeGuard.OnAny          => onAnyCodec.encode(EdgeGuard.OnAny)
          case EdgeGuard.OnTimeout      => onTimeoutCodec.encode(EdgeGuard.OnTimeout)
          case v: EdgeGuard.Conditional => conditionalCodec.encode(v),
      unionCodec.decode
    )
    result

  // ---------------------------------------------------------------------------
  // JoinStrategy (enum)
  // ---------------------------------------------------------------------------

  /**
   * JoinStrategy.And - wait for all branches.
   */
  private given Codec[JoinStrategy.And.type] = Codec.record(
    name = "JoinStrategyAnd",
    namespace = "com.monadial.waygrid.common.domain.model.traversal.dag"
  ) { _ =>
    JoinStrategy.And.pure
  }

  /**
   * JoinStrategy.Or - wait for any branch.
   */
  private given Codec[JoinStrategy.Or.type] = Codec.record(
    name = "JoinStrategyOr",
    namespace = "com.monadial.waygrid.common.domain.model.traversal.dag"
  ) { _ =>
    JoinStrategy.Or.pure
  }

  /**
   * JoinStrategy.Quorum - wait for N branches.
   */
  private given Codec[JoinStrategy.Quorum] = Codec.record(
    name = "JoinStrategyQuorum",
    namespace = "com.monadial.waygrid.common.domain.model.traversal.dag"
  ) { field =>
    field("n", _.n).map(JoinStrategy.Quorum.apply)
  }

  /**
   * JoinStrategy enum as Avro union.
   * Uses explicit encoding with pattern matching for Scala 3 compatibility.
   */
  given Codec[JoinStrategy] =
    import scala.annotation.nowarn

    val andCodec    = summon[Codec[JoinStrategy.And.type]]
    val orCodec     = summon[Codec[JoinStrategy.Or.type]]
    val quorumCodec = summon[Codec[JoinStrategy.Quorum]]

    val unionCodec = Codec.union[JoinStrategy] { alt =>
      alt[JoinStrategy.And.type] |+|
        alt[JoinStrategy.Or.type] |+|
        alt[JoinStrategy.Quorum]
    }

    @nowarn("msg=deprecated")
    val result = Codec.instance(
      unionCodec.schema,
      (js: JoinStrategy) =>
        js match
          case JoinStrategy.And       => andCodec.encode(JoinStrategy.And)
          case JoinStrategy.Or        => orCodec.encode(JoinStrategy.Or)
          case v: JoinStrategy.Quorum => quorumCodec.encode(v),
      unionCodec.decode
    )
    result

  // ---------------------------------------------------------------------------
  // NodeType (enum)
  // ---------------------------------------------------------------------------

  /**
   * NodeType.Standard - regular processing node.
   */
  private given Codec[NodeType.Standard.type] = Codec.record(
    name = "NodeTypeStandard",
    namespace = "com.monadial.waygrid.common.domain.model.traversal.dag"
  ) { _ =>
    NodeType.Standard.pure
  }

  /**
   * NodeType.Fork - fan-out node.
   */
  private given Codec[NodeType.Fork] = Codec.record(
    name = "NodeTypeFork",
    namespace = "com.monadial.waygrid.common.domain.model.traversal.dag"
  ) { field =>
    field("forkId", _.forkId).map(NodeType.Fork.apply)
  }

  /**
   * NodeType.Join - fan-in node.
   */
  private given Codec[NodeType.Join] = Codec.record(
    name = "NodeTypeJoin",
    namespace = "com.monadial.waygrid.common.domain.model.traversal.dag"
  ) { field =>
    (
      field("forkId", _.forkId),
      field("strategy", _.strategy),
      field("timeout", _.timeout)
    ).mapN(NodeType.Join.apply)
  }

  /**
   * NodeType enum as Avro union.
   * Uses explicit encoding with pattern matching for Scala 3 compatibility.
   */
  given Codec[NodeType] =
    import scala.annotation.nowarn

    val standardCodec = summon[Codec[NodeType.Standard.type]]
    val forkCodec     = summon[Codec[NodeType.Fork]]
    val joinCodec     = summon[Codec[NodeType.Join]]

    val unionCodec = Codec.union[NodeType] { alt =>
      alt[NodeType.Standard.type] |+|
        alt[NodeType.Fork] |+|
        alt[NodeType.Join]
    }

    @nowarn("msg=deprecated")
    val result = Codec.instance(
      unionCodec.schema,
      (nt: NodeType) =>
        nt match
          case NodeType.Standard => standardCodec.encode(NodeType.Standard)
          case v: NodeType.Fork  => forkCodec.encode(v)
          case v: NodeType.Join  => joinCodec.encode(v),
      unionCodec.decode
    )
    result

  // ---------------------------------------------------------------------------
  // Edge
  // ---------------------------------------------------------------------------

  /**
   * Edge encoded as Avro record.
   */
  given Codec[Edge] = Codec.record(
    name = "Edge",
    namespace = "com.monadial.waygrid.common.domain.model.traversal.dag"
  ) { field =>
    (
      field("from", _.from),
      field("to", _.to),
      field("guard", _.guard)
    ).mapN(Edge.apply)
  }

  // ---------------------------------------------------------------------------
  // Node
  // ---------------------------------------------------------------------------

  /**
   * Node encoded as Avro record.
   */
  given Codec[Node] = Codec.record(
    name = "Node",
    namespace = "com.monadial.waygrid.common.domain.model.traversal.dag"
  ) { field =>
    (
      field("id", _.id),
      field("label", _.label),
      field("retryPolicy", _.retryPolicy),
      field("deliveryStrategy", _.deliveryStrategy),
      field("address", _.address),
      field("nodeType", _.nodeType)
    ).mapN(Node.apply)
  }

  // ---------------------------------------------------------------------------
  // NonEmptyList helper
  // ---------------------------------------------------------------------------

  /**
   * NonEmptyList codec as Avro array (encoded as List, validated on decode).
   */
  given [A](using Codec[A]): Codec[NonEmptyList[A]] =
    Codec.list[A].imapError(list =>
      NonEmptyList
        .fromList(list)
        .toRight(vulcan.AvroError("Expected non-empty list"))
    )(_.toList)

  // ---------------------------------------------------------------------------
  // Map[NodeId, Node] helper
  // ---------------------------------------------------------------------------

  /**
   * Map[NodeId, Node] encoded as Avro array of nodes (nodes have their own ID).
   * We encode as List[Node] and reconstruct the Map on decode.
   */
  given Codec[Map[NodeId, Node]] =
    Codec.list[Node].imap(nodes => nodes.map(n => n.id -> n).toMap)(nodeMap => nodeMap.values.toList)

  // ---------------------------------------------------------------------------
  // Dag
  // ---------------------------------------------------------------------------

  /**
   * Dag encoded as Avro record.
   */
  given Codec[Dag] = Codec.record(
    name = "Dag",
    namespace = "com.monadial.waygrid.common.domain.model.traversal.dag"
  ) { field =>
    (
      field("hash", _.hash),
      field("entryPoints", _.entryPoints),
      field("repeatPolicy", _.repeatPolicy),
      field("nodes", _.nodes),
      field("edges", _.edges),
      field("timeout", _.timeout)
    ).mapN(Dag.apply)
  }
