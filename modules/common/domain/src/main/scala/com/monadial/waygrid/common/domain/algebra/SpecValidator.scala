package com.monadial.waygrid.common.domain.algebra

import cats.data.ValidatedNel
import cats.syntax.all.*
import com.monadial.waygrid.common.domain.algebra.ParameterValidator.ValidationError
import com.monadial.waygrid.common.domain.model.parameter.*
import com.monadial.waygrid.common.domain.model.traversal.spec.Spec
import com.monadial.waygrid.common.domain.value.Address.ServiceAddress

/**
 * Validates parameters in a Spec against service schemas.
 *
 * This validator enables checking all node parameters in a Spec before
 * DAG compilation, ensuring that:
 * - Required parameters are present for each service
 * - Parameter types match their definitions
 * - Constraints are satisfied
 * - Sensitive parameters use SecretReference
 *
 * ==Example==
 * {{{
 * val schemas = Map(
 *   ServiceAddress("waygrid://processor/openai") -> openaiSchema,
 *   ServiceAddress("waygrid://destination/webhook") -> webhookSchema
 * )
 *
 * SpecValidator.validate(spec, schemas) match
 *   case Valid(()) => // All parameters are valid
 *   case Invalid(errors) => // Handle validation errors
 * }}}
 *
 * ==Validation Modes==
 * - '''Strict''': All services with parameters must have schemas, unknown services are errors
 * - '''Lenient''': Services without schemas are skipped (only validate what we have schemas for)
 */
object SpecValidator:

  /** Error indicating validation failed for a specific service */
  final case class ServiceValidationError(
    address: ServiceAddress,
    errors: List[ValidationError]
  ):
    def messages: List[String] = errors.map(_.message)

    override def toString: String =
      s"ServiceValidationError(${address.toDescriptor.show}, errors=${errors.map(_.message).mkString(", ")})"

  /** Error when a service's schema is missing in strict mode */
  final case class MissingSchemaError(address: ServiceAddress):
    def message: String = s"No schema found for service: ${address.toDescriptor.show}"

  /** Combined result of spec validation */
  enum SpecValidationError:
    case ParameterErrors(errors: List[ServiceValidationError])
    case SchemaErrors(errors: List[MissingSchemaError])
    case Combined(parameterErrors: List[ServiceValidationError], schemaErrors: List[MissingSchemaError])

    def messages: List[String] = this match
      case ParameterErrors(errs)  => errs.flatMap(_.messages)
      case SchemaErrors(errs)     => errs.map(_.message)
      case Combined(pErrs, sErrs) => pErrs.flatMap(_.messages) ++ sErrs.map(_.message)

  type SpecValidationResult = ValidatedNel[SpecValidationError, Unit]

  /**
   * Validate all parameters in a Spec against the provided schemas.
   *
   * Runs in lenient mode - services without schemas are skipped.
   *
   * @param spec The spec to validate
   * @param schemas Map of service addresses to their parameter schemas
   * @return Validated result with all errors accumulated
   */
  def validate(
    spec: Spec,
    schemas: Map[ServiceAddress, ServiceParameterSchema]
  ): SpecValidationResult =
    validateLenient(spec, schemas)

  /**
   * Validate in lenient mode - only validate services that have schemas.
   * Services without schemas are silently skipped.
   */
  def validateLenient(
    spec: Spec,
    schemas: Map[ServiceAddress, ServiceParameterSchema]
  ): SpecValidationResult =
    val parametersByService = spec.collectParameters

    val errors = parametersByService.flatMap { case (address, params) =>
      schemas.get(address) match
        case Some(schema) =>
          ParameterValidator.validate(schema, params) match
            case cats.data.Validated.Valid(_) => None
            case cats.data.Validated.Invalid(errs) =>
              Some(ServiceValidationError(address, errs.toList))
        case None =>
          // Lenient: skip services without schemas
          None
    }

    if errors.isEmpty then ().validNel
    else SpecValidationError.ParameterErrors(errors).invalidNel

  /**
   * Validate in strict mode - all parameterized services must have schemas.
   * Returns errors for services that have parameters but no schema.
   */
  def validateStrict(
    spec: Spec,
    schemas: Map[ServiceAddress, ServiceParameterSchema]
  ): SpecValidationResult =
    val parametersByService = spec.collectParameters

    var parameterErrors: List[ServiceValidationError] = Nil
    var schemaErrors: List[MissingSchemaError]        = Nil

    parametersByService.foreach { case (address, params) =>
      schemas.get(address) match
        case Some(schema) =>
          ParameterValidator.validate(schema, params) match
            case cats.data.Validated.Valid(_) => ()
            case cats.data.Validated.Invalid(errs) =>
              parameterErrors = ServiceValidationError(address, errs.toList) :: parameterErrors
        case None if params.nonEmpty =>
          schemaErrors = MissingSchemaError(address) :: schemaErrors
        case None =>
          // No parameters, no schema needed
          ()
    }

    (parameterErrors.isEmpty, schemaErrors.isEmpty) match
      case (true, true)  => ().validNel
      case (false, true) => SpecValidationError.ParameterErrors(parameterErrors.reverse).invalidNel
      case (true, false) => SpecValidationError.SchemaErrors(schemaErrors.reverse).invalidNel
      case (false, false) =>
        SpecValidationError.Combined(parameterErrors.reverse, schemaErrors.reverse).invalidNel

  /**
   * Validate parameters for a single service address.
   * Useful for incremental validation during spec construction.
   */
  def validateService(
    address: ServiceAddress,
    params: Map[String, ParameterValue],
    schema: ServiceParameterSchema
  ): ValidatedNel[ServiceValidationError, Unit] =
    ParameterValidator.validate(schema, params) match
      case cats.data.Validated.Valid(_) => ().validNel
      case cats.data.Validated.Invalid(errs) =>
        ServiceValidationError(address, errs.toList).invalidNel
