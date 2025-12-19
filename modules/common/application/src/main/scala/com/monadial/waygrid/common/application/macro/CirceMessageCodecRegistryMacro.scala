package com.monadial.waygrid.common.application.`macro`

import scala.collection.mutable
import scala.quoted.{ Expr, Quotes, Type }

import cats.effect.Async
import cats.syntax.all.*
import com.monadial.waygrid.common.application.algebra.Logger
import com.monadial.waygrid.common.domain.algebra.messaging.message.Message
import com.monadial.waygrid.common.domain.algebra.messaging.message.Value.MessageType
import io.circe.*

object CirceMessageCodecRegistryMacro:
  type MessageCodec = Codec[? <: Message]
  type CodecMap     = mutable.Map[MessageType, MessageCodec]

  private val eventCodecRegistry: CodecMap =
    mutable.Map.empty[MessageType, MessageCodec]

  inline def registerAll[E <: Message](): Unit =
    ${ registerAllImpl[E] }

  private def registerAllImpl[E <: Message](using q: Quotes, e: Type[E]): Expr[Unit] =
    import q.reflect.*

    val eventTrait = TypeRepr.of[E].typeSymbol
    val children   = eventTrait.children

    def canonicalName(sym: Symbol): String =
      val owner = sym.owner
      if owner.isNoSymbol || owner == Symbol.noSymbol then sym.name
      else
        val prefix =
          if owner.flags.is(Flags.Module) then canonicalName(owner).stripSuffix("$")
          else canonicalName(owner)
        val full = s"$prefix.${sym.name}"
        // remove the artificial "<root>." prefix if present
        full.stripPrefix("<root>.")

    val registrations = children.map { subtype =>
      val fqcn       = canonicalName(subtype)
      val fqcnExpr   = Expr(fqcn)
      val subtypeTpe = subtype.typeRef
      val codecTpe   = TypeRepr.of[Codec].appliedTo(subtypeTpe)

      if CirceMessageCodecRegistryMacro.isRegistered(MessageType(fqcn)) then
        report.errorAndAbort(s"Duplicate codec registration detected for: $fqcn")

      Implicits.search(codecTpe) match
        case success: ImplicitSearchSuccess =>
          report.info(s"Found Codec for ${fqcn}: ${success.tree.show}")
          val codecExpr = success.tree.asExprOf[Codec[? <: Message]]
          '{
            CirceMessageCodecRegistryMacro.register(
              MessageType($fqcnExpr),
              $codecExpr.asInstanceOf[Codec[Message]]
            )
          }
        case _: ImplicitSearchFailure =>
          report.errorAndAbort(
            s"Could not find implicit Codec[$fqcn]. Did you forget to `import` or `derives Codec`?"
          )
    }

    Expr.block(registrations.toList, '{ () })

  private def register(eType: MessageType, codec: Codec[? <: Message]): Unit =
    eventCodecRegistry += (eType -> codec)

  private def isRegistered(eventType: MessageType): Boolean =
    eventCodecRegistry.contains(eventType)

  inline def readRegistry: CodecMap = eventCodecRegistry

  def debug[F[+_]: {Async, Logger}]: F[Unit] =
    eventCodecRegistry.toList.traverse_ {
      case (eventType, _) =>
        Logger[F].debug(s"Registered codec for EventType: ${eventType.show}")
    }
