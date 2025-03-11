package com.monadial.waygrid.origin.http

import cats.effect.{IO, IOApp}

object Main extends IOApp.Simple:
  def run: IO[Unit] = IO(println("Waygrid Origin Http!"))
