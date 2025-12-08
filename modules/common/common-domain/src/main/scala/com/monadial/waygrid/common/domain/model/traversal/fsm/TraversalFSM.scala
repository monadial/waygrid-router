//package com.monadial.waygrid.common.domain.model.traversal.fsm
//
//import cats.Applicative
//import cats.implicits.*
//import com.monadial.waygrid.common.domain.model.fsm.FSM
//import com.monadial.waygrid.common.domain.model.fsm.Value.Result
//import com.monadial.waygrid.common.domain.model.routing.Value
//import com.monadial.waygrid.common.domain.model.routing.Value.DeliveryStrategy.{ Immediate, ScheduleAfter, ScheduleAt }
//import com.monadial.waygrid.common.domain.model.routing.Value.TraversalId
//import com.monadial.waygrid.common.domain.model.traversal.dag.Dag
//import com.monadial.waygrid.common.domain.model.traversal.dag.Value.NodeId
//import com.monadial.waygrid.common.domain.model.traversal.fsm.TraversalEffect.{ DispatchNode, Schedule }
//import com.monadial.waygrid.common.domain.model.traversal.fsm.TraversalSignal.Begin
//import com.monadial.waygrid.common.domain.model.traversal.state.TraversalState
//
//import java.time.Instant
//import scala.annotation.nowarn
//
//object TraversalFSM:
//
//  @nowarn("msg=unused explicit parameter")
//  def stateless[F[+_]: {Applicative}](dag: Dag)
//    : FSM[F, TraversalState, TraversalSignal, TraversalError, TraversalEffect] =
//    FSM[F, TraversalState, TraversalSignal, TraversalError, TraversalEffect]: (state, signal) =>
//      Applicative[F]
//        .pure:
//          signal match
//            case Begin(traversalId) => onBegin(traversalId, state)
//            case _                  => fail(state, UnsupportedSignal(state.traversalId))
//
//    def onBegin(
//      traversalId: TraversalId,
//      state: TraversalState
//    ): Result[TraversalState, TraversalError, TraversalEffect] =
//      if dag.nodes.isEmpty then
//        Result(state, EmptyDag(traversalId).asLeft[TraversalEffect])
//      else if state.hasProgress then
//        Result(state, AlreadyInProgress(traversalId).asLeft[TraversalEffect])
//      else
//        dag.entryNode match
//          case None => Result(state, MissingEntryNode(traversalId).asLeft[TraversalEffect])
//          case Some(entry) =>
//            entry.deliveryStrategy match
//              case Immediate => ok(state.start(entry.id), DispatchNode(traversalId))
//              case ScheduleAfter(delay) =>
//                scheduleStartOrStart(state, entry.id, Instant.now().plusMillis(delay.toMillis))
//              case ScheduleAt(time) => scheduleStartOrStart(state, entry.id, time)
//
//    def scheduleStartOrStart(
//      state: TraversalState,
//      nodeId: NodeId,
//      scheduleAt: Instant
//    ): Result[TraversalState, TraversalError, TraversalEffect] =
//      if scheduleAt.isAfter(Instant.now()) then
//        ok(state.start(nodeId), DispatchNode(state.traversalId))
//      else
//        ok(state.schedule(nodeId, scheduleAt), Schedule(state.traversalId, scheduleAt))
//
//    def ok[E <: TraversalEffect](
//      state: TraversalState,
//      effect: E
//    ): Result[TraversalState, TraversalError, E] =
//      Result(state, effect.asRight[TraversalError])
//
//    def fail[E <: TraversalError](
//      state: TraversalState,
//      error: TraversalError
//    ): Result[TraversalState, E, TraversalEffect] =
//      Result(state, error.asLeft[TraversalEffect])
