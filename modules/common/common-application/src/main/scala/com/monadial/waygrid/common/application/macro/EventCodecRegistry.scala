package com.monadial.waygrid.common.application.`macro`

import com.monadial.waygrid.common.application.algebra.Logger
import com.monadial.waygrid.common.application.model.event.*
import com.monadial.waygrid.common.domain.model.event.Event as DomainEvent

import cats.effect.Async
import io.circe.*
import cats.syntax.all.*

import scala.collection.concurrent.TrieMap
import scala.quoted.{ Expr, Quotes, Type }

object EventCodecRegistry:
  type EventCodec       = Codec[? <: DomainEvent]
  private type CodecMap = TrieMap[EventType, EventCodec]

  private val eventCodecRegistry: CodecMap =
    TrieMap.empty[EventType, EventCodec]

  inline def registerAll[E <: DomainEvent](): Unit =
    ${ registerAllImpl[E] }

  private def registerAllImpl[E <: DomainEvent](using q: Quotes, e: Type[E]): Expr[Unit] =
    import q.reflect.*

    val eventTrait = TypeRepr.of[E].typeSymbol
    val children   = eventTrait.children
    report.info(s"Detected subtypes of ${Type.show[E]}: ${children.map(_.name).mkString(", ")}")

    val registrations = children.map { subtype =>
      val fqcnExpr   = Expr(subtype.fullName)
      val subtypeTpe = subtype.typeRef
      val codecTpe   = TypeRepr.of[Codec].appliedTo(subtypeTpe)

      Implicits.search(codecTpe) match
        case success: ImplicitSearchSuccess =>
          val codecExpr = success.tree.asExprOf[Codec[? <: DomainEvent]]
          '{
            EventCodecRegistry.register(
              EventType($fqcnExpr),
              $codecExpr.asInstanceOf[Codec[DomainEvent]]
            )
          }
        case _: ImplicitSearchFailure =>
          report.errorAndAbort(
            s"Could not find implicit Codec[${subtype}]. Did you forget to `import` or `derives Codec`?"
          )
    }

    Expr.block(registrations.toList, '{ () })

  private def register(eType: EventType, codec: Codec[? <: DomainEvent]): Unit =
    eventCodecRegistry += (eType -> codec)

  def readRegistry(): Map[EventType, EventCodec] = eventCodecRegistry.toMap

  def debug[F[+_]: {Async, Logger}]: F[Unit] =
    eventCodecRegistry.toList.traverse_ {
      case (eventType, codec) =>
        Logger[F].debug(s"Registered codec for EventType: ${eventType.show}")
    }
