package com.monadial.waygrid.common.application.algebra

trait SettingsLoader[F[+_], A]:
  def load(using ThisNode[F]): F[A]
