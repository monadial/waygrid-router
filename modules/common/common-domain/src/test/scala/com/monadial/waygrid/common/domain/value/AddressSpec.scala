package com.monadial.waygrid.common.domain.value

import cats.effect.IO
import cats.implicits.*
import com.monadial.waygrid.common.domain.model.node.Value.*
import com.monadial.waygrid.common.domain.value.Address.EndpointDirection.Inbound
import org.http4s.Uri
import weaver.SimpleIOSuite

object AddressSpec extends SimpleIOSuite:

  private def newNodeId: IO[NodeId] = NodeId.next[IO]

  private val descriptor = NodeDescriptor(
    NodeComponent.Destination,
    NodeService("websocket")
  )

  test("ServiceAddress.apply should build valid URI") {
    IO(Address.ServiceAddress(descriptor)).map { addr =>
      expect(addr.unwrap.scheme.exists(_.value == "waygrid")) and
        expect(addr.unwrap.authority.exists(_.host.value == "destination")) and
        expect.same(addr.unwrap.path.renderString, "/websocket")
    }
  }

  test("ServiceAddress.fromUri should parse valid URI") {
    for
      uri    <- IO(Uri.unsafeFromString("waygrid://destination/websocket"))
      parsed <- IO(Address.ServiceAddress.fromUri(uri))
    yield expect(parsed.isRight) and
      expect(parsed.exists(_.component == NodeComponent.Destination)) and
      expect(parsed.exists(_.service == NodeService("websocket")))
  }

  test("ServiceAddress.fromUri should fail on invalid scheme") {
    for
      uri    <- IO(Uri.unsafeFromString("http://destination/websocket"))
      result <- IO(Address.ServiceAddress.fromUri(uri))
    yield expect.same(result, Left(Address.AddressError.InvalidScheme))
  }

  test("NodeAddress.apply should include nodeId param") {
    for
      nodeId <- newNodeId
      addr   <- IO(Address.NodeAddress(descriptor, nodeId))
      query = addr.unwrap.query.params
    yield expect(query.contains("nodeId")) and expect.same(query("nodeId"), nodeId.show)
  }

  test("NodeAddress.fromUri should parse valid node address") {
    for
      nodeId <- newNodeId
      uri    <- IO(Uri.unsafeFromString(s"waygrid://destination/websocket?nodeId=${nodeId.show}"))
      parsed <- IO(Address.NodeAddress.fromUri(uri))
    yield expect(parsed.isRight) and expect(parsed.exists(_.nodeId == nodeId))
  }

  test("NodeAddress.fromUri should fail when nodeId missing") {
    for
      uri    <- IO(Uri.unsafeFromString("waygrid://destination/websocket"))
      result <- IO(Address.NodeAddress.fromUri(uri))
    yield expect.same(result, Left(Address.AddressError.MissingNodeId))
  }

  test("ServiceAddress.toEndpoint should produce LogicalEndpoint") {
    for
      addr     <- IO(Address.ServiceAddress(descriptor))
      endpoint <- IO(addr.toEndpoint(Inbound))
    yield expect.same(endpoint.descriptor, descriptor)
  }

  test("NodeAddress.toEndpoint should produce PhysicalEndpoint with same descriptor and nodeId") {
    for
      nodeId   <- newNodeId
      addr     <- IO(Address.NodeAddress(descriptor, nodeId))
      endpoint <- IO(addr.toEndpoint(Inbound))
    yield expect.same(endpoint.descriptor, descriptor) and expect.same(endpoint.nodeId, nodeId)
  }

  test("NodeAddress.fromString ⟶ toString ⟶ fromString should roundtrip") {
    for
      nodeId <- newNodeId
      addr   <- IO(Address.NodeAddress(descriptor, nodeId))
      str    <- IO(addr.unwrap.renderString)
      round  <- IO(Address.NodeAddress.fromString(str))
    yield expect(round.isRight) and expect(round.exists(_.nodeId == nodeId))
  }

  test("ServiceAddress.fromString ⟶ toString ⟶ fromString should roundtrip") {
    for
      addr   <- IO(Address.ServiceAddress(descriptor))
      str    <- IO(addr.unwrap.renderString)
      parsed <- IO(Address.ServiceAddress.fromString(str))
    yield expect(parsed.isRight) and expect(parsed.exists(_.service == descriptor.service))
  }
