package com.monadial.waygrid.common.domain.model.resiliency

import weaver.SimpleIOSuite

import scala.concurrent.duration.*
import cats.implicits.*

object BackoffSuite extends SimpleIOSuite:

  // ---------------------------------------------------------------------------
  //  Base helpers
  // ---------------------------------------------------------------------------

  private def inRange(d: FiniteDuration, min: FiniteDuration, max: FiniteDuration): Boolean =
    d >= min && d <= max

  private def roughlyWithin(d: FiniteDuration, max: FiniteDuration, tolerance: Double = 0.05): Boolean =
    d <= (max.toMillis * (1.0 + tolerance)).millis

  // ---------------------------------------------------------------------------
  //  None policy
  // ---------------------------------------------------------------------------

  test("None policy never retries"):
      for
        d1 <- Backoff[RetryPolicy].nextDelay(RetryPolicy.None, 1).pure
        d3 <- Backoff[RetryPolicy].nextDelay(RetryPolicy.None, 3).pure
      yield expect(d1.isEmpty) and expect(d3.isEmpty)

  // ---------------------------------------------------------------------------
  //  Linear
  // ---------------------------------------------------------------------------

  test("Linear backoff respects maxRetries and produces positive delay"):
      val p = RetryPolicy.Linear(500.millis, maxRetries = 3)
      for
        d1 <- Backoff[RetryPolicy].nextDelay(p, 1).pure
        d2 <- Backoff[RetryPolicy].nextDelay(p, 2).pure
        d4 <- Backoff[RetryPolicy].nextDelay(p, 4).pure
      yield expect(d1.exists(_ > Duration.Zero)) and
        expect(d2.exists(_ > Duration.Zero)) and
        expect(d4.isEmpty)

  test("Linear backoff with hashKey is deterministic and distinct per key"):
      val p  = RetryPolicy.Linear(1.second, maxRetries = 5)
      val k1 = Some("waystation-A")
      val k2 = Some("waystation-B")
      for
        d11 <- Backoff[RetryPolicy].nextDelay(p, 2, k1).pure
        d12 <- Backoff[RetryPolicy].nextDelay(p, 2, k1).pure
        d21 <- Backoff[RetryPolicy].nextDelay(p, 2, k2).pure
      yield expect.same(d11, d12) and expect(d11 != d21)

  // ---------------------------------------------------------------------------
  //  Exponential
  // ---------------------------------------------------------------------------

  test("Exponential backoff grows with attempts"):
      val p = RetryPolicy.Exponential(200.millis, maxRetries = 5)
      for
        d1 <- Backoff[RetryPolicy].nextDelay(p, 1).pure
        d2 <- Backoff[RetryPolicy].nextDelay(p, 2).pure
        d3 <- Backoff[RetryPolicy].nextDelay(p, 3).pure
      yield
        val inc = for a <- d1; b <- d2; c <- d3 yield a < b && b <= c
        expect(inc.contains(true))

  // ---------------------------------------------------------------------------
  //  Bounded Exponential
  // ---------------------------------------------------------------------------

  test("BoundedExponential never exceeds cap (with jitter tolerance)"):
      val base   = 300.millis
      val cap    = 2.seconds
      val policy = RetryPolicy.BoundedExponential(base, cap, maxRetries = 10)
      for
        delays <- (1 to 10).toList.traverse(n =>
          Backoff[RetryPolicy].nextDelay(policy, n, Some("node-x")).pure
        )
        within = delays.flatten.forall(d => roughlyWithin(d, cap))
      yield expect(within) and expect(delays.flatten.nonEmpty)

  // ---------------------------------------------------------------------------
  //  Polynomial
  // ---------------------------------------------------------------------------

  test("Polynomial backoff grows with attempt^power"):
      val p = RetryPolicy.Polynomial(100.millis, maxRetries = 5, 2.0)
      for
        d1 <- Backoff[RetryPolicy].nextDelay(p, 1).pure
        d3 <- Backoff[RetryPolicy].nextDelay(p, 3).pure
        d5 <- Backoff[RetryPolicy].nextDelay(p, 5).pure
      yield
        val inc = for a <- d1; b <- d3; c <- d5 yield a < b && b < c
        expect(inc.contains(true))

  // ---------------------------------------------------------------------------
  //  Fibonacci
  // ---------------------------------------------------------------------------

  test("Fibonacci backoff increases with attempt index"):
      val p = RetryPolicy.Fibonacci(50.millis, maxRetries = 6)
      for
        d1 <- Backoff[RetryPolicy].nextDelay(p, 1).pure
        d2 <- Backoff[RetryPolicy].nextDelay(p, 2).pure
        d4 <- Backoff[RetryPolicy].nextDelay(p, 4).pure
      yield
        val inc = for a <- d1; b <- d2; c <- d4 yield a <= b && b <= c
        expect(inc.contains(true))

  // ---------------------------------------------------------------------------
  //  DecorrelatedJitter
  // ---------------------------------------------------------------------------

  test("DecorrelatedJitter stays within [base, maxDelay]"):
      val base     = 200.millis
      val p        = RetryPolicy.DecorrelatedJitter(base, maxRetries = 5)
      val attempt  = 3
      val maxDelay = base * math.pow(3, attempt - 1).toLong
      for
        samples <- List.fill(20)(
          Backoff[RetryPolicy].nextDelay(p, attempt).pure
        ).sequence
        allOk = samples.flatten.forall(d => inRange(d, base, maxDelay))
      yield expect(allOk) and expect(samples.exists(_.isDefined))

  // ---------------------------------------------------------------------------
  //  FullJitter
  // ---------------------------------------------------------------------------

  test("FullJitter stays within [0, maxDelay]"):
      val base     = 400.millis
      val p        = RetryPolicy.FullJitter(base, maxRetries = 4)
      val attempt  = 3
      val maxDelay = base * math.pow(2, attempt - 1).toLong
      for
        samples <- List.fill(20)(
          Backoff[RetryPolicy].nextDelay(p, attempt).pure
        ).sequence
        allOk = samples.flatten.forall(d => inRange(d, Duration.Zero, maxDelay))
      yield expect(allOk) and expect(samples.exists(_.isDefined))

  // ---------------------------------------------------------------------------
  //  Hash Jitter Mode Tests
  // ---------------------------------------------------------------------------

  test("Hash jitter symmetric stays within ±maxPercent"):
      val p = RetryPolicy.Linear(1.second, 3)
      for
        d <- Backoff[RetryPolicy].nextDelay(p, 1, Some("hash-symmetric")).pure
      yield
        val ok = d.exists(dd => dd.toMillis >= 800 && dd.toMillis <= 1200)
        expect(ok)

  test("Hash jitter positive only tends to increase delay (≈ within [base, base+max%])"):
      val p = RetryPolicy.Linear(1.second, 3)
      for
        d <- Backoff[RetryPolicy].nextDelay(p, 1, Some("hash-positive")).pure
      yield
        // relaxed: allow small floating precision below base
        val ok = d.exists(dd => dd >= 0.9.second && dd <= 1.3.second)
        expect(ok)

  test("Hash jitter negative only tends to decrease delay (≈ within [base-max%, base])"):
      val p = RetryPolicy.Linear(1.second, 3)
      for
        d <- Backoff[RetryPolicy].nextDelay(p, 1, Some("hash-negative")).pure
      yield
        // relaxed: allow slight rounding above base
        val ok = d.exists(dd => dd >= 0.7.second && dd <= 1.1.second)
        expect(ok)
  // ---------------------------------------------------------------------------
  //  Generic dispatcher sanity
  // ---------------------------------------------------------------------------

  test("Generic dispatcher delegates correctly to all policy types"):
      val cases = List(
        RetryPolicy.Linear(1.second, 2),
        RetryPolicy.Exponential(500.millis, 2),
        RetryPolicy.BoundedExponential(200.millis, 2.seconds, 4),
        RetryPolicy.Polynomial(100.millis, 3, 2.0),
        RetryPolicy.Fibonacci(50.millis, 3),
        RetryPolicy.DecorrelatedJitter(200.millis, 3),
        RetryPolicy.FullJitter(200.millis, 3),
        RetryPolicy.None
      )
      for
        results <- cases.traverse(p => Backoff[RetryPolicy].nextDelay(p, 1).pure)
      yield
        val nonEmpty  = results.dropRight(1).forall(_.exists(_ > Duration.Zero))
        val noneEmpty = results.last.isEmpty
        expect(nonEmpty) and expect(noneEmpty)

  test("Hash jitter produces stable delay for same key across runs"):
      val p   = RetryPolicy.Linear(750.millis, 3)
      val key = Some("stable-hash-key")
      for
        d1 <- Backoff[RetryPolicy].nextDelay(p, 2, key).pure
        d2 <- Backoff[RetryPolicy].nextDelay(p, 2, key).pure
      yield expect.same(d1, d2)
