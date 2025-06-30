package com.monadial.waygrid.common.application.`macro`

import com.monadial.waygrid.common.application.algebra.Logger
import cats.effect.Async
import cats.syntax.all.*
import com.monadial.waygrid.common.application.domain.model.event.EventType
import com.monadial.waygrid.common.domain.model.event.Event as DomainEvent
import io.circe.*

import scala.collection.mutable
import scala.quoted.{Expr, Quotes, Type}

object CirceEventCodecRegistryMacro:
  type EventCodec       = Codec[? <: DomainEvent]
  private type CodecMap = mutable.Map[EventType, EventCodec]

  private val eventCodecRegistry: CodecMap =
    mutable.Map.empty[EventType, EventCodec]

  inline def registerAll[E <: DomainEvent](): Unit =
    ${ registerAllImpl[E] }

  private def registerAllImpl[E <: DomainEvent](using q: Quotes, e: Type[E]): Expr[Unit] =
    import q.reflect.*

    val eventTrait = TypeRepr.of[E].typeSymbol
    val children   = eventTrait.children

    val registrations = children.map { subtype =>
      val fqcn       = subtype.fullName
      val fqcnExpr   = Expr(fqcn)
      val subtypeTpe = subtype.typeRef
      val codecTpe   = TypeRepr.of[Codec].appliedTo(subtypeTpe)

      if CirceEventCodecRegistryMacro.isRegistered(EventType(fqcn)) then
        report.errorAndAbort(s"Duplicate codec registration detected for: $fqcn")

      Implicits.search(codecTpe) match
        case success: ImplicitSearchSuccess =>
          val codecExpr = success.tree.asExprOf[Codec[? <: DomainEvent]]
          '{
            CirceEventCodecRegistryMacro.register(
              EventType($fqcnExpr),
              $codecExpr.asInstanceOf[Codec[DomainEvent]]
            )
          }
        case _: ImplicitSearchFailure =>
          report.errorAndAbort(
            s"Could not find implicit Codec[${fqcn}]. Did you forget to `import` or `derives Codec`?"
          )
    }

    Expr.block(registrations.toList, '{ () })

  private def register(eType: EventType, codec: Codec[? <: DomainEvent]): Unit =
    eventCodecRegistry += (eType -> codec)

  private def isRegistered(eventType: EventType): Boolean =
    eventCodecRegistry.contains(eventType)

  def readRegistry(): CodecMap = eventCodecRegistry

  def debug[F[+_]: {Async, Logger}]: F[Unit] =
    eventCodecRegistry.toList.traverse_ {
      case (eventType, _) =>
        Logger[F].debug(s"Registered codec for EventType: ${eventType.show}")
    }
