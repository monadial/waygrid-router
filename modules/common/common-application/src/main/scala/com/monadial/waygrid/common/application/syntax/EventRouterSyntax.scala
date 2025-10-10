package com.monadial.waygrid.common.application.syntax

import com.monadial.waygrid.common.application.`macro`.EventRouterMacro
import com.monadial.waygrid.common.application.domain.model.envelope.Envelope
import com.monadial.waygrid.common.domain.model.event.Event as DomainEvent
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
  inline def route[F[+_], E <: DomainEvent](
    inline f: Envelope[E] => F[Unit]
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
  inline def routeWithPriority[F[+_], E <: DomainEvent](
    priority: Int
  )(inline f: Envelope[E] => F[Unit])(using b: EventRouterMacro.Builder[F], tpe: Typeable[E]): Unit =
    b.handle(f, priority)
