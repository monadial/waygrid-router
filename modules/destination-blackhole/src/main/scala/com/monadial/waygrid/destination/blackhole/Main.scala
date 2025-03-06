package com.monadial.waygrid.destination.blackhole

import cats.effect.{IO, IOApp}

object Main extends IOApp.Simple:
  def run: IO[Unit] = IO(println("Waygrid Destination Webhook"))
