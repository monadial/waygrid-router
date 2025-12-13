//package com.monadial.waygrid.common.application.model.routing
//
//import cats.effect.IO
//import cats.implicits.*
//import com.monadial.waygrid.common.domain.model.Waygrid.Address
//import com.monadial.waygrid.common.domain.model.routing.RouteNode
//import com.monadial.waygrid.common.domain.model.routing.Value.{DeliveryStrategy, RetryMaxRetries, RetryPolicy}
//import io.circe.parser.decode
//import io.circe.syntax.*
//import weaver.SimpleIOSuite
//
//import scala.concurrent.duration.*
//
//object RouteNodeSpec extends SimpleIOSuite:
//
//  test("RouteNode should be serializable to JSON and deserializable back to RouteNode"):
//      for
//        routingNode <- RouteNode(
//          address = Address("waygrid://test/route-node"),
//          retryPolicy = RetryPolicy.Exponential(
//            baseDelay = 1.second,
//            maxRetries = RetryMaxRetries(10)
//          ),
//          deliveryStrategy = DeliveryStrategy.ScheduleAfter(delay = 5.seconds),
//          Some(
//            RouteNode(
//              address = Address("waygrid://test/on-failure"),
//              retryPolicy = RetryPolicy.NoRetry,
//              deliveryStrategy = DeliveryStrategy.Immediate,
//              onFailure = Option(
//                RouteNode(
//                  address = Address("waygrid://test/on-failure"),
//                  retryPolicy = RetryPolicy.NoRetry,
//                  deliveryStrategy = DeliveryStrategy.Immediate,
//                  onFailure = None,
//                  onSuccess = None
//                )
//              ),
//              onSuccess = Option(
//                RouteNode(
//                  address = Address("waygrid://test/on-success"),
//                  retryPolicy = RetryPolicy.NoRetry,
//                  deliveryStrategy = DeliveryStrategy.Immediate,
//                  onFailure = None,
//                  onSuccess = None
//                )
//              )
//            )
//          ),
//          None
//        ).pure[IO]
//        decodedRoutingNode <- routingNode
//          .asJson
//          .deepDropNullValues
//          .spaces2
//          .pure[IO]
//        encodedRouteingNode <- IO.fromEither(
//          decode[RouteNode](decodedRoutingNode)
//        )
//      yield expect(routingNode == encodedRouteingNode)
