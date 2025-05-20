package com.monadial.waygrid.common.application.interpreter

import cats.*
import cats.data.OptionT
import cats.effect.Resource
import cats.effect.std.Env
import cats.syntax.all.*
import com.monadial.waygrid.common.application.algebra.{ HasNode, SettingsLoader }
import com.typesafe.config.ConfigFactory
import io.circe.Decoder
import io.circe.config.syntax.CirceConfigOps

import java.io.File

object CirceSettingsLoaderInterpreter:
  def resource[F[+_]: {MonadThrow, Env}, A: Decoder]: Resource[F, SettingsLoader[F, A]] =
    Resource
      .pure:
        new SettingsLoader[F, A]:
          override def load(using HasNode[F]): F[A] =
            for
              thisNode        <- HasNode[F].get
              bundledSettings <- ConfigFactory.load().pure[F]
              envSettings <- OptionT(Env[F].get("WAYGRID_SETTINGS_PATH"))
                .filter(f => new File(f).exists)
                .map(f => ConfigFactory.parseFile(new File(f)))
                .getOrElse(ConfigFactory.empty())
              mergedSettings <- bundledSettings
                .withFallback(envSettings)
                .pure
                .flatMap: config =>
                  config.asF[F, A](thisNode.settingsPath.show)
            yield mergedSettings
