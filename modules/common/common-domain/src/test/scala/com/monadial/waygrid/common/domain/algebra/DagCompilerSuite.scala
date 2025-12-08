//package com.monadial.waygrid.common.domain.algebra
//
//import cats.effect.IO
//import com.monadial.waygrid.common.domain.model.node.Value.{ NodeDescriptor, NodeService, ServiceAddress }
//import com.monadial.waygrid.common.domain.model.routing.Value.RepeatPolicy.NoRepeat
//import com.monadial.waygrid.common.domain.model.routing.Value.{ DeliveryStrategy, RetryPolicy, RouteSalt }
//import com.monadial.waygrid.common.domain.model.routing.spec.{ Node, Spec }
//import weaver.*
//import weaver.scalacheck.Checkers
//
//object DagCompilerSuite extends SimpleIOSuite with Checkers:
//
//  test("compile a route spec into a route dag") {
//    DagCompiler.default[F].use { compiler =>
//      for
//        spec <- IO.pure(Spec(
//          Node(
//            ServiceAddress.fromNodeDescriptor(NodeDescriptor.Destination(NodeService("service-A"))),
//            RetryPolicy.NoRetry,
//            DeliveryStrategy.Immediate,
//            Some(Node(
//              ServiceAddress.fromNodeDescriptor(NodeDescriptor.Destination(NodeService("service-A"))),
//              RetryPolicy.NoRetry,
//              DeliveryStrategy.Immediate,
//              Some(Node(
//                ServiceAddress.fromNodeDescriptor(NodeDescriptor.Destination(NodeService("service-A"))),
//                RetryPolicy.NoRetry,
//                DeliveryStrategy.Immediate,
//                None,
//                Some(Node(
//                  ServiceAddress.fromNodeDescriptor(NodeDescriptor.Destination(NodeService("service-A"))),
//                  RetryPolicy.NoRetry,
//                  DeliveryStrategy.Immediate,
//                  None,
//                  None
//                ))
//              )),
//              None
//            )),
//            Some(Node(
//              ServiceAddress.fromNodeDescriptor(NodeDescriptor.Destination(NodeService("service-A"))),
//              RetryPolicy.NoRetry,
//              DeliveryStrategy.Immediate,
//              Some(Node(
//                ServiceAddress.fromNodeDescriptor(NodeDescriptor.Destination(NodeService("service-A"))),
//                RetryPolicy.NoRetry,
//                DeliveryStrategy.Immediate,
//                Some(Node(
//                  ServiceAddress.fromNodeDescriptor(NodeDescriptor.Destination(NodeService("service-A"))),
//                  RetryPolicy.NoRetry,
//                  DeliveryStrategy.Immediate,
//                  None,
//                  None
//                )),
//                Some(Node(
//                  ServiceAddress.fromNodeDescriptor(NodeDescriptor.Destination(NodeService("service-A"))),
//                  RetryPolicy.NoRetry,
//                  DeliveryStrategy.Immediate,
//                  Some(Node(
//                    ServiceAddress.fromNodeDescriptor(NodeDescriptor.Destination(NodeService("service-A"))),
//                    RetryPolicy.NoRetry,
//                    DeliveryStrategy.Immediate,
//                    None,
//                    None
//                  )),
//                  Some(Node(
//                    ServiceAddress.fromNodeDescriptor(NodeDescriptor.Destination(NodeService("service-A"))),
//                    RetryPolicy.NoRetry,
//                    DeliveryStrategy.Immediate,
//                    None,
//                    None
//                  ))
//                ))
//              )),
//              Some(Node(
//                ServiceAddress.fromNodeDescriptor(NodeDescriptor.Destination(NodeService("service-A"))),
//                RetryPolicy.NoRetry,
//                DeliveryStrategy.Immediate,
//                None,
//                None
//              ))
//            ))
//          ),
//          NoRepeat
//        ))
//        compiled <- compiler.compile(spec, RouteSalt("test-salt"))
//      yield expect(true)
//    }
//  }
