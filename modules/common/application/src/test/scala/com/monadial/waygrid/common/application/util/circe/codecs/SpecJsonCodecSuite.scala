package com.monadial.waygrid.common.application.util.circe.codecs

import cats.effect.IO
import com.monadial.waygrid.common.application.util.circe.codecs.DomainRoutingSpecCodecs.given
import com.monadial.waygrid.common.domain.algebra.DagCompiler
import com.monadial.waygrid.common.domain.model.routing.Value.RepeatPolicy
import com.monadial.waygrid.common.domain.model.routing.Value.RouteSalt
import com.monadial.waygrid.common.domain.model.traversal.condition.Condition
import com.monadial.waygrid.common.domain.model.traversal.dag.JoinStrategy
import com.monadial.waygrid.common.domain.model.traversal.dag.Value.EdgeGuard
import com.monadial.waygrid.common.domain.model.traversal.spec.{ Node, Spec }
import com.monadial.waygrid.common.domain.value.Address.ServiceAddress
import io.circe.Json
import io.circe.parser.decode
import io.circe.syntax.*
import org.http4s.Uri
import weaver.SimpleIOSuite

object SpecJsonCodecSuite extends SimpleIOSuite:

  private val jsonForkJoinWithCondition =
    """
      |{
      |  "entryPoints": [
      |    {
      |      "type": "standard",
      |      "address": "waygrid://origin/entry",
      |      "retryPolicy": { "type": "None" },
      |      "deliveryStrategy": { "type": "Immediate" },
      |      "onSuccess": {
      |        "type": "fork",
      |        "address": "waygrid://processor/fork",
      |        "retryPolicy": { "type": "None" },
      |        "deliveryStrategy": { "type": "Immediate" },
      |        "branches": {
      |          "b": {
      |            "type": "standard",
      |            "address": "waygrid://processor/b",
      |            "retryPolicy": { "type": "None" },
      |            "deliveryStrategy": { "type": "Immediate" },
      |            "onSuccess": {
      |              "type": "join",
      |              "address": "waygrid://processor/join",
      |              "retryPolicy": { "type": "None" },
      |              "deliveryStrategy": { "type": "Immediate" },
      |              "joinNodeId": "join-1",
      |              "strategy": { "type": "And" },
      |              "timeout": null,
      |              "onSuccess": {
      |                "type": "standard",
      |                "address": "waygrid://destination/out",
      |                "retryPolicy": { "type": "None" },
      |                "deliveryStrategy": { "type": "Immediate" },
      |                "onSuccess": null,
      |                "onFailure": null,
      |                "onConditions": [],
      |                "label": "after"
      |              },
      |              "onFailure": null,
      |              "onTimeout": null,
      |              "label": "join"
      |            },
      |            "onFailure": null,
      |            "onConditions": [
      |              {
      |                "condition": { "type": "JsonEquals", "pointer": "/approved", "value": true },
      |                "to": {
      |                  "type": "standard",
      |                  "address": "waygrid://processor/yes",
      |                  "retryPolicy": { "type": "None" },
      |                  "deliveryStrategy": { "type": "Immediate" },
      |                  "onSuccess": null,
      |                  "onFailure": null,
      |                  "onConditions": [],
      |                  "label": "yes"
      |                }
      |              }
      |            ],
      |            "label": "b"
      |          },
      |          "c": {
      |            "type": "standard",
      |            "address": "waygrid://processor/c",
      |            "retryPolicy": { "type": "None" },
      |            "deliveryStrategy": { "type": "Immediate" },
      |            "onSuccess": {
      |              "type": "join",
      |              "address": "waygrid://processor/join",
      |              "retryPolicy": { "type": "None" },
      |              "deliveryStrategy": { "type": "Immediate" },
      |              "joinNodeId": "join-1",
      |              "strategy": { "type": "And" },
      |              "timeout": null,
      |              "onSuccess": {
      |                "type": "standard",
      |                "address": "waygrid://destination/out",
      |                "retryPolicy": { "type": "None" },
      |                "deliveryStrategy": { "type": "Immediate" },
      |                "onSuccess": null,
      |                "onFailure": null,
      |                "onConditions": [],
      |                "label": "after"
      |              },
      |              "onFailure": null,
      |              "onTimeout": null,
      |              "label": "join"
      |            },
      |            "onFailure": null,
      |            "onConditions": [],
      |            "label": "c"
      |          }
      |        },
      |        "joinNodeId": "join-1",
      |        "label": "fork"
      |      },
      |      "onFailure": null,
      |      "onConditions": [],
      |      "label": "entry"
      |    }
      |  ],
      |  "repeatPolicy": { "type": "NoRepeat" }
      |}
      |""".stripMargin

  private val jsonMultiEntry =
    """
      |{
      |  "entryPoints": [
      |    {
      |      "type": "standard",
      |      "address": "waygrid://origin/http",
      |      "retryPolicy": { "type": "None" },
      |      "deliveryStrategy": { "type": "Immediate" },
      |      "onSuccess": null,
      |      "onFailure": null,
      |      "onConditions": [],
      |      "label": "entry1"
      |    },
      |    {
      |      "type": "standard",
      |      "address": "waygrid://origin/kafka",
      |      "retryPolicy": { "type": "None" },
      |      "deliveryStrategy": { "type": "Immediate" },
      |      "onSuccess": null,
      |      "onFailure": null,
      |      "onConditions": [],
      |      "label": "entry2"
      |    }
      |  ],
      |  "repeatPolicy": { "type": "NoRepeat" }
      |}
      |""".stripMargin

  test("Spec JSON roundtrip supports fork/join and conditional edges"):
    val svcEntry = ServiceAddress(Uri.unsafeFromString("waygrid://origin/entry"))
    val svcFork  = ServiceAddress(Uri.unsafeFromString("waygrid://processor/fork"))
    val svcB     = ServiceAddress(Uri.unsafeFromString("waygrid://processor/b"))
    val svcC     = ServiceAddress(Uri.unsafeFromString("waygrid://processor/c"))
    val svcJoin  = ServiceAddress(Uri.unsafeFromString("waygrid://processor/join"))
    val svcAfter = ServiceAddress(Uri.unsafeFromString("waygrid://destination/out"))
    val svcYes   = ServiceAddress(Uri.unsafeFromString("waygrid://processor/yes"))

    val joinNodeId = "join-1"

    val after = Node.standard(svcAfter)
    val join  = Node.join(svcJoin, joinNodeId, JoinStrategy.And, onSuccess = Some(after))

    val yes = Node.standard(svcYes, onSuccess = Some(join))
    val b = Node.Standard(
      address = svcB,
      retryPolicy = com.monadial.waygrid.common.domain.model.resiliency.RetryPolicy.None,
      deliveryStrategy = com.monadial.waygrid.common.domain.model.routing.Value.DeliveryStrategy.Immediate,
      onSuccess = Some(join),
      onFailure = None,
      onConditions = List(Node.when(Condition.JsonEquals("/approved", Json.fromBoolean(true)), yes)),
      label = Some("b-router")
    )

    val c = Node.standard(svcC, onSuccess = Some(join))
    val fork = Node.fork(
      address = svcFork,
      branches = Map("b" -> b, "c" -> c),
      joinNodeId = joinNodeId
    )

    val entry = Node.standard(svcEntry, onSuccess = Some(fork))
    val spec  = Spec.single(entry, RepeatPolicy.NoRepeat)

    val json = spec.asJson
    val decoded = json.as[Spec]

    IO.pure(expect(decoded == Right(spec)) && expect(json.hcursor.downField("entryPoints").succeeded))

  test("Decode Spec from JSON (fork/join + conditional)"):
    IO.pure(decode[Spec](jsonForkJoinWithCondition)).map { decoded =>
      decoded match
        case Left(err) => failure(s"Expected successful decode, got: $err")
        case Right(spec) =>
          expect(spec.entryPoints.length == 1) &&
          expect(spec.repeatPolicy == RepeatPolicy.NoRepeat) &&
          expect(spec.entryPoints.head.isInstanceOf[Node.Standard])
    }

  test("Decode Spec from JSON (multi-entry)"):
    IO.pure(decode[Spec](jsonMultiEntry)).map { decoded =>
      decoded match
        case Left(err) => failure(s"Expected successful decode, got: $err")
        case Right(spec) =>
          expect(spec.entryPoints.length == 2) &&
          expect(spec.entryPoints.toList.collect { case s: Node.Standard => s }.length == 2)
    }

  test("Decode Spec from JSON rejects empty entryPoints"):
    val json =
      """
        |{
        |  "entryPoints": [],
        |  "repeatPolicy": { "type": "NoRepeat" }
        |}
        |""".stripMargin
    IO.pure(expect(decode[Spec](json).isLeft))

  test("Compile decoded Spec JSON into Dag (fork/join + conditional)"):
    DagCompiler.default[IO].use { compiler =>
      decode[Spec](jsonForkJoinWithCondition) match
        case Left(err) => IO.pure(failure(s"decode failed: $err"))
        case Right(spec) =>
          compiler.compile(spec, RouteSalt("json-salt")).map { dag =>
            val svcFork  = ServiceAddress(Uri.unsafeFromString("waygrid://processor/fork"))
            val svcJoin  = ServiceAddress(Uri.unsafeFromString("waygrid://processor/join"))
            val svcB     = ServiceAddress(Uri.unsafeFromString("waygrid://processor/b"))
            val svcYes   = ServiceAddress(Uri.unsafeFromString("waygrid://processor/yes"))

            val forkNode = dag.nodes.values.find(_.address == svcFork)
            val joinNode = dag.nodes.values.find(_.address == svcJoin)
            val bNode    = dag.nodes.values.find(_.address == svcB)
            val yesNode  = dag.nodes.values.find(_.address == svcYes)

            val hasConditionalEdge =
              (bNode, yesNode) match
                case (Some(b), Some(y)) =>
                  dag.edges.exists(e =>
                    e.from == b.id &&
                      e.to == y.id &&
                      (e.guard match
                        case EdgeGuard.Conditional(Condition.JsonEquals("/approved", Json.True)) => true
                        case _                                                                  => false
                      )
                  )
                case _ => false

            val forkJoinIdsMatch =
              (forkNode, joinNode) match
                case (Some(f), Some(j)) => expect.same(f.forkId, j.forkId)
                case _                  => failure("expected fork and join nodes present")

            forkJoinIdsMatch && expect(hasConditionalEdge)
          }
    }

  test("Compile decoded Spec JSON into Dag (multi-entry)"):
    DagCompiler.default[IO].use { compiler =>
      decode[Spec](jsonMultiEntry) match
        case Left(err) => IO.pure(failure(s"decode failed: $err"))
        case Right(spec) =>
          compiler.compile(spec, RouteSalt("json-multi")).map { dag =>
            expect(dag.entryPoints.length == 2)
          }
    }
