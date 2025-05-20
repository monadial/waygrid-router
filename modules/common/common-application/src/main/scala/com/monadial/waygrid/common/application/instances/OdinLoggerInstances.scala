package com.monadial.waygrid.common.application.instances

import io.circe.{ Decoder, Encoder }
import io.odin.Level
import io.odin.Level.{ Debug, Error, Info, Trace, Warn }

object OdinLoggerInstances:
  given Encoder[Level] = Encoder
    .encodeString
    .contramap:
      case Trace => "TRACE"
      case Debug => "DEBUG"
      case Info  => "INFO"
      case Warn  => "WARN"
      case Error => "ERROR"

  given Decoder[Level] = Decoder
    .decodeString
    .map(_.toUpperCase)
    .emap:
      case "TRACE" => Right(Trace)
      case "DEBUG" => Right(Debug)
      case "INFO"  => Right(Info)
      case "WARN"  => Right(Warn)
      case "ERROR" => Right(Error)
      case other   => Left(s"Unknown log level: $other")
