package com.monadial.waygrid.common.domain.model.event

type EventHandler[F[+_], E <: Event] = E => F[Unit]

trait Event
