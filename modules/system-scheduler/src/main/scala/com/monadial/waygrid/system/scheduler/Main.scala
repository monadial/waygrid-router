package com.monadial.waygrid.system.scheduler

import cats.effect.{IO, IOApp}

object Main extends IOApp.Simple:
  def run: IO[Unit] = IO(println("Waygrid Service Archive!"))
