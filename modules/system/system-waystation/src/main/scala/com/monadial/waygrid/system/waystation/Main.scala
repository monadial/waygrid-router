package com.monadial.waygrid.system.waystation

import cats.effect.{IO, IOApp}

object Main extends IOApp.Simple:
  def run: IO[Unit] = IO(println("Waygrid System Waystation!"))
