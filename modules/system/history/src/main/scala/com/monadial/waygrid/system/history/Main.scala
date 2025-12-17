package com.monadial.waygrid.system.history

import cats.effect.{ IO, IOApp }

object Main extends IOApp.Simple:
  def run: IO[Unit] = IO(println("Waygrid Service Scheduler!"))
