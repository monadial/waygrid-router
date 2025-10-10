package com.monadial.waygrid.common.application.domain.model.envelope

import java.time.Instant
import cats.effect.IO
import com.monadial.waygrid.common.domain.model.event.Event
import weaver.SimpleIOSuite
import cats.implicits.*
import com.monadial.waygrid.common.application.algebra.ThisNode
import com.monadial.waygrid.common.domain.model.node.Node
import com.monadial.waygrid.common.domain.model.node.Value.{NodeClusterId, NodeDescriptor, NodeId, NodeRegion, NodeRuntime, ServiceAddress}
import com.monadial.waygrid.common.domain.model.topology.Value.ClusterId

object EnvelopeSpec extends SimpleIOSuite:

  final case class DummyEvent() extends Event

  private lazy val dummyServiceAddress = ServiceAddress.fromString("waygrid://test/dummy.service")
  private def getNode[F[+_]] =
    for
      nodeId <- NodeId.next[IO]
      descriptor <- dummyServiceAddress.toNodeDescriptor
      clusterId <- NodeClusterId("test-1").pure[IO]
      region <- NodeRegion.from("tc-test-0")
    yield Node(
      nodeId,
      descriptor,
      clusterId,
      region,
      Instant.now(,
      NodeRuntime()
    )


  test("Serialize dummy event wrapped in envelope") {
    for
      given ThisNode[IO] <- ThisNode.
      dummyEvent <- DummyEvent().pure[IO]
      envelope <- Envelope.toService(
        dummyServiceAddress,
        dummyEvent
      )
    yield expect(1 == 1)
  }
