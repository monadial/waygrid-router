package com.monadial.waygrid.common.application.util.otel4s

import org.http4s.{ Header, Headers }
import org.typelevel.ci.CIString
import org.typelevel.otel4s.context.propagation.{ TextMapGetter, TextMapUpdater }

object Htt4sHeadersSyntax:

  given TextMapGetter[Headers] = new TextMapGetter[Headers]:
    override def get(carrier: Headers, key: String): Option[String] =
      carrier.get(CIString(key)).map(_.head.value)

    override def keys(carrier: Headers): Iterable[String] =
      carrier.headers.map(_.name.toString)

  given TextMapUpdater[Headers] =
    (carrier: Headers, key: String, value: String) =>
      carrier.put(Header.Raw(CIString(key), value))
