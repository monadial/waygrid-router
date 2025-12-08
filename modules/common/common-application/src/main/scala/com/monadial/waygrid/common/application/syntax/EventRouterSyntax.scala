package com.monadial.waygrid.common.application.syntax

import com.monadial.waygrid.common.application.`macro`.EventRouterMacro
import com.monadial.waygrid.common.domain.algebra.messaging.event.Event as DomainEvent
import com.monadial.waygrid.common.domain.model.envelope.DomainEnvelope
import shapeless3.typeable.Typeable

/**
 * DSL methods available inside an EventRouterMacro.build { … } block.
 */
object EventRouterSyntax:

  /**
   * Register a handler for events of type `E` at the **default** priority (0).
   *
   * @tparam F your effect type (e.g. IO)
   * @tparam E the specific DomainEvent subtype to match
   * @param f   a function from `Event[E]` to an `F[Unit]`
   * @param b   the enclosing macro’s Builder[F]
   * @param tpe evidence that `E` is Typeable at runtime
   */
  inline def event[F[+_], E <: DomainEvent](
    inline f: DomainEnvelope[E] => F[Unit]
  )(using b: EventRouterMacro.Builder[F], tpe: Typeable[E]): Unit =
    b.handle(f, 0)

  /**
   * Register a handler for events of type `E` at an **explicit** priority.
   *
   * Higher‐priority handlers (larger numbers) will be launched before lower‐priority ones.
   *
   * @tparam F        your effect type
   * @tparam E        the specific DomainEvent subtype to match
   * @param priority ordering weight (higher runs first)
   * @param f        a function from `Event[E]` to an `F[Unit]`
   * @param b        the enclosing macro’s Builder[F]
   * @param tpe      evidence that `E` is Typeable at runtime
   */
  inline def eventWithPriority[F[+_], E <: DomainEvent](
    priority: Int
  )(inline f: DomainEnvelope[E] => F[Unit])(using b: EventRouterMacro.Builder[F], tpe: Typeable[E]): Unit =
    b.handle(f, priority)
