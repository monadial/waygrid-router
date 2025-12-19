package com.monadial.waygrid.common.application.util.vulcan.codecs

import cats.syntax.all.*
import com.monadial.waygrid.common.application.util.vulcan.VulcanUtils.given
import com.monadial.waygrid.common.application.util.vulcan.codecs.DomainPrimitivesVulcanCodecs.given
import com.monadial.waygrid.common.domain.model.resiliency.RetryPolicy
import com.monadial.waygrid.common.domain.model.routing.Value.{ DeliveryStrategy, RepeatPolicy }
import vulcan.Codec

/**
 * Vulcan Avro codecs for routing-related domain types.
 *
 * Handles:
 * - RetryPolicy (sealed trait with 8 variants)
 * - RepeatPolicy (enum with 4 variants)
 * - DeliveryStrategy (enum with 3 variants)
 *
 * Avro Representation:
 * - All sealed traits are encoded as Avro unions
 * - Each variant becomes a record in the union
 * - Case objects become records with no fields
 *
 * Import `DomainRoutingVulcanCodecs.given` to bring these codecs into scope.
 */
object DomainRoutingVulcanCodecs:

  // ---------------------------------------------------------------------------
  // RetryPolicy (sealed trait with 8 variants)
  // ---------------------------------------------------------------------------

  /**
   * RetryPolicy.None - no retries.
   * Case objects use a unit-like record pattern.
   */
  private given Codec[RetryPolicy.None.type] = Codec.record(
    name = "RetryPolicyNone",
    namespace = "com.monadial.waygrid.common.domain.model.resiliency"
  ) { _ =>
    RetryPolicy.None.pure
  }

  /**
   * RetryPolicy.Linear - linear backoff.
   */
  private given Codec[RetryPolicy.Linear] = Codec.record(
    name = "RetryPolicyLinear",
    namespace = "com.monadial.waygrid.common.domain.model.resiliency"
  ) { field =>
    (
      field("base", _.base),
      field("maxRetries", _.maxRetries)
    ).mapN(RetryPolicy.Linear.apply)
  }

  /**
   * RetryPolicy.Exponential - exponential backoff.
   */
  private given Codec[RetryPolicy.Exponential] = Codec.record(
    name = "RetryPolicyExponential",
    namespace = "com.monadial.waygrid.common.domain.model.resiliency"
  ) { field =>
    (
      field("base", _.base),
      field("maxRetries", _.maxRetries)
    ).mapN(RetryPolicy.Exponential.apply)
  }

  /**
   * RetryPolicy.BoundedExponential - capped exponential backoff.
   */
  private given Codec[RetryPolicy.BoundedExponential] = Codec.record(
    name = "RetryPolicyBoundedExponential",
    namespace = "com.monadial.waygrid.common.domain.model.resiliency"
  ) { field =>
    (
      field("base", _.base),
      field("cap", _.cap),
      field("maxRetries", _.maxRetries)
    ).mapN(RetryPolicy.BoundedExponential.apply)
  }

  /**
   * RetryPolicy.Fibonacci - Fibonacci backoff.
   */
  private given Codec[RetryPolicy.Fibonacci] = Codec.record(
    name = "RetryPolicyFibonacci",
    namespace = "com.monadial.waygrid.common.domain.model.resiliency"
  ) { field =>
    (
      field("base", _.base),
      field("maxRetries", _.maxRetries)
    ).mapN(RetryPolicy.Fibonacci.apply)
  }

  /**
   * RetryPolicy.Polynomial - polynomial backoff.
   */
  private given Codec[RetryPolicy.Polynomial] = Codec.record(
    name = "RetryPolicyPolynomial",
    namespace = "com.monadial.waygrid.common.domain.model.resiliency"
  ) { field =>
    (
      field("base", _.base),
      field("maxRetries", _.maxRetries),
      field("power", _.power)
    ).mapN(RetryPolicy.Polynomial.apply)
  }

  /**
   * RetryPolicy.DecorrelatedJitter - decorrelated jitter backoff.
   */
  private given Codec[RetryPolicy.DecorrelatedJitter] = Codec.record(
    name = "RetryPolicyDecorrelatedJitter",
    namespace = "com.monadial.waygrid.common.domain.model.resiliency"
  ) { field =>
    (
      field("base", _.base),
      field("maxRetries", _.maxRetries)
    ).mapN(RetryPolicy.DecorrelatedJitter.apply)
  }

  /**
   * RetryPolicy.FullJitter - full jitter backoff.
   */
  private given Codec[RetryPolicy.FullJitter] = Codec.record(
    name = "RetryPolicyFullJitter",
    namespace = "com.monadial.waygrid.common.domain.model.resiliency"
  ) { field =>
    (
      field("base", _.base),
      field("maxRetries", _.maxRetries)
    ).mapN(RetryPolicy.FullJitter.apply)
  }

  /**
   * RetryPolicy sealed trait as Avro union.
   */
  given Codec[RetryPolicy] = Codec.union[RetryPolicy] { alt =>
    alt[RetryPolicy.None.type] |+|
      alt[RetryPolicy.Linear] |+|
      alt[RetryPolicy.Exponential] |+|
      alt[RetryPolicy.BoundedExponential] |+|
      alt[RetryPolicy.Fibonacci] |+|
      alt[RetryPolicy.Polynomial] |+|
      alt[RetryPolicy.DecorrelatedJitter] |+|
      alt[RetryPolicy.FullJitter]
  }

  // ---------------------------------------------------------------------------
  // RepeatPolicy (enum with 4 variants)
  // ---------------------------------------------------------------------------

  /**
   * RepeatPolicy.NoRepeat - no repetition.
   */
  private given Codec[RepeatPolicy.NoRepeat.type] = Codec.record(
    name = "RepeatPolicyNoRepeat",
    namespace = "com.monadial.waygrid.common.domain.model.routing"
  ) { _ =>
    RepeatPolicy.NoRepeat.pure
  }

  /**
   * RepeatPolicy.Indefinitely - repeat forever.
   */
  private given Codec[RepeatPolicy.Indefinitely] = Codec.record(
    name = "RepeatPolicyIndefinitely",
    namespace = "com.monadial.waygrid.common.domain.model.routing"
  ) { field =>
    field("every", _.every).map(RepeatPolicy.Indefinitely.apply)
  }

  /**
   * RepeatPolicy.Times - repeat N times.
   */
  private given Codec[RepeatPolicy.Times] = Codec.record(
    name = "RepeatPolicyTimes",
    namespace = "com.monadial.waygrid.common.domain.model.routing"
  ) { field =>
    (
      field("every", _.every),
      field("time", _.time)
    ).mapN(RepeatPolicy.Times.apply)
  }

  /**
   * RepeatPolicy.Until - repeat until deadline.
   */
  private given Codec[RepeatPolicy.Until] = Codec.record(
    name = "RepeatPolicyUntil",
    namespace = "com.monadial.waygrid.common.domain.model.routing"
  ) { field =>
    (
      field("every", _.every),
      field("until", _.until)
    ).mapN(RepeatPolicy.Until.apply)
  }

  /**
   * RepeatPolicy enum as Avro union.
   * Uses explicit encoding with pattern matching to ensure proper type discrimination
   * in Scala 3, where instanceOf checks may not work correctly with Vulcan's default union encoding.
   */
  given Codec[RepeatPolicy] =
    import scala.annotation.nowarn

    val noRepeatCodec     = summon[Codec[RepeatPolicy.NoRepeat.type]]
    val indefinitelyCodec = summon[Codec[RepeatPolicy.Indefinitely]]
    val timesCodec        = summon[Codec[RepeatPolicy.Times]]
    val untilCodec        = summon[Codec[RepeatPolicy.Until]]

    // Build union schema
    val unionCodec = Codec.union[RepeatPolicy] { alt =>
      alt[RepeatPolicy.NoRepeat.type] |+|
        alt[RepeatPolicy.Indefinitely] |+|
        alt[RepeatPolicy.Times] |+|
        alt[RepeatPolicy.Until]
    }

    // Create custom codec with explicit encoding
    // @nowarn suppresses deprecation warning - no alternative method available
    @nowarn("msg=deprecated")
    val result = Codec.instance(
      unionCodec.schema,
      // Custom encode with pattern matching
      (rp: RepeatPolicy) =>
        rp match
          case RepeatPolicy.NoRepeat        => noRepeatCodec.encode(RepeatPolicy.NoRepeat)
          case v: RepeatPolicy.Indefinitely => indefinitelyCodec.encode(v)
          case v: RepeatPolicy.Times        => timesCodec.encode(v)
          case v: RepeatPolicy.Until        => untilCodec.encode(v),
      // Use union codec's decode
      unionCodec.decode
    )
    result

  // ---------------------------------------------------------------------------
  // DeliveryStrategy (enum with 3 variants)
  // ---------------------------------------------------------------------------

  /**
   * DeliveryStrategy.Immediate - deliver immediately.
   */
  private given Codec[DeliveryStrategy.Immediate.type] = Codec.record(
    name = "DeliveryStrategyImmediate",
    namespace = "com.monadial.waygrid.common.domain.model.routing"
  ) { _ =>
    DeliveryStrategy.Immediate.pure
  }

  /**
   * DeliveryStrategy.ScheduleAfter - deliver after delay.
   */
  private given Codec[DeliveryStrategy.ScheduleAfter] = Codec.record(
    name = "DeliveryStrategyScheduleAfter",
    namespace = "com.monadial.waygrid.common.domain.model.routing"
  ) { field =>
    field("delay", _.delay).map(DeliveryStrategy.ScheduleAfter.apply)
  }

  /**
   * DeliveryStrategy.ScheduleAt - deliver at specific time.
   */
  private given Codec[DeliveryStrategy.ScheduleAt] = Codec.record(
    name = "DeliveryStrategyScheduleAt",
    namespace = "com.monadial.waygrid.common.domain.model.routing"
  ) { field =>
    field("time", _.time).map(DeliveryStrategy.ScheduleAt.apply)
  }

  /**
   * DeliveryStrategy enum as Avro union.
   * Uses explicit encoding with pattern matching for Scala 3 compatibility.
   */
  given Codec[DeliveryStrategy] =
    import scala.annotation.nowarn

    val immediateCodec     = summon[Codec[DeliveryStrategy.Immediate.type]]
    val scheduleAfterCodec = summon[Codec[DeliveryStrategy.ScheduleAfter]]
    val scheduleAtCodec    = summon[Codec[DeliveryStrategy.ScheduleAt]]

    val unionCodec = Codec.union[DeliveryStrategy] { alt =>
      alt[DeliveryStrategy.Immediate.type] |+|
        alt[DeliveryStrategy.ScheduleAfter] |+|
        alt[DeliveryStrategy.ScheduleAt]
    }

    @nowarn("msg=deprecated")
    val result = Codec.instance(
      unionCodec.schema,
      (ds: DeliveryStrategy) =>
        ds match
          case DeliveryStrategy.Immediate      => immediateCodec.encode(DeliveryStrategy.Immediate)
          case v: DeliveryStrategy.ScheduleAfter => scheduleAfterCodec.encode(v)
          case v: DeliveryStrategy.ScheduleAt  => scheduleAtCodec.encode(v),
      unionCodec.decode
    )
    result
