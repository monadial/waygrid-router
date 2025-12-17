package com.monadial.waygrid.common.application.algebra

import com.monadial.waygrid.common.application.domain.model.event.{ Event, RawEvent }
import com.monadial.waygrid.common.domain.algebra.messaging.event.Event as DomainEvent

/**
 * Typeclass-style abstraction for encoding and decoding domain events.
 *
 * Allows pluggable implementations (e.g., JSON, Avro) that convert between
 * structured domain-level events (`Event[E]`) and their serialized wire format (`RawEvent`).
 *
 * Used by event routers, processors, and sinks to serialize/deserialize events
 * in a type-safe and modular way.
 */
trait EventCodec[F[_]]:

  /**
   * Decodes a raw serialized event into a structured, typed domain event.
   *
   * @param raw The incoming raw event (e.g. from Kafka, HTTP, etc.)
   * @return A fully typed domain event wrapped in the effect type
   */
  def decode(raw: RawEvent): F[Event[? <: DomainEvent]]

  /**
   * Encodes a domain event into its raw serialized form.
   *
   * @param event The structured domain event to serialize
   * @return A raw wire-format event, suitable for transport or persistence
   */
  def encode[E <: DomainEvent](event: Event[E]): F[RawEvent]
