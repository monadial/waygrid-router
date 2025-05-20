package com.monadial.waygrid.common.application.algebra

trait TopologyAwareRequest extends SupervisedRequest

type TopologyAwareActor[F[+_]]    = SupervisedActor[F, TopologyAwareRequest]
type TopologyAwareActorRef[F[+_]] = SupervisedActorRef[F, TopologyAwareRequest]
