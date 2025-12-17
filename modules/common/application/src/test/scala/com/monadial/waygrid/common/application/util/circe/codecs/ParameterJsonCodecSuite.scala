package com.monadial.waygrid.common.application.util.circe.codecs

import cats.data.NonEmptyList
import com.monadial.waygrid.common.application.util.circe.codecs.DomainParameterCodecs.given
import com.monadial.waygrid.common.domain.model.parameter.*
import io.circe.*
import io.circe.parser.*
import io.circe.syntax.*
import weaver.SimpleIOSuite

object ParameterJsonCodecSuite extends SimpleIOSuite:

  // ---------------------------------------------------------------------------
  // ParameterValue Codec Tests
  // ---------------------------------------------------------------------------

  pureTest("ParameterValue.StringVal roundtrips through JSON") {
    val value   = ParameterValue.StringVal("hello")
    val json    = value.asJson
    val decoded = json.as[ParameterValue]
    expect(decoded == Right(value))
  }

  pureTest("ParameterValue.IntVal roundtrips through JSON") {
    val value   = ParameterValue.IntVal(42)
    val json    = value.asJson
    val decoded = json.as[ParameterValue]
    expect(decoded == Right(value))
  }

  pureTest("ParameterValue.FloatVal roundtrips through JSON") {
    val value   = ParameterValue.FloatVal(3.14)
    val json    = value.asJson
    val decoded = json.as[ParameterValue]
    expect(decoded == Right(value))
  }

  pureTest("ParameterValue.BoolVal roundtrips through JSON") {
    val value   = ParameterValue.BoolVal(true)
    val json    = value.asJson
    val decoded = json.as[ParameterValue]
    expect(decoded == Right(value))
  }

  pureTest("ParameterValue.Secret roundtrips through JSON") {
    val value   = ParameterValue.Secret(SecretReference("tenant-123/api-keys/openai"))
    val json    = value.asJson
    val decoded = json.as[ParameterValue]
    expect(decoded == Right(value))
  }

  pureTest("ParameterValue.Secret with field roundtrips through JSON") {
    val value = ParameterValue.Secret(SecretReference(
      SecretPath("tenant-123/openai"),
      Some("apiKey"),
      None
    ))
    val json    = value.asJson
    val decoded = json.as[ParameterValue]
    expect(decoded == Right(value))
  }

  pureTest("ParameterValue.Secret with version roundtrips through JSON") {
    val value = ParameterValue.Secret(SecretReference(
      SecretPath("tenant-123/openai"),
      None,
      Some(SecretVersion("v2"))
    ))
    val json    = value.asJson
    val decoded = json.as[ParameterValue]
    expect(decoded == Right(value))
  }

  // ---------------------------------------------------------------------------
  // SecretReference Codec Tests
  // ---------------------------------------------------------------------------

  pureTest("SecretReference minimal roundtrips through JSON") {
    val ref     = SecretReference("tenant/key")
    val json    = ref.asJson
    val decoded = json.as[SecretReference]
    expect(decoded == Right(ref))
  }

  pureTest("SecretReference with all fields roundtrips through JSON") {
    val ref = SecretReference(
      SecretPath("tenant-123/credentials/api"),
      Some("token"),
      Some(SecretVersion("v3"))
    )
    val json    = ref.asJson
    val decoded = json.as[SecretReference]
    expect(decoded == Right(ref))
  }

  // ---------------------------------------------------------------------------
  // ParameterType Codec Tests
  // ---------------------------------------------------------------------------

  pureTest("ParameterType.StringType roundtrips through JSON") {
    val pt      = ParameterType.StringType
    val json    = pt.asJson
    val decoded = json.as[ParameterType]
    expect(decoded == Right(pt))
  }

  pureTest("ParameterType.IntType roundtrips through JSON") {
    val pt      = ParameterType.IntType
    val json    = pt.asJson
    val decoded = json.as[ParameterType]
    expect(decoded == Right(pt))
  }

  pureTest("ParameterType.FloatType roundtrips through JSON") {
    val pt      = ParameterType.FloatType
    val json    = pt.asJson
    val decoded = json.as[ParameterType]
    expect(decoded == Right(pt))
  }

  pureTest("ParameterType.BoolType roundtrips through JSON") {
    val pt      = ParameterType.BoolType
    val json    = pt.asJson
    val decoded = json.as[ParameterType]
    expect(decoded == Right(pt))
  }

  pureTest("ParameterType.EnumType roundtrips through JSON") {
    val pt      = ParameterType.oneOf("gpt-4", "gpt-3.5-turbo", "claude-3")
    val json    = pt.asJson
    val decoded = json.as[ParameterType]
    expect(decoded == Right(pt))
  }

  // ---------------------------------------------------------------------------
  // ParameterConstraint Codec Tests
  // ---------------------------------------------------------------------------

  pureTest("ParameterConstraint.length roundtrips through JSON") {
    val constraint = ParameterConstraint.length(3, 10)
    val json       = constraint.asJson
    val decoded    = json.as[ParameterConstraint]
    expect(decoded == Right(constraint))
  }

  pureTest("ParameterConstraint.range roundtrips through JSON") {
    val constraint = ParameterConstraint.range(BigDecimal(0), BigDecimal(100))
    val json       = constraint.asJson
    val decoded    = json.as[ParameterConstraint]
    expect(decoded == Right(constraint))
  }

  pureTest("ParameterConstraint.regex roundtrips through JSON") {
    val constraint = ParameterConstraint.regex("^[a-z]+$")
    val json       = constraint.asJson
    val decoded    = json.as[ParameterConstraint]
    expect(decoded == Right(constraint))
  }

  // ---------------------------------------------------------------------------
  // ParameterDef Codec Tests
  // ---------------------------------------------------------------------------

  pureTest("ParameterDef.requiredString roundtrips through JSON") {
    val paramDef = ParameterDef.requiredString("name", "User's name")
    val json     = paramDef.asJson
    val decoded  = json.as[ParameterDef]
    expect(decoded == Right(paramDef))
  }

  pureTest("ParameterDef.requiredSecret roundtrips through JSON") {
    val paramDef = ParameterDef.requiredSecret("apiKey", "API key")
    val json     = paramDef.asJson
    val decoded  = json.as[ParameterDef]
    expect(decoded == Right(paramDef))
  }

  pureTest("ParameterDef.optionalInt roundtrips through JSON") {
    val paramDef = ParameterDef.optionalInt("maxRetries", 3)
    val json     = paramDef.asJson
    val decoded  = json.as[ParameterDef]
    expect(decoded == Right(paramDef))
  }

  pureTest("ParameterDef.optionalFloat with constraints roundtrips through JSON") {
    val paramDef = ParameterDef.optionalFloat("temperature", default = 0.7, min = 0.0, max = 2.0)
    val json     = paramDef.asJson
    val decoded  = json.as[ParameterDef]
    expect(decoded == Right(paramDef))
  }

  pureTest("ParameterDef.requiredEnum roundtrips through JSON") {
    val paramDef = ParameterDef.requiredEnum("model", "gpt-4", "gpt-3.5-turbo")
    val json     = paramDef.asJson
    val decoded  = json.as[ParameterDef]
    expect(decoded == Right(paramDef))
  }

  // ---------------------------------------------------------------------------
  // ServiceParameterSchema Codec Tests
  // ---------------------------------------------------------------------------

  pureTest("ServiceParameterSchema roundtrips through JSON") {
    import com.monadial.waygrid.common.domain.value.Address.ServiceAddress

    val address = ServiceAddress.fromString("waygrid://processor/openai").toOption.get
    val schema = ServiceParameterSchema(
      serviceAddress = address,
      parameters = List(
        ParameterDef.requiredSecret("apiKey", "OpenAI API key"),
        ParameterDef.requiredEnum("model", "gpt-4", "gpt-3.5-turbo"),
        ParameterDef.optionalFloat("temperature", default = 0.7, min = 0.0, max = 2.0)
      )
    )
    val json    = schema.asJson
    val decoded = json.as[ServiceParameterSchema]
    expect(decoded == Right(schema))
  }

  // ---------------------------------------------------------------------------
  // Decode from JSON String Tests
  // ---------------------------------------------------------------------------

  pureTest("Decode ParameterValue.StringVal from JSON string") {
    val jsonStr = """{"type":"StringVal","value":"hello"}"""
    val decoded = decode[ParameterValue](jsonStr)
    expect(decoded == Right(ParameterValue.StringVal("hello")))
  }

  pureTest("Decode ParameterValue.IntVal from JSON string") {
    val jsonStr = """{"type":"IntVal","value":42}"""
    val decoded = decode[ParameterValue](jsonStr)
    expect(decoded == Right(ParameterValue.IntVal(42)))
  }

  pureTest("Decode ParameterValue.Secret from JSON string") {
    val jsonStr = """{"type":"Secret","ref":{"path":"tenant/key"}}"""
    val decoded = decode[ParameterValue](jsonStr)
    expect(decoded == Right(ParameterValue.Secret(SecretReference("tenant/key"))))
  }

  pureTest("Decode ParameterType.EnumType from JSON string") {
    val jsonStr = """{"type":"EnumType","values":["a","b","c"]}"""
    val decoded = decode[ParameterType](jsonStr)
    expect(decoded == Right(ParameterType.EnumType(NonEmptyList.of("a", "b", "c"))))
  }

  // ---------------------------------------------------------------------------
  // Map[String, ParameterValue] Codec Tests
  // ---------------------------------------------------------------------------

  pureTest("Map of parameters roundtrips through JSON") {
    val params: Map[String, ParameterValue] = Map(
      "name"    -> ParameterValue.StringVal("test"),
      "count"   -> ParameterValue.IntVal(5),
      "enabled" -> ParameterValue.BoolVal(true),
      "apiKey"  -> ParameterValue.Secret(SecretReference("tenant/key"))
    )
    val json    = params.asJson
    val decoded = json.as[Map[String, ParameterValue]]
    expect(decoded == Right(params))
  }

  pureTest("Empty parameter map roundtrips through JSON") {
    val params: Map[String, ParameterValue] = Map.empty
    val json                                = params.asJson
    val decoded                             = json.as[Map[String, ParameterValue]]
    expect(decoded == Right(params))
  }
