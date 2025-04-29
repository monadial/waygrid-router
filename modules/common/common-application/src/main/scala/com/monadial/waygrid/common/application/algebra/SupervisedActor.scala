package com.monadial.waygrid.common.application.algebra

import com.suprnation.actor.{ReplyingActor, ReplyingActorRef}

trait SupervisedRequest
trait SupervisedResponse

object SupervisedRequest:
  case object Start extends SupervisedRequest
  case object Stop extends SupervisedRequest
  case object Restart extends SupervisedRequest

object SupervisedResponse:
  case object Started extends SupervisedResponse
  case object Stopped extends SupervisedResponse
  case object Restarted extends SupervisedResponse

type SupervisedActor[F[+_], Req] = ReplyingActor[F, Req | SupervisedRequest, Any]
type SupervisedActorRef[F[+_], Req] = ReplyingActorRef[F, Req | SupervisedRequest, Any]
