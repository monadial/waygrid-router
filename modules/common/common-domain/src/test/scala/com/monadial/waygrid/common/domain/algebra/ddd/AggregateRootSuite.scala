package com.monadial.waygrid.common.domain.algebra.ddd

import cats.implicits.*
import com.monadial.waygrid.common.domain.model.command.Command
import com.monadial.waygrid.common.domain.model.event.Event
import com.monadial.waygrid.common.domain.value.ulid.ULIDValue
import weaver.SimpleIOSuite
import weaver.scalacheck.Checkers

object AggregateRootSuite extends SimpleIOSuite with Checkers:

  type DummyId = DummyId.Type
  object DummyId extends ULIDValue

  sealed trait DummyCommand           extends Command
  case class DoSomething(id: DummyId) extends DummyCommand
  case object DoNothing               extends DummyCommand
  case object Increment               extends DummyCommand

  sealed trait DummyEvent       extends Event
  case object SomethingDone     extends DummyEvent
  case object SomethingWontDone extends DummyEvent
  case object Incremented       extends DummyEvent

  final case class DummyState(
    id: DummyId,
    counter: Int = 0,
    version: Version
  ) extends AggregateRootState[DummyId, DummyState]:
    override def bump: DummyState = copy(version = version.bump)
  object DummyState:
    def initial(id: DummyId): DummyState = DummyState(id, 0, Version.initial)

  object DummyAggregate extends AggregateRoot[DummyId, DummyState, DummyCommand, DummyEvent]:
    private val doSomething = Handler.fromPartial[DummyId, DummyState, DummyCommand, DummyEvent]:
        case (DoSomething(id), None) =>
          AggregateHandlerResult.withEvent(DummyState.initial(id), SomethingDone)
        case (DoSomething(id), Some(_)) =>
          AggregateHandlerResult.withEvent(DummyState.initial(id), SomethingWontDone)

    private val increment = Handler.fromPartial[DummyId, DummyState, DummyCommand, DummyEvent]:
        case (Increment, Some(state)) =>
          AggregateHandlerResult.withEvent(state.copy(counter = state.counter + 1), Incremented)

    override def handlers: List[Handler[DummyId, DummyState, DummyCommand, DummyEvent]] =
      List(increment, doSomething)

  test("AggregateHandlerResult.withEvent creates result with event"):
      for
        id     <- DummyId.next
        state  <- DummyState.initial(id).pure
        event  <- SomethingDone.pure
        result <- AggregateHandlerResult.withEvent(state, event).pure
      yield expect.same(result.state, state.bump) and expect(result.events.contains(event))

  test("Handler.fromPartial runs when PF is defined"):
      for
        id    <- DummyId.next
        state <- DummyState.initial(id).pure
        handler <- Handler
          .fromPartial[DummyId, DummyState, DummyCommand, DummyEvent]:
            case (DoSomething(id), None) =>
              AggregateHandlerResult.withEvent(
                state,
                SomethingDone
              )
          .pure
        result <- handler.run((DoSomething(id), None)).pure
      yield expect(result.exists(_.events == List(SomethingDone)))

  test("Handler.fromPartial returns HandlerNotFound when PF not defined"):
      for
        id    <- DummyId.next
        state <- DummyState.initial(id).pure
        handler <- Handler
          .fromPartial[DummyId, DummyState, DummyCommand, DummyEvent]:
            case (DoSomething(id), None) =>
              AggregateHandlerResult.withEvent(
                state,
                SomethingDone
              )
          .pure
        result <- handler.run((DoNothing, None)).pure
      yield expect:
          result match
            case Left(AggregateHandlerError.HandlerNotFound(_)) => true
            case _                                              => false

  test("AggregateRoot.handle picks up defined handler"):
      for
        id     <- DummyId.next
        result <- DummyAggregate.handle(DoSomething(id), None).pure
      yield expect:
          result match
            case Right(r) => r.events == List(SomethingDone)
            case _        => false

  test("Create initializes state correctly"):
      for
        id     <- DummyId.next
        result <- DummyAggregate.handle(DoSomething(id), None).pure
      yield expect:
          result match
            case Right(r) => r.state.id == id && r.state.counter == 0 && r.state.version.unwrap == 1
            case _        => false

  test("Increment mutates state consistently"):
      for
        id     <- DummyId.next
        state  <- DummyState(id, 5, Version(6)).some.pure
        result <- DummyAggregate.handle(Increment, state).pure
      yield expect(result.exists(_.state.counter == 6)) and
        expect(result.exists(_.state.version.unwrap == 7)) and
        expect(result.exists(_.events.contains(Incremented)))

  test("Increment fails when no state exists"):
      for
        id     <- DummyId.next
        result <- DummyAggregate.handle(Increment, None).pure
      yield expect:
          result match
            case Left(AggregateHandlerError.HandlerNotFound(_)) => true
            case _                                              => false

  test("Create initializes new state"):
      for
        id     <- DummyId.next
        result <- DummyAggregate.handle(DoSomething(id), None).pure
      yield expect:
          result match
            case Right(r) => r.state.version.unwrap == 1 && r.events.contains(SomethingDone)
            case _        => false

  test("Increment increases counter and version"):
      for
        id     <- DummyId.next
        state  <- DummyState.initial(id).copy(counter = 1).some.pure
        result <- DummyAggregate.handle(Increment, state).pure
      yield expect:
          result match
            case Right(r) => r.state.counter == 2 && r.state.version.unwrap == 1
            case _        => false

  test("Multiple increments evolve deterministically"):
      for
        id   <- DummyId.next
        init <- DummyState.initial(id).some.pure
        r1   <- DummyAggregate.handle(Increment, init).pure
        r2   <- r1.flatMap(r => DummyAggregate.handle(Increment, Some(r.state))).pure
        r3   <- r2.flatMap(r => DummyAggregate.handle(Increment, Some(r.state))).pure
      yield expect(
        r3 match
          case Right(r) => r.state.counter == 3 && r.state.version.unwrap == 3 && r.events.contains(Incremented)
          case _        => false
      )

  test("DoNothing always returns HandlerNotFound"):
      for
        id     <- DummyId.next
        result <- DummyAggregate.handle(DoNothing, None).pure
      yield expect:
          result match
            case Left(AggregateHandlerError.HandlerNotFound(_)) => true
            case _                                              => false

  test("Version increases monotonically"):
      for
        id   <- DummyId.next
        init <- DummyState.initial(id).pure
        commands = List.fill(5)(Increment)
        results =
          commands.scanLeft(Right(AggregateHandlerResult(init, Nil)): AggResult[DummyId, DummyState, DummyEvent]):
              case (Right(r), cmd)  => DummyAggregate.handle(cmd, Some(r.state))
              case (l @ Left(_), _) => l
        versions = results.collect { case Right(r) => r.state.version }
      yield expect(versions == versions.sorted.distinct)

  test("Idempotency: same command on same state should not mutate again"):
      for
        id     <- DummyId.next
        init   <- DummyState.initial(id).some.pure
        first  <- DummyAggregate.handle(Increment, init).pure
        second <- DummyAggregate.handle(Increment, init).pure
      yield expect:
          first.exists(_.state.counter == 1) &&
          second.exists(_.state.counter == 1) // version may diverge, but counter consistent

  test("Create twice on same id fails with AlreadyExists"):
      for
        id     <- DummyId.next
        first  <- DummyAggregate.handle(DoSomething(id), None).pure
        second <- DummyAggregate.handle(DoSomething(id), first.toOption.map(_.state)).pure
      yield expect(
        second match
          case Right(r) => r.events.contains(SomethingWontDone)
          case _        => false
      )

  test("Events accumulate across multiple commands"):
      for
        id        <- DummyId.next
        created   <- DummyAggregate.handle(DoSomething(id), None).pure
        increment <- created.flatMap(r => DummyAggregate.handle(Increment, Some(r.state))).pure
        combined =
          for
            c <- created
            i <- increment
          yield AggregateHandlerResult.combine(c, i)
      yield expect(
        combined match
          case Right(r) => r.events.contains(SomethingDone) && r.events.contains(Incremented)
          case _        => false
      )

  test("AlreadyExists does not mutate state"):
      for
        id      <- DummyId.next
        created <- DummyAggregate.handle(DoSomething(id), None).pure
        second  <- DummyAggregate.handle(DoSomething(id), created.toOption.map(_.state)).pure
      yield expect(
        second match
          case Right(r) => r.events.contains(SomethingWontDone) && second.exists(
              _.state.version.unwrap == 1
            ) && created.exists(_.state.version.unwrap == 1)
          case _ => false
      )

  test("Aggregate with empty handlers always returns HandlerNotFound"):
      object EmptyAggregate extends AggregateRoot[DummyId, DummyState, DummyCommand, DummyEvent]:
        override def handlers: List[Handler[DummyId, DummyState, DummyCommand, DummyEvent]] = Nil

      for
        id     <- DummyId.next
        result <- EmptyAggregate.handle(DoSomething(id), None).pure
      yield expect(
        result match
          case Left(AggregateHandlerError.HandlerNotFound(_)) => true
          case _                                              => false
      )
