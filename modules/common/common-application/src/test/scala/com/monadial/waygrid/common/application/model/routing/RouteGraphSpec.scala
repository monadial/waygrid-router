package com.monadial.waygrid.common.application.model.routing

import cats.effect.IO
import cats.implicits.*
import com.monadial.waygrid.common.domain.model.Waygrid.Address
import com.monadial.waygrid.common.domain.model.routing.Value.*
import com.monadial.waygrid.common.domain.model.routing.{RouteGraph, RouteNode}
import com.monadial.waygrid.common.domain.value.string.StringValue
import io.circe.parser.decode
import io.circe.syntax.*
import scodec.Attempt.{Failure, Successful}
import scodec.Codec
import weaver.SimpleIOSuite

import scala.concurrent.duration.*

object RouteGraphSpec extends SimpleIOSuite:

  type NameId = NameId.Type
  object NameId extends StringValue

  type Name = Name.Type
  object Name extends StringValue

  type Home = Home.Type
  object Home extends StringValue

  final case class Data(id: NameId, name: Name, home: Home)

  test("RouteGraph should be serializable to scodec and deserializable back to RouteGraph") {
    given Codec[Data] = Codec.derived[Data]

    val routeGraph = Data(
      id = NameId("1234"),
      name = Name("Tomas"),
      home = Home("Bratislava")
    )

    Codec[Data].encode(routeGraph) match
      case Failure(cause) =>
        IO.raiseError(new Exception(s"Encoding failed: ${cause.message}"))
      case Successful(bits) =>
        Codec[Data].decode(bits) match
          case Failure(cause) =>
            IO.raiseError(new Exception(s"Decoding failed: ${cause.message}, ${cause.context}"))
          case Successful(result) =>
            IO(expect.same(routeGraph, result.value))
  }

  test("RouteGraph should be serializable to JSON and deserializable back to RouteGraph"):
      for
        routeGraph <- RouteGraph(
          entryPoint = RouteNode(
            address = Address("waygrid://test/route-node"),
            retryPolicy = RetryPolicy.Exponential(
              baseDelay = 1.second,
              maxRetries = RetryMaxRetries(10)
            ),
            deliveryStrategy = DeliveryStrategy.ScheduleAfter(delay = 5.seconds),
            onFailure = Option(
              RouteNode(
                address = Address("waygrid://test/on-failure"),
                retryPolicy = RetryPolicy.NoRetry,
                deliveryStrategy = DeliveryStrategy.Immediate,
                onFailure = None,
                onSuccess = None
              )
            ),
            onSuccess = Option(
              RouteNode(
                address = Address("waygrid://test/on-success"),
                retryPolicy = RetryPolicy.NoRetry,
                deliveryStrategy = DeliveryStrategy.Immediate,
                onFailure = None,
                onSuccess = None
              )
            )
          )
        ).pure[IO]
        encodedRouteGraph <- routeGraph
          .asJson
          .dropEmptyValues
          .deepDropNullValues
          .noSpaces
          .pure[IO]
        decodedRouteGraph <- IO.fromEither(decode[RouteGraph](encodedRouteGraph))
      yield expect(routeGraph == decodedRouteGraph)
