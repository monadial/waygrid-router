package com.monadial.waygrid.common.domain.algebra

import com.monadial.waygrid.common.domain.algebra.ParameterValidator.ValidationError
import com.monadial.waygrid.common.domain.model.parameter.*
import com.monadial.waygrid.common.domain.value.Address.ServiceAddress
import weaver.SimpleIOSuite

object ParameterValidatorSuite extends SimpleIOSuite:

  // Test fixtures
  val testServiceAddress: ServiceAddress =
    ServiceAddress.fromString("waygrid://processor/openai").toOption.get

  val openAiSchema: ServiceParameterSchema = ServiceParameterSchema(
    serviceAddress = testServiceAddress,
    parameters = List(
      ParameterDef.requiredSecret("apiKey", "OpenAI API key"),
      ParameterDef.requiredEnum("model", "gpt-4", "gpt-3.5-turbo", "gpt-4-turbo"),
      ParameterDef.optionalFloat("temperature", default = 0.7, min = 0.0, max = 2.0),
      ParameterDef.optionalInt("maxTokens", default = 1000)
    )
  )

  // ---------------------------------------------------------------------------
  // Required Parameter Tests
  // ---------------------------------------------------------------------------

  pureTest("validates successfully when all required parameters provided") {
    val params = Map(
      "apiKey" -> ParameterValue.Secret(SecretReference("tenant-123/openai")),
      "model"  -> ParameterValue.StringVal("gpt-4")
    )

    val result = ParameterValidator.validate(openAiSchema, params)
    expect(result.isValid)
  }

  pureTest("fails when required parameter is missing") {
    val params = Map(
      "model" -> ParameterValue.StringVal("gpt-4")
      // apiKey is missing
    )

    val result = ParameterValidator.validate(openAiSchema, params)
    expect(result.isInvalid) &&
    expect(result.toEither.left.toOption.exists(_.exists {
      case ValidationError.MissingRequired("apiKey") => true
      case _                                         => false
    }))
  }

  pureTest("fails with multiple errors when multiple required parameters missing") {
    val params = Map[String, ParameterValue]()

    val result = ParameterValidator.validate(openAiSchema, params)
    expect(result.isInvalid) &&
    expect(result.toEither.left.toOption.map(_.toList.length).getOrElse(0) == 2)
  }

  // ---------------------------------------------------------------------------
  // Type Validation Tests
  // ---------------------------------------------------------------------------

  pureTest("fails when string provided instead of int") {
    val schema = ServiceParameterSchema(
      testServiceAddress,
      List(ParameterDef("count", ParameterType.IntType, required = true))
    )
    val params = Map("count" -> ParameterValue.StringVal("not-an-int"))

    val result = ParameterValidator.validate(schema, params)
    expect(result.isInvalid) &&
    expect(result.toEither.left.toOption.exists(_.exists {
      case ValidationError.TypeMismatch("count", ParameterType.IntType, _) => true
      case _                                                               => false
    }))
  }

  pureTest("fails when int provided instead of string") {
    val schema = ServiceParameterSchema(
      testServiceAddress,
      List(ParameterDef("name", ParameterType.StringType, required = true))
    )
    val params = Map("name" -> ParameterValue.IntVal(42))

    val result = ParameterValidator.validate(schema, params)
    expect(result.isInvalid) &&
    expect(result.toEither.left.toOption.exists(_.exists {
      case ValidationError.TypeMismatch("name", ParameterType.StringType, _) => true
      case _                                                                 => false
    }))
  }

  // ---------------------------------------------------------------------------
  // Enum Validation Tests
  // ---------------------------------------------------------------------------

  pureTest("validates enum value when in allowed list") {
    val params = Map(
      "apiKey" -> ParameterValue.Secret(SecretReference("tenant-123/openai")),
      "model"  -> ParameterValue.StringVal("gpt-4")
    )

    val result = ParameterValidator.validate(openAiSchema, params)
    expect(result.isValid)
  }

  pureTest("fails when enum value not in allowed list") {
    val params = Map(
      "apiKey" -> ParameterValue.Secret(SecretReference("tenant-123/openai")),
      "model"  -> ParameterValue.StringVal("invalid-model")
    )

    val result = ParameterValidator.validate(openAiSchema, params)
    expect(result.isInvalid) &&
    expect(result.toEither.left.toOption.exists(_.exists {
      case ValidationError.InvalidEnumValue("model", "invalid-model", _) => true
      case _                                                             => false
    }))
  }

  // ---------------------------------------------------------------------------
  // Sensitive Parameter Tests
  // ---------------------------------------------------------------------------

  pureTest("validates sensitive parameter when using SecretReference") {
    val params = Map(
      "apiKey" -> ParameterValue.Secret(SecretReference("tenant-123/openai")),
      "model"  -> ParameterValue.StringVal("gpt-4")
    )

    val result = ParameterValidator.validate(openAiSchema, params)
    expect(result.isValid)
  }

  pureTest("fails when sensitive parameter uses literal value") {
    val params = Map(
      "apiKey" -> ParameterValue.StringVal("sk-plaintext-key"),
      "model"  -> ParameterValue.StringVal("gpt-4")
    )

    val result = ParameterValidator.validate(openAiSchema, params)
    expect(result.isInvalid) &&
    expect(result.toEither.left.toOption.exists(_.exists {
      case ValidationError.SensitiveNotSecret("apiKey") => true
      case _                                            => false
    }))
  }

  // ---------------------------------------------------------------------------
  // Constraint Validation Tests
  // ---------------------------------------------------------------------------

  pureTest("validates float within range constraints") {
    val params = Map(
      "apiKey"      -> ParameterValue.Secret(SecretReference("tenant-123/openai")),
      "model"       -> ParameterValue.StringVal("gpt-4"),
      "temperature" -> ParameterValue.FloatVal(1.5)
    )

    val result = ParameterValidator.validate(openAiSchema, params)
    expect(result.isValid)
  }

  pureTest("fails when float below minimum") {
    val params = Map(
      "apiKey"      -> ParameterValue.Secret(SecretReference("tenant-123/openai")),
      "model"       -> ParameterValue.StringVal("gpt-4"),
      "temperature" -> ParameterValue.FloatVal(-0.5)
    )

    val result = ParameterValidator.validate(openAiSchema, params)
    expect(result.isInvalid) &&
    expect(result.toEither.left.toOption.exists(_.exists {
      case ValidationError.OutOfRange("temperature", _, _) => true
      case _                                               => false
    }))
  }

  pureTest("fails when float above maximum") {
    val params = Map(
      "apiKey"      -> ParameterValue.Secret(SecretReference("tenant-123/openai")),
      "model"       -> ParameterValue.StringVal("gpt-4"),
      "temperature" -> ParameterValue.FloatVal(2.5)
    )

    val result = ParameterValidator.validate(openAiSchema, params)
    expect(result.isInvalid) &&
    expect(result.toEither.left.toOption.exists(_.exists {
      case ValidationError.OutOfRange("temperature", _, _) => true
      case _                                               => false
    }))
  }

  pureTest("validates string length within constraints") {
    val schema = ServiceParameterSchema(
      testServiceAddress,
      List(ParameterDef(
        "code",
        ParameterType.StringType,
        required = true,
        constraint = Some(ParameterConstraint.length(3, 10))
      ))
    )
    val params = Map("code" -> ParameterValue.StringVal("ABC123"))

    val result = ParameterValidator.validate(schema, params)
    expect(result.isValid)
  }

  pureTest("fails when string too short") {
    val schema = ServiceParameterSchema(
      testServiceAddress,
      List(ParameterDef(
        "code",
        ParameterType.StringType,
        required = true,
        constraint = Some(ParameterConstraint.length(5, 10))
      ))
    )
    val params = Map("code" -> ParameterValue.StringVal("AB"))

    val result = ParameterValidator.validate(schema, params)
    expect(result.isInvalid) &&
    expect(result.toEither.left.toOption.exists(_.exists {
      case ValidationError.LengthViolation("code", 2, _) => true
      case _                                             => false
    }))
  }

  pureTest("fails when string too long") {
    val schema = ServiceParameterSchema(
      testServiceAddress,
      List(ParameterDef(
        "code",
        ParameterType.StringType,
        required = true,
        constraint = Some(ParameterConstraint.length(1, 5))
      ))
    )
    val params = Map("code" -> ParameterValue.StringVal("ABCDEFGHIJ"))

    val result = ParameterValidator.validate(schema, params)
    expect(result.isInvalid) &&
    expect(result.toEither.left.toOption.exists(_.exists {
      case ValidationError.LengthViolation("code", 10, _) => true
      case _                                              => false
    }))
  }

  pureTest("validates string matching regex pattern") {
    val schema = ServiceParameterSchema(
      testServiceAddress,
      List(ParameterDef(
        "email",
        ParameterType.StringType,
        required = true,
        constraint = Some(ParameterConstraint.regex("^[a-z]+@[a-z]+\\.[a-z]+$"))
      ))
    )
    val params = Map("email" -> ParameterValue.StringVal("test@example.com"))

    val result = ParameterValidator.validate(schema, params)
    expect(result.isValid)
  }

  pureTest("fails when string doesn't match pattern") {
    val schema = ServiceParameterSchema(
      testServiceAddress,
      List(ParameterDef(
        "email",
        ParameterType.StringType,
        required = true,
        constraint = Some(ParameterConstraint.regex("^[a-z]+@[a-z]+\\.[a-z]+$"))
      ))
    )
    val params = Map("email" -> ParameterValue.StringVal("invalid-email"))

    val result = ParameterValidator.validate(schema, params)
    expect(result.isInvalid) &&
    expect(result.toEither.left.toOption.exists(_.exists {
      case ValidationError.PatternViolation("email", "invalid-email", _) => true
      case _                                                             => false
    }))
  }

  // ---------------------------------------------------------------------------
  // Default Value Tests
  // ---------------------------------------------------------------------------

  pureTest("applies default values for missing optional parameters") {
    val params = Map(
      "apiKey" -> ParameterValue.Secret(SecretReference("tenant-123/openai")),
      "model"  -> ParameterValue.StringVal("gpt-4")
    )

    val result = ParameterValidator.validate(openAiSchema, params)
    expect(result.isValid) &&
    expect(result.toOption.exists { validated =>
      validated.get("temperature").contains(ParameterValue.FloatVal(0.7)) &&
      validated.get("maxTokens").contains(ParameterValue.IntVal(1000))
    })
  }

  // ---------------------------------------------------------------------------
  // Unknown Parameter Tests
  // ---------------------------------------------------------------------------

  pureTest("fails on unknown parameter by default") {
    val params = Map(
      "apiKey"       -> ParameterValue.Secret(SecretReference("tenant-123/openai")),
      "model"        -> ParameterValue.StringVal("gpt-4"),
      "unknownParam" -> ParameterValue.StringVal("value")
    )

    val result = ParameterValidator.validate(openAiSchema, params)
    expect(result.isInvalid) &&
    expect(result.toEither.left.toOption.exists(_.exists {
      case ValidationError.UnknownParameter("unknownParam") => true
      case _                                                => false
    }))
  }

  pureTest("allows unknown parameters when allowUnknown is true") {
    val params = Map(
      "apiKey"       -> ParameterValue.Secret(SecretReference("tenant-123/openai")),
      "model"        -> ParameterValue.StringVal("gpt-4"),
      "unknownParam" -> ParameterValue.StringVal("value")
    )

    val result = ParameterValidator.validate(openAiSchema, params, allowUnknown = true)
    expect(result.isValid)
  }

  // ---------------------------------------------------------------------------
  // Error Accumulation Tests
  // ---------------------------------------------------------------------------

  pureTest("accumulates all validation errors") {
    val params = Map(
      "apiKey"      -> ParameterValue.StringVal("plaintext-key"), // Should be secret
      "model"       -> ParameterValue.StringVal("invalid-model"), // Invalid enum
      "temperature" -> ParameterValue.FloatVal(5.0),              // Out of range
      "unknown"     -> ParameterValue.StringVal("value")          // Unknown
    )

    val result = ParameterValidator.validate(openAiSchema, params)
    expect(result.isInvalid) &&
    expect(result.toEither.left.toOption.map(_.toList.length).getOrElse(0) >= 4)
  }

  // ---------------------------------------------------------------------------
  // Boolean Type Tests
  // ---------------------------------------------------------------------------

  pureTest("validates boolean parameter") {
    val schema = ServiceParameterSchema(
      testServiceAddress,
      List(ParameterDef("enabled", ParameterType.BoolType, required = true))
    )
    val params = Map("enabled" -> ParameterValue.BoolVal(true))

    val result = ParameterValidator.validate(schema, params)
    expect(result.isValid)
  }

  pureTest("fails when non-boolean provided for bool parameter") {
    val schema = ServiceParameterSchema(
      testServiceAddress,
      List(ParameterDef("enabled", ParameterType.BoolType, required = true))
    )
    val params = Map("enabled" -> ParameterValue.StringVal("yes"))

    val result = ParameterValidator.validate(schema, params)
    expect(result.isInvalid) &&
    expect(result.toEither.left.toOption.exists(_.exists {
      case ValidationError.TypeMismatch("enabled", ParameterType.BoolType, _) => true
      case _                                                                  => false
    }))
  }

  // ---------------------------------------------------------------------------
  // Secret Reference Bypass Type Check
  // ---------------------------------------------------------------------------

  pureTest("secret reference bypasses type check (resolved at runtime)") {
    val schema = ServiceParameterSchema(
      testServiceAddress,
      List(ParameterDef(
        "config",
        ParameterType.StringType,
        required = true,
        sensitive = true
      ))
    )
    val params = Map("config" -> ParameterValue.Secret(SecretReference("tenant/config")))

    val result = ParameterValidator.validate(schema, params)
    expect(result.isValid)
  }
