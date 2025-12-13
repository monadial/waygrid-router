//package com.monadial.waygrid.common.application.domain.model.envelope
//
//import cats.effect.{Clock, IO}
//import cats.implicits.*
//import com.monadial.waygrid.common.application.algebra.ThisNode
//import com.monadial.waygrid.common.domain.algebra.messaging.event.Event
//import com.monadial.waygrid.common.domain.model.envelope.DomainEnvelope
//import com.monadial.waygrid.common.domain.model.envelope.Value.{EnvelopeId, SentAt}
//import com.monadial.waygrid.common.domain.model.node.Node
//import com.monadial.waygrid.common.domain.model.node.Value.*
//import com.monadial.waygrid.common.domain.value.Address.ServiceAddress
//import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
//import io.circe.syntax.EncoderOps
//import scodec.Encoder
//import weaver.SimpleIOSuite
//import io.circe.generic.semiauto
//
//import java.time.Instant
//
//object EnvelopeSpec extends SimpleIOSuite:
//
//  final case class DummyEvent() extends Event
//  object DummyEvent:
//    given Encoder[DummyEvent] = semiauto.deriveEncoder
//
//  private lazy val dummyServiceAddress = ServiceAddress(NodeDescriptor(NodeComponent("test"), NodeService("test")))
//
//  private lazy val thisNode = new ThisNode[IO]:
//    override def get: IO[Node] =
//      for
//        nodeId <- NodeId.next[IO]
//        now <- Clock[IO].realTimeInstant
//        descriptor <- dummyServiceAddress.toDescriptor.pure[IO]
//        clusterId <- NodeClusterId("test-1").pure[IO]
//        region <- NodeRegion.from("tc-test-0").toOption.get.pure[IO]
//      yield Node(
//        nodeId,
//        descriptor,
//        clusterId,
//        region,
//        now,
//        NodeRuntime()
//      )
//
//  test("Serialize dummy event wrapped in envelope") {
//    for
//      given ThisNode[IO] <- thisNode.pure[IO]
//      dummyEvent <- DummyEvent().pure[IO]
//      envelopeId <- EnvelopeId.next[IO]
//      nodeAddress <- ThisNode[IO].get.map(_.address)
//      envelope <- DomainEnvelope[DummyEvent](
//        envelopeId,
//        nodeAddress,
//        dummyEvent
//      ).addStamp(SentAt(nodeAddress, Instant.now())).pure[IO]
//      serialized <- envelope.asJson.pure[IO]
//    yield expect(1 == 1)
//  }
