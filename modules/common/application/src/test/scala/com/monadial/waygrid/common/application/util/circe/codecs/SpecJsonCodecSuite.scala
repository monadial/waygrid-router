package com.monadial.waygrid.common.application.util.circe.codecs

import cats.effect.IO
import com.monadial.waygrid.common.application.util.circe.codecs.DomainRoutingSpecCirceCodecs.given
import com.monadial.waygrid.common.domain.model.parameter.ParameterValue
import com.monadial.waygrid.common.domain.model.routing.Value.RepeatPolicy
import com.monadial.waygrid.common.domain.model.traversal.condition.Condition
import com.monadial.waygrid.common.domain.model.traversal.dag.JoinStrategy
import com.monadial.waygrid.common.domain.model.traversal.spec.{ Node, Spec }
import com.monadial.waygrid.common.domain.value.Address.ServiceAddress
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
      |                "condition": { "type": "Always" },
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
        onConditions = List(Node.when(Condition.Always, yes)),
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

      val json    = spec.asJson
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

  // ===========================================================================
  // EASY EXAMPLES - Single Node & Simple Chains
  // ===========================================================================

  /**
   * Level 1: Simplest possible spec - single node, no edges
   *
   * Diagram:
   *   [origin/http]
   */
  private val jsonLevel1_SingleNode =
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
      |      "onConditions": []
      |    }
      |  ],
      |  "repeatPolicy": { "type": "NoRepeat" }
      |}
      |""".stripMargin

  test("Level 1: Single node spec"):
      IO {
        decode[Spec](jsonLevel1_SingleNode) match
          case Left(err) => failure(s"Decode failed: $err")
          case Right(spec) =>
            expect(spec.entryPoints.size == 1) &&
            expect(spec.entryPoints.head.isInstanceOf[Node.Standard]) &&
            expect(spec.repeatPolicy == RepeatPolicy.NoRepeat)
      }

  /**
   * Level 2: Simple two-node chain
   *
   * Diagram:
   *   [origin/http] --> [destination/webhook]
   */
  private val jsonLevel2_TwoNodeChain =
    """
      |{
      |  "entryPoints": [
      |    {
      |      "type": "standard",
      |      "address": "waygrid://origin/http",
      |      "retryPolicy": { "type": "None" },
      |      "deliveryStrategy": { "type": "Immediate" },
      |      "onSuccess": {
      |        "type": "standard",
      |        "address": "waygrid://destination/webhook",
      |        "retryPolicy": { "type": "None" },
      |        "deliveryStrategy": { "type": "Immediate" },
      |        "onSuccess": null,
      |        "onFailure": null,
      |        "onConditions": []
      |      },
      |      "onFailure": null,
      |      "onConditions": []
      |    }
      |  ],
      |  "repeatPolicy": { "type": "NoRepeat" }
      |}
      |""".stripMargin

  test("Level 2: Two-node chain"):
      IO {
        decode[Spec](jsonLevel2_TwoNodeChain) match
          case Left(err) => failure(s"Decode failed: $err")
          case Right(spec) =>
            val entry = spec.entryPoints.head.asInstanceOf[Node.Standard]
            expect(entry.onSuccess.isDefined) &&
            expect(entry.onSuccess.get.address.service.unwrap == "webhook")
      }

  /**
   * Level 3: Three-node chain with labels
   *
   * Diagram:
   *   [origin/http] --> [processor/transform] --> [destination/webhook]
   *      "Entry"          "Transform"              "Deliver"
   */
  private val jsonLevel3_ThreeNodeChainWithLabels =
    """
      |{
      |  "entryPoints": [
      |    {
      |      "type": "standard",
      |      "address": "waygrid://origin/http",
      |      "retryPolicy": { "type": "None" },
      |      "deliveryStrategy": { "type": "Immediate" },
      |      "onSuccess": {
      |        "type": "standard",
      |        "address": "waygrid://processor/transform",
      |        "retryPolicy": { "type": "None" },
      |        "deliveryStrategy": { "type": "Immediate" },
      |        "onSuccess": {
      |          "type": "standard",
      |          "address": "waygrid://destination/webhook",
      |          "retryPolicy": { "type": "None" },
      |          "deliveryStrategy": { "type": "Immediate" },
      |          "onSuccess": null,
      |          "onFailure": null,
      |          "onConditions": [],
      |          "label": "Deliver"
      |        },
      |        "onFailure": null,
      |        "onConditions": [],
      |        "label": "Transform"
      |      },
      |      "onFailure": null,
      |      "onConditions": [],
      |      "label": "Entry"
      |    }
      |  ],
      |  "repeatPolicy": { "type": "NoRepeat" }
      |}
      |""".stripMargin

  test("Level 3: Three-node chain with labels"):
      IO {
        decode[Spec](jsonLevel3_ThreeNodeChainWithLabels) match
          case Left(err) => failure(s"Decode failed: $err")
          case Right(spec) =>
            val entry = spec.entryPoints.head.asInstanceOf[Node.Standard]
            expect(entry.label.contains("Entry")) &&
            expect(entry.onSuccess.flatMap(_.label).contains("Transform"))
      }

  // ===========================================================================
  // MEDIUM EXAMPLES - Retry Policies & Delivery Strategies
  // ===========================================================================

  /**
   * Level 4: Node with Linear retry policy
   *
   * Diagram:
   *   [processor/api] --> [destination/webhook]
   *    retry: linear(1s, 3)
   */
  private val jsonLevel4_LinearRetry =
    """
      |{
      |  "entryPoints": [
      |    {
      |      "type": "standard",
      |      "address": "waygrid://processor/api",
      |      "retryPolicy": {
      |        "type": "Linear",
      |        "base": "1 second",
      |        "maxRetries": 3
      |      },
      |      "deliveryStrategy": { "type": "Immediate" },
      |      "onSuccess": {
      |        "type": "standard",
      |        "address": "waygrid://destination/webhook",
      |        "retryPolicy": { "type": "None" },
      |        "deliveryStrategy": { "type": "Immediate" },
      |        "onSuccess": null,
      |        "onFailure": null,
      |        "onConditions": []
      |      },
      |      "onFailure": null,
      |      "onConditions": []
      |    }
      |  ],
      |  "repeatPolicy": { "type": "NoRepeat" }
      |}
      |""".stripMargin

  test("Level 4: Linear retry policy"):
      IO {
        decode[Spec](jsonLevel4_LinearRetry) match
          case Left(err) => failure(s"Decode failed: $err")
          case Right(spec) =>
            val entry = spec.entryPoints.head.asInstanceOf[Node.Standard]
            entry.retryPolicy match
              case com.monadial.waygrid.common.domain.model.resiliency.RetryPolicy.Linear(base, max) =>
                expect(base.toSeconds == 1) && expect(max == 3)
              case other => failure(s"Expected Linear retry, got: $other")
      }

  /**
   * Level 5: Node with Exponential retry policy
   */
  private val jsonLevel5_ExponentialRetry =
    """
      |{
      |  "entryPoints": [
      |    {
      |      "type": "standard",
      |      "address": "waygrid://processor/api",
      |      "retryPolicy": {
      |        "type": "Exponential",
      |        "base": "500 milliseconds",
      |        "maxRetries": 5
      |      },
      |      "deliveryStrategy": { "type": "Immediate" },
      |      "onSuccess": null,
      |      "onFailure": null,
      |      "onConditions": []
      |    }
      |  ],
      |  "repeatPolicy": { "type": "NoRepeat" }
      |}
      |""".stripMargin

  test("Level 5: Exponential retry policy"):
      IO {
        decode[Spec](jsonLevel5_ExponentialRetry) match
          case Left(err) => failure(s"Decode failed: $err")
          case Right(spec) =>
            val entry = spec.entryPoints.head.asInstanceOf[Node.Standard]
            entry.retryPolicy match
              case com.monadial.waygrid.common.domain.model.resiliency.RetryPolicy.Exponential(base, max) =>
                expect(base.toMillis == 500) && expect(max == 5)
              case other => failure(s"Expected Exponential retry, got: $other")
      }

  /**
   * Level 6: Bounded exponential retry with cap
   */
  private val jsonLevel6_BoundedExponentialRetry =
    """
      |{
      |  "entryPoints": [
      |    {
      |      "type": "standard",
      |      "address": "waygrid://processor/api",
      |      "retryPolicy": {
      |        "type": "BoundedExponential",
      |        "base": "1 second",
      |        "cap": "30 seconds",
      |        "maxRetries": 10
      |      },
      |      "deliveryStrategy": { "type": "Immediate" },
      |      "onSuccess": null,
      |      "onFailure": null,
      |      "onConditions": []
      |    }
      |  ],
      |  "repeatPolicy": { "type": "NoRepeat" }
      |}
      |""".stripMargin

  test("Level 6: Bounded exponential retry"):
      IO {
        decode[Spec](jsonLevel6_BoundedExponentialRetry) match
          case Left(err) => failure(s"Decode failed: $err")
          case Right(spec) =>
            val entry = spec.entryPoints.head.asInstanceOf[Node.Standard]
            entry.retryPolicy match
              case com.monadial.waygrid.common.domain.model.resiliency.RetryPolicy.BoundedExponential(base, cap, max) =>
                expect(base.toSeconds == 1) && expect(cap.toSeconds == 30) && expect(max == 10)
              case other => failure(s"Expected BoundedExponential retry, got: $other")
      }

  /**
   * Level 7: Scheduled delivery after delay
   */
  private val jsonLevel7_ScheduledDelivery =
    """
      |{
      |  "entryPoints": [
      |    {
      |      "type": "standard",
      |      "address": "waygrid://processor/batch",
      |      "retryPolicy": { "type": "None" },
      |      "deliveryStrategy": {
      |        "type": "ScheduleAfter",
      |        "delay": "5 minutes"
      |      },
      |      "onSuccess": null,
      |      "onFailure": null,
      |      "onConditions": []
      |    }
      |  ],
      |  "repeatPolicy": { "type": "NoRepeat" }
      |}
      |""".stripMargin

  test("Level 7: Scheduled delivery after delay"):
      IO {
        decode[Spec](jsonLevel7_ScheduledDelivery) match
          case Left(err) => failure(s"Decode failed: $err")
          case Right(spec) =>
            val entry = spec.entryPoints.head.asInstanceOf[Node.Standard]
            entry.deliveryStrategy match
              case com.monadial.waygrid.common.domain.model.routing.Value.DeliveryStrategy.ScheduleAfter(delay) =>
                expect(delay.toMinutes == 5)
              case other => failure(s"Expected ScheduleAfter, got: $other")
      }

  /**
   * Level 8: Chain with failure path
   *
   * Diagram:
   *   [origin/http] --> [processor/validate]
   *                           |
   *                     onFailure
   *                           |
   *                           v
   *                  [destination/dlq]
   */
  private val jsonLevel8_FailurePath =
    """
      |{
      |  "entryPoints": [
      |    {
      |      "type": "standard",
      |      "address": "waygrid://origin/http",
      |      "retryPolicy": { "type": "None" },
      |      "deliveryStrategy": { "type": "Immediate" },
      |      "onSuccess": {
      |        "type": "standard",
      |        "address": "waygrid://processor/validate",
      |        "retryPolicy": { "type": "None" },
      |        "deliveryStrategy": { "type": "Immediate" },
      |        "onSuccess": {
      |          "type": "standard",
      |          "address": "waygrid://destination/webhook",
      |          "retryPolicy": { "type": "None" },
      |          "deliveryStrategy": { "type": "Immediate" },
      |          "onSuccess": null,
      |          "onFailure": null,
      |          "onConditions": []
      |        },
      |        "onFailure": {
      |          "type": "standard",
      |          "address": "waygrid://destination/dlq",
      |          "retryPolicy": { "type": "None" },
      |          "deliveryStrategy": { "type": "Immediate" },
      |          "onSuccess": null,
      |          "onFailure": null,
      |          "onConditions": [],
      |          "label": "Dead Letter Queue"
      |        },
      |        "onConditions": []
      |      },
      |      "onFailure": null,
      |      "onConditions": []
      |    }
      |  ],
      |  "repeatPolicy": { "type": "NoRepeat" }
      |}
      |""".stripMargin

  test("Level 8: Failure path to DLQ"):
      IO {
        decode[Spec](jsonLevel8_FailurePath) match
          case Left(err) => failure(s"Decode failed: $err")
          case Right(spec) =>
            val entry    = spec.entryPoints.head.asInstanceOf[Node.Standard]
            val validate = entry.onSuccess.get.asInstanceOf[Node.Standard]
            expect(validate.onSuccess.isDefined) &&
            expect(validate.onFailure.isDefined) &&
            expect(validate.onFailure.get.address.service.unwrap == "dlq")
      }

  // ===========================================================================
  // ADVANCED EXAMPLES - Conditions, Fork/Join, Complex Routing
  // ===========================================================================

  /**
   * Level 9: Simple conditional routing with Always condition
   *
   * Diagram:
   *   [processor/classify]
   *         |
   *    when Always
   *         |
   *         v
   *   [destination/premium]
   */
  private val jsonLevel9_ConditionalAlways =
    """
      |{
      |  "entryPoints": [
      |    {
      |      "type": "standard",
      |      "address": "waygrid://processor/classify",
      |      "retryPolicy": { "type": "None" },
      |      "deliveryStrategy": { "type": "Immediate" },
      |      "onSuccess": null,
      |      "onFailure": null,
      |      "onConditions": [
      |        {
      |          "condition": { "type": "Always" },
      |          "to": {
      |            "type": "standard",
      |            "address": "waygrid://destination/premium",
      |            "retryPolicy": { "type": "None" },
      |            "deliveryStrategy": { "type": "Immediate" },
      |            "onSuccess": null,
      |            "onFailure": null,
      |            "onConditions": []
      |          }
      |        }
      |      ]
      |    }
      |  ],
      |  "repeatPolicy": { "type": "NoRepeat" }
      |}
      |""".stripMargin

  test("Level 9: Conditional routing with Always"):
      IO {
        decode[Spec](jsonLevel9_ConditionalAlways) match
          case Left(err) => failure(s"Decode failed: $err")
          case Right(spec) =>
            val entry = spec.entryPoints.head.asInstanceOf[Node.Standard]
            expect(entry.onConditions.length == 1) &&
            expect(entry.onConditions.head.condition == Condition.Always)
      }

  /**
   * Level 10: Multiple conditional routes with Always
   *
   * Diagram:
   *   [processor/router]
   *     |          |
   *   Always    Always
   *     |          |
   *     v          v
   *  [dest/a]   [dest/b]
   */
  private val jsonLevel10_MultipleConditions =
    """
      |{
      |  "entryPoints": [
      |    {
      |      "type": "standard",
      |      "address": "waygrid://processor/router",
      |      "retryPolicy": { "type": "None" },
      |      "deliveryStrategy": { "type": "Immediate" },
      |      "onSuccess": null,
      |      "onFailure": null,
      |      "onConditions": [
      |        {
      |          "condition": { "type": "Always" },
      |          "to": {
      |            "type": "standard",
      |            "address": "waygrid://destination/a",
      |            "retryPolicy": { "type": "None" },
      |            "deliveryStrategy": { "type": "Immediate" },
      |            "onSuccess": null,
      |            "onFailure": null,
      |            "onConditions": []
      |          }
      |        },
      |        {
      |          "condition": { "type": "Always" },
      |          "to": {
      |            "type": "standard",
      |            "address": "waygrid://destination/b",
      |            "retryPolicy": { "type": "None" },
      |            "deliveryStrategy": { "type": "Immediate" },
      |            "onSuccess": null,
      |            "onFailure": null,
      |            "onConditions": []
      |          }
      |        }
      |      ]
      |    }
      |  ],
      |  "repeatPolicy": { "type": "NoRepeat" }
      |}
      |""".stripMargin

  test("Level 10: Multiple conditional routes"):
      IO {
        decode[Spec](jsonLevel10_MultipleConditions) match
          case Left(err) => failure(s"Decode failed: $err")
          case Right(spec) =>
            val entry = spec.entryPoints.head.asInstanceOf[Node.Standard]
            expect(entry.onConditions.length == 2) &&
            expect(entry.onConditions.head.condition == Condition.Always) &&
            expect(entry.onConditions(1).condition == Condition.Always)
      }

  /**
   * Level 11: Complex conditions with And/Or/Not
   *
   * Condition: (Always AND Always) OR NOT Always
   */
  private val jsonLevel11_ComplexConditions =
    """
      |{
      |  "entryPoints": [
      |    {
      |      "type": "standard",
      |      "address": "waygrid://processor/filter",
      |      "retryPolicy": { "type": "None" },
      |      "deliveryStrategy": { "type": "Immediate" },
      |      "onSuccess": null,
      |      "onFailure": null,
      |      "onConditions": [
      |        {
      |          "condition": {
      |            "type": "Or",
      |            "any": [
      |              {
      |                "type": "And",
      |                "all": [ { "type": "Always" }, { "type": "Always" } ]
      |              },
      |              {
      |                "type": "Not",
      |                "cond": { "type": "Always" }
      |              }
      |            ]
      |          },
      |          "to": {
      |            "type": "standard",
      |            "address": "waygrid://destination/priority",
      |            "retryPolicy": { "type": "None" },
      |            "deliveryStrategy": { "type": "Immediate" },
      |            "onSuccess": null,
      |            "onFailure": null,
      |            "onConditions": []
      |          }
      |        }
      |      ]
      |    }
      |  ],
      |  "repeatPolicy": { "type": "NoRepeat" }
      |}
      |""".stripMargin

  test("Level 11: Complex nested conditions (And/Or/Not)"):
      IO {
        decode[Spec](jsonLevel11_ComplexConditions) match
          case Left(err) => failure(s"Decode failed: $err")
          case Right(spec) =>
            val entry = spec.entryPoints.head.asInstanceOf[Node.Standard]
            entry.onConditions.head.condition match
              case Condition.Or(any) =>
                expect(any.length == 2) &&
                expect(any.head.isInstanceOf[Condition.And]) &&
                expect(any(1).isInstanceOf[Condition.Not])
              case other => failure(s"Expected Or condition, got: $other")
      }

  /**
   * Level 12: Simple Fork/Join (And strategy)
   *
   * Diagram:
   *   [entry] --> [FORK]
   *                 |-- branch "a" --> [proc/a] --> [JOIN] --> [destination]
   *                 |-- branch "b" --> [proc/b] ----^
   */
  private val jsonLevel12_SimpleForkJoin =
    """
      |{
      |  "entryPoints": [
      |    {
      |      "type": "standard",
      |      "address": "waygrid://origin/http",
      |      "retryPolicy": { "type": "None" },
      |      "deliveryStrategy": { "type": "Immediate" },
      |      "onSuccess": {
      |        "type": "fork",
      |        "address": "waygrid://system/waystation",
      |        "retryPolicy": { "type": "None" },
      |        "deliveryStrategy": { "type": "Immediate" },
      |        "branches": {
      |          "a": {
      |            "type": "standard",
      |            "address": "waygrid://processor/a",
      |            "retryPolicy": { "type": "None" },
      |            "deliveryStrategy": { "type": "Immediate" },
      |            "onSuccess": {
      |              "type": "join",
      |              "address": "waygrid://system/waystation",
      |              "retryPolicy": { "type": "None" },
      |              "deliveryStrategy": { "type": "Immediate" },
      |              "joinNodeId": "parallel-1",
      |              "strategy": { "type": "And" },
      |              "onSuccess": {
      |                "type": "standard",
      |                "address": "waygrid://destination/webhook",
      |                "retryPolicy": { "type": "None" },
      |                "deliveryStrategy": { "type": "Immediate" },
      |                "onSuccess": null,
      |                "onFailure": null,
      |                "onConditions": []
      |              },
      |              "onFailure": null,
      |              "onTimeout": null
      |            },
      |            "onFailure": null,
      |            "onConditions": []
      |          },
      |          "b": {
      |            "type": "standard",
      |            "address": "waygrid://processor/b",
      |            "retryPolicy": { "type": "None" },
      |            "deliveryStrategy": { "type": "Immediate" },
      |            "onSuccess": {
      |              "type": "join",
      |              "address": "waygrid://system/waystation",
      |              "retryPolicy": { "type": "None" },
      |              "deliveryStrategy": { "type": "Immediate" },
      |              "joinNodeId": "parallel-1",
      |              "strategy": { "type": "And" },
      |              "onSuccess": {
      |                "type": "standard",
      |                "address": "waygrid://destination/webhook",
      |                "retryPolicy": { "type": "None" },
      |                "deliveryStrategy": { "type": "Immediate" },
      |                "onSuccess": null,
      |                "onFailure": null,
      |                "onConditions": []
      |              },
      |              "onFailure": null,
      |              "onTimeout": null
      |            },
      |            "onFailure": null,
      |            "onConditions": []
      |          }
      |        },
      |        "joinNodeId": "parallel-1"
      |      },
      |      "onFailure": null,
      |      "onConditions": []
      |    }
      |  ],
      |  "repeatPolicy": { "type": "NoRepeat" }
      |}
      |""".stripMargin

  test("Level 12: Simple fork/join with And strategy"):
      IO {
        decode[Spec](jsonLevel12_SimpleForkJoin) match
          case Left(err) => failure(s"Decode failed: $err")
          case Right(spec) =>
            val entry = spec.entryPoints.head.asInstanceOf[Node.Standard]
            entry.onSuccess.get match
              case fork: Node.Fork =>
                expect(fork.branches.size == 2) &&
                expect(fork.joinNodeId == "parallel-1") &&
                expect(fork.branches.contains("a")) &&
                expect(fork.branches.contains("b"))
              case other => failure(s"Expected Fork node, got: $other")
      }

  /**
   * Level 13: Fork/Join with Or strategy (race)
   */
  private val jsonLevel13_ForkJoinOr =
    """
      |{
      |  "entryPoints": [
      |    {
      |      "type": "fork",
      |      "address": "waygrid://system/waystation",
      |      "retryPolicy": { "type": "None" },
      |      "deliveryStrategy": { "type": "Immediate" },
      |      "branches": {
      |        "fast": {
      |          "type": "standard",
      |          "address": "waygrid://processor/fast",
      |          "retryPolicy": { "type": "None" },
      |          "deliveryStrategy": { "type": "Immediate" },
      |          "onSuccess": {
      |            "type": "join",
      |            "address": "waygrid://system/waystation",
      |            "retryPolicy": { "type": "None" },
      |            "deliveryStrategy": { "type": "Immediate" },
      |            "joinNodeId": "race-1",
      |            "strategy": { "type": "Or" },
      |            "onSuccess": null,
      |            "onFailure": null,
      |            "onTimeout": null
      |          },
      |          "onFailure": null,
      |          "onConditions": []
      |        },
      |        "slow": {
      |          "type": "standard",
      |          "address": "waygrid://processor/slow",
      |          "retryPolicy": { "type": "None" },
      |          "deliveryStrategy": { "type": "Immediate" },
      |          "onSuccess": {
      |            "type": "join",
      |            "address": "waygrid://system/waystation",
      |            "retryPolicy": { "type": "None" },
      |            "deliveryStrategy": { "type": "Immediate" },
      |            "joinNodeId": "race-1",
      |            "strategy": { "type": "Or" },
      |            "onSuccess": null,
      |            "onFailure": null,
      |            "onTimeout": null
      |          },
      |          "onFailure": null,
      |          "onConditions": []
      |        }
      |      },
      |      "joinNodeId": "race-1",
      |      "label": "Race branches"
      |    }
      |  ],
      |  "repeatPolicy": { "type": "NoRepeat" }
      |}
      |""".stripMargin

  test("Level 13: Fork/Join with Or strategy (race)"):
      IO {
        decode[Spec](jsonLevel13_ForkJoinOr) match
          case Left(err) => failure(s"Decode failed: $err")
          case Right(spec) =>
            val fork         = spec.entryPoints.head.asInstanceOf[Node.Fork]
            val joinFromFast = fork.branches("fast").asInstanceOf[Node.Standard].onSuccess.get.asInstanceOf[Node.Join]
            expect(fork.branches.size == 2) &&
            expect(joinFromFast.strategy == JoinStrategy.Or)
      }

  /**
   * Level 14: Fork/Join with Quorum strategy
   */
  private val jsonLevel14_ForkJoinQuorum =
    """
      |{
      |  "entryPoints": [
      |    {
      |      "type": "fork",
      |      "address": "waygrid://system/waystation",
      |      "retryPolicy": { "type": "None" },
      |      "deliveryStrategy": { "type": "Immediate" },
      |      "branches": {
      |        "node1": {
      |          "type": "standard",
      |          "address": "waygrid://processor/node1",
      |          "retryPolicy": { "type": "None" },
      |          "deliveryStrategy": { "type": "Immediate" },
      |          "onSuccess": {
      |            "type": "join",
      |            "address": "waygrid://system/waystation",
      |            "retryPolicy": { "type": "None" },
      |            "deliveryStrategy": { "type": "Immediate" },
      |            "joinNodeId": "quorum-1",
      |            "strategy": { "type": "Quorum", "n": 2 },
      |            "timeout": "30 seconds",
      |            "onSuccess": null,
      |            "onFailure": null,
      |            "onTimeout": null
      |          },
      |          "onFailure": null,
      |          "onConditions": []
      |        },
      |        "node2": {
      |          "type": "standard",
      |          "address": "waygrid://processor/node2",
      |          "retryPolicy": { "type": "None" },
      |          "deliveryStrategy": { "type": "Immediate" },
      |          "onSuccess": {
      |            "type": "join",
      |            "address": "waygrid://system/waystation",
      |            "retryPolicy": { "type": "None" },
      |            "deliveryStrategy": { "type": "Immediate" },
      |            "joinNodeId": "quorum-1",
      |            "strategy": { "type": "Quorum", "n": 2 },
      |            "timeout": "30 seconds",
      |            "onSuccess": null,
      |            "onFailure": null,
      |            "onTimeout": null
      |          },
      |          "onFailure": null,
      |          "onConditions": []
      |        },
      |        "node3": {
      |          "type": "standard",
      |          "address": "waygrid://processor/node3",
      |          "retryPolicy": { "type": "None" },
      |          "deliveryStrategy": { "type": "Immediate" },
      |          "onSuccess": {
      |            "type": "join",
      |            "address": "waygrid://system/waystation",
      |            "retryPolicy": { "type": "None" },
      |            "deliveryStrategy": { "type": "Immediate" },
      |            "joinNodeId": "quorum-1",
      |            "strategy": { "type": "Quorum", "n": 2 },
      |            "timeout": "30 seconds",
      |            "onSuccess": null,
      |            "onFailure": null,
      |            "onTimeout": null
      |          },
      |          "onFailure": null,
      |          "onConditions": []
      |        }
      |      },
      |      "joinNodeId": "quorum-1"
      |    }
      |  ],
      |  "repeatPolicy": { "type": "NoRepeat" }
      |}
      |""".stripMargin

  test("Level 14: Fork/Join with Quorum strategy"):
      IO {
        decode[Spec](jsonLevel14_ForkJoinQuorum) match
          case Left(err) => failure(s"Decode failed: $err")
          case Right(spec) =>
            val fork = spec.entryPoints.head.asInstanceOf[Node.Fork]
            val join = fork.branches("node1").asInstanceOf[Node.Standard].onSuccess.get.asInstanceOf[Node.Join]
            expect(fork.branches.size == 3) &&
            expect(join.strategy == JoinStrategy.Quorum(2)) &&
            expect(join.timeout.map(_.toSeconds).contains(30L))
      }

  /**
   * Level 15: Join with timeout and failure handling
   */
  private val jsonLevel15_JoinWithTimeoutHandling =
    """
      |{
      |  "entryPoints": [
      |    {
      |      "type": "fork",
      |      "address": "waygrid://system/waystation",
      |      "retryPolicy": { "type": "None" },
      |      "deliveryStrategy": { "type": "Immediate" },
      |      "branches": {
      |        "main": {
      |          "type": "standard",
      |          "address": "waygrid://processor/main",
      |          "retryPolicy": { "type": "None" },
      |          "deliveryStrategy": { "type": "Immediate" },
      |          "onSuccess": {
      |            "type": "join",
      |            "address": "waygrid://system/waystation",
      |            "retryPolicy": { "type": "None" },
      |            "deliveryStrategy": { "type": "Immediate" },
      |            "joinNodeId": "timeout-test",
      |            "strategy": { "type": "And" },
      |            "timeout": "60 seconds",
      |            "onSuccess": {
      |              "type": "standard",
      |              "address": "waygrid://destination/success",
      |              "retryPolicy": { "type": "None" },
      |              "deliveryStrategy": { "type": "Immediate" },
      |              "onSuccess": null,
      |              "onFailure": null,
      |              "onConditions": []
      |            },
      |            "onFailure": {
      |              "type": "standard",
      |              "address": "waygrid://destination/failure",
      |              "retryPolicy": { "type": "None" },
      |              "deliveryStrategy": { "type": "Immediate" },
      |              "onSuccess": null,
      |              "onFailure": null,
      |              "onConditions": []
      |            },
      |            "onTimeout": {
      |              "type": "standard",
      |              "address": "waygrid://destination/timeout",
      |              "retryPolicy": { "type": "None" },
      |              "deliveryStrategy": { "type": "Immediate" },
      |              "onSuccess": null,
      |              "onFailure": null,
      |              "onConditions": []
      |            }
      |          },
      |          "onFailure": null,
      |          "onConditions": []
      |        }
      |      },
      |      "joinNodeId": "timeout-test"
      |    }
      |  ],
      |  "repeatPolicy": { "type": "NoRepeat" }
      |}
      |""".stripMargin

  test("Level 15: Join with timeout and failure handling"):
      IO {
        decode[Spec](jsonLevel15_JoinWithTimeoutHandling) match
          case Left(err) => failure(s"Decode failed: $err")
          case Right(spec) =>
            val fork = spec.entryPoints.head.asInstanceOf[Node.Fork]
            val join = fork.branches("main").asInstanceOf[Node.Standard].onSuccess.get.asInstanceOf[Node.Join]
            expect(join.timeout.map(_.toSeconds).contains(60L)) &&
            expect(join.onSuccess.isDefined) &&
            expect(join.onFailure.isDefined) &&
            expect(join.onTimeout.isDefined) &&
            expect(join.onTimeout.get.address.service.unwrap == "timeout")
      }

  /**
   * Level 16: Repeat policy - Indefinitely
   */
  private val jsonLevel16_RepeatIndefinitely =
    """
      |{
      |  "entryPoints": [
      |    {
      |      "type": "standard",
      |      "address": "waygrid://processor/poll",
      |      "retryPolicy": { "type": "None" },
      |      "deliveryStrategy": { "type": "Immediate" },
      |      "onSuccess": null,
      |      "onFailure": null,
      |      "onConditions": []
      |    }
      |  ],
      |  "repeatPolicy": {
      |    "type": "Indefinitely",
      |    "every": "1 hour"
      |  }
      |}
      |""".stripMargin

  test("Level 16: Repeat policy - Indefinitely"):
      IO {
        decode[Spec](jsonLevel16_RepeatIndefinitely) match
          case Left(err) => failure(s"Decode failed: $err")
          case Right(spec) =>
            spec.repeatPolicy match
              case RepeatPolicy.Indefinitely(every) =>
                expect(every.toHours == 1)
              case other => failure(s"Expected Indefinitely, got: $other")
      }

  // ===========================================================================
  // ROUNDTRIP TESTS - Encode then Decode
  // ===========================================================================

  test("Roundtrip: Single node"):
      IO {
        val decoded = decode[Spec](jsonLevel1_SingleNode)
        decoded match
          case Left(err) => failure(s"Decode failed: $err")
          case Right(spec) =>
            val reEncoded = spec.asJson
            val reDecoded = reEncoded.as[Spec]
            expect(reDecoded == Right(spec))
      }

  test("Roundtrip: Complex fork/join"):
      IO {
        val decoded = decode[Spec](jsonLevel12_SimpleForkJoin)
        decoded match
          case Left(err) => failure(s"Decode failed: $err")
          case Right(spec) =>
            val reEncoded = spec.asJson
            val reDecoded = reEncoded.as[Spec]
            expect(reDecoded == Right(spec))
      }

  test("Roundtrip: All retry policies"):
      IO {
        import com.monadial.waygrid.common.domain.model.resiliency.RetryPolicy as RP
        import scala.concurrent.duration.*

        val addr = ServiceAddress(Uri.unsafeFromString("waygrid://processor/test"))
        val retryPolicies = List(
          RP.None,
          RP.Linear(1.second, 3),
          RP.Exponential(500.millis, 5),
          RP.BoundedExponential(1.second, 30.seconds, 10),
          RP.Fibonacci(1.second, 5),
          RP.FullJitter(1.second, 5),
          RP.DecorrelatedJitter(1.second, 5)
        )

        val results = retryPolicies.map { policy =>
          val node = Node.Standard(
            address = addr,
            retryPolicy = policy,
            deliveryStrategy = com.monadial.waygrid.common.domain.model.routing.Value.DeliveryStrategy.Immediate,
            onSuccess = None,
            onFailure = None,
            onConditions = Nil,
            label = None
          )
          val spec    = Spec.single(node, RepeatPolicy.NoRepeat)
          val json    = spec.asJson
          val decoded = json.as[Spec]
          decoded == Right(spec)
        }

        expect(results.forall(identity))
      }

  // ===========================================================================
  // PARAMETER TESTS - Node parameters extraction
  // ===========================================================================

  /**
   * Spec with parameters on nodes - demonstrates OpenAI processor with config
   *
   * Diagram:
   *   [origin/http] --> [processor/openai] --> [destination/websocket]
   *                     (with parameters)       (with channelId)
   */
  private val jsonWithParameters =
    """
      |{
      |  "entryPoints": [
      |    {
      |      "type": "standard",
      |      "address": "waygrid://origin/http",
      |      "retryPolicy": { "type": "None" },
      |      "deliveryStrategy": { "type": "Immediate" },
      |      "parameters": {},
      |      "onSuccess": {
      |        "type": "standard",
      |        "address": "waygrid://processor/openai",
      |        "retryPolicy": { "type": "None" },
      |        "deliveryStrategy": { "type": "Immediate" },
      |        "parameters": {
      |          "apiKey": { "type": "Secret", "ref": { "path": "tenant-123/openai", "field": "apiKey" } },
      |          "model": { "type": "StringVal", "value": "gpt-4" },
      |          "temperature": { "type": "FloatVal", "value": 0.7 },
      |          "maxTokens": { "type": "IntVal", "value": 1000 }
      |        },
      |        "onSuccess": {
      |          "type": "standard",
      |          "address": "waygrid://destination/websocket",
      |          "retryPolicy": { "type": "None" },
      |          "deliveryStrategy": { "type": "Immediate" },
      |          "parameters": {
      |            "channelId": { "type": "StringVal", "value": "channel-456" },
      |            "broadcast": { "type": "BoolVal", "value": true }
      |          },
      |          "onSuccess": null,
      |          "onFailure": null,
      |          "onConditions": [],
      |          "label": "websocket"
      |        },
      |        "onFailure": null,
      |        "onConditions": [],
      |        "label": "openai"
      |      },
      |      "onFailure": null,
      |      "onConditions": [],
      |      "label": "entry"
      |    }
      |  ],
      |  "repeatPolicy": { "type": "NoRepeat" }
      |}
      |""".stripMargin

  test("Decode Spec with parameters"):
      IO {
        decode[Spec](jsonWithParameters) match
          case Left(err) => failure(s"Decode failed: $err")
          case Right(spec) =>
            val entry  = spec.entryPoints.head.asInstanceOf[Node.Standard]
            val openai = entry.onSuccess.get.asInstanceOf[Node.Standard]
            val ws     = openai.onSuccess.get.asInstanceOf[Node.Standard]

            // Check OpenAI processor parameters
            val openaiParams = openai.parameters
            val hasApiKey = openaiParams.get("apiKey") match
              case Some(ParameterValue.Secret(ref)) => ref.path.value == "tenant-123/openai"
              case _                                => false
            val hasModel  = openaiParams.get("model") == Some(ParameterValue.StringVal("gpt-4"))
            val hasTemp   = openaiParams.get("temperature") == Some(ParameterValue.FloatVal(0.7))
            val hasTokens = openaiParams.get("maxTokens") == Some(ParameterValue.IntVal(1000))

            // Check WebSocket destination parameters
            val wsParams     = ws.parameters
            val hasChannelId = wsParams.get("channelId") == Some(ParameterValue.StringVal("channel-456"))
            val hasBroadcast = wsParams.get("broadcast") == Some(ParameterValue.BoolVal(true))

            expect(hasApiKey) &&
            expect(hasModel) &&
            expect(hasTemp) &&
            expect(hasTokens) &&
            expect(hasChannelId) &&
            expect(hasBroadcast)
      }

  test("Roundtrip: Spec with parameters"):
      IO {
        decode[Spec](jsonWithParameters) match
          case Left(err) => failure(s"Decode failed: $err")
          case Right(spec) =>
            val reEncoded = spec.asJson
            val reDecoded = reEncoded.as[Spec]
            reDecoded match
              case Left(err) => failure(s"Re-decode failed: $err")
              case Right(decoded) =>
                val origOpenai =
                  spec.entryPoints.head.asInstanceOf[Node.Standard].onSuccess.get.asInstanceOf[Node.Standard]
                val decodedOpenai =
                  decoded.entryPoints.head.asInstanceOf[Node.Standard].onSuccess.get.asInstanceOf[Node.Standard]
                expect(origOpenai.parameters == decodedOpenai.parameters)
      }
