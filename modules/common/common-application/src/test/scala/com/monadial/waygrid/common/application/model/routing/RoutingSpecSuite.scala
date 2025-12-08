//package com.monadial.waygrid.common.application.model.routing
//
//import cats.effect.IO
//import com.monadial.waygrid.common.domain.model.node.Value.ServiceAddress
//import com.monadial.waygrid.common.domain.model.routing.Value.DeliveryStrategy.Immediate
//import com.monadial.waygrid.common.domain.model.routing.Value.RepeatPolicy.NoRepeat
//import com.monadial.waygrid.common.domain.model.routing.Value.RetryPolicy.NoRetry
//import com.monadial.waygrid.common.domain.model.routing.spec.{Node, Spec}
//import com.monadial.waygrid.common.application.util.circe.codecs.DomainRoutingSpecCodecs.given
//import io.circe.syntax.*
//import weaver.SimpleIOSuite
//import weaver.scalacheck.Checkers
//
//object RoutingSpecSuite extends SimpleIOSuite with Checkers:
//
//  test("serialize and deserialize simple routingSpec") {
//    for
//      spec <- IO.pure(Spec(
//        Node(
//          ServiceAddress.fromString("waygrid://test/service"),
//          NoRetry,
//          Immediate,
//          None,
//          None,
//          None,
//        ),
//        NoRepeat
//      ))
//      encodedSpec <- IO.pure(spec.asJson)
//      decodedSpec <- IO.pure(encodedSpec.as[Spec])
//    yield expect(decodedSpec.isRight) && expect(decodedSpec.toOption.get == spec)
//  }
