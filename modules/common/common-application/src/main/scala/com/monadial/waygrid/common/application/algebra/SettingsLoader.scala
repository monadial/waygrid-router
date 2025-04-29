package com.monadial.waygrid.common.application.algebra

trait SettingsLoader[F[+_], A]:
  def load(using HasNode[F]): F[A]
