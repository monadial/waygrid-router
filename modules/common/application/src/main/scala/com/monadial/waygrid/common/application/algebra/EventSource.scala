package com.monadial.waygrid.common.application.algebra

import cats.effect.Resource
import com.monadial.waygrid.common.application.algebra.EventSource.RebalanceHandler
import com.monadial.waygrid.common.domain.algebra.messaging.event.Event
import com.monadial.waygrid.common.domain.model.envelope.DomainEnvelope
import com.monadial.waygrid.common.domain.value.Address.Endpoint

/**
 * A high-level abstraction for subscribing to deserialized domain events.
 *
 * Uses a handler to process selected events from one or more logical streams.
 */
trait EventSource[F[+_]]:

  /**
   * A type alias for domain events.
   */
  type Evt = DomainEnvelope[? <: Event]

  /**
   * A handler that selectively processes domain events.
   */
  type Handler = PartialFunction[Evt, F[Unit]]

  /**
   * Subscribes to a single stream with the provided handler.
   *
   * @param endpoint the endpoint of the event stream
   * @param handler the handler for matching events
   * @return a managed subscription
   */
  def subscribe(endpoint: Endpoint)(handler: Handler): Resource[F, Unit]

  /**
   * subscribe to a single stream with the provided handler, and with rebalance handler.
   *
   * @param endpoint the endpoint of the event stream
   * @param rebalanceHandler  handler for rebalance events
   * @param handler  the handler for matching events
   * @return a managed subscription
   */
  def subscribe(endpoint: Endpoint, rebalanceHandler: RebalanceHandler[F])(handler: Handler): Resource[F, Unit]

object EventSource:

  /**
   * A handler that processes rebalance events.
   */
  type RebalanceHandler[F[+_]] = EventSource.RebalanceEvent => F[Unit]

  enum RebalanceEvent:
    case Started
    case Revoked(endpoint: Endpoint, oldAssignments: List[Int])
    case Completed(endpoint: Endpoint, newAssignments: List[Int])

  def apply[F[+_]](using ev: EventSource[F]): EventSource[F] = ev
