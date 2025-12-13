package com.monadial.waygrid.system.blob.store.actor

import com.monadial.waygrid.common.application.algebra.{ReplyingSupervisedActor, ReplyingSupervisedActorRef}

trait StorageManagerRequest
trait StorageManagerResponse

type StorageManagerActor[F[+_]] = ReplyingSupervisedActor[F, StorageManagerRequest, StorageManagerResponse]
type StorageManagerActorRef[F[+_]] = ReplyingSupervisedActorRef[F, StorageManagerRequest, StorageManagerResponse]

object StorageManagerActor
