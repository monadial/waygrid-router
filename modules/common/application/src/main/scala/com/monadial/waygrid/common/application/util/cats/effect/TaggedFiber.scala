package com.monadial.waygrid.common.application.util.cats.effect

import cats.effect.kernel.{Fiber, Outcome}
import cats.effect.{GenSpawn, MonadCancel}

type FiberTUnit[F[+_], T <: FiberType] = FiberT[F, T, Unit]

trait FiberType

/**
 * Wrapper for Fiber[F, Throwable, A]
 *
 * This wrapper adds type information to the fiber
 */
final case class FiberT[F[+_], T <: FiberType, A](fiber: Fiber[F, Throwable, A]):

  def cancel: F[Unit] = fiber.cancel

  def join: F[Outcome[F, Throwable, A]] = fiber.join

  def joinWith(onCancel: F[A])(using F: MonadCancel[F, Throwable]): F[A] = fiber.joinWith(onCancel)

  def joinWithNever(using F: GenSpawn[F, Throwable]): F[A] = fiber.joinWithNever

  def joinWithUnit(using F: MonadCancel[F, Throwable], ev: Unit <:< A): F[A] = fiber.joinWithUnit
