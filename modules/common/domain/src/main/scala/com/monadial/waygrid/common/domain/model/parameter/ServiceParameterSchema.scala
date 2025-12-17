package com.monadial.waygrid.common.domain.model.parameter

import com.monadial.waygrid.common.domain.value.Address.ServiceAddress

/**
 * Parameter schema declared by a service when registering with topology.
 *
 * This schema defines what parameters a processor or destination accepts,
 * enabling validation of specs before they are compiled into DAGs.
 *
 * ==Example: OpenAI Processor==
 * {{{
 * ServiceParameterSchema(
 *   serviceAddress = ServiceAddress("waygrid://processor/openai"),
 *   parameters = List(
 *     ParameterDef.requiredSecret("apiKey", "OpenAI API key"),
 *     ParameterDef.requiredEnum("model", "gpt-4", "gpt-3.5-turbo", "gpt-4-turbo"),
 *     ParameterDef.optionalFloat("temperature", default = 0.7, min = 0.0, max = 2.0),
 *     ParameterDef.optionalInt("maxTokens", default = 1000)
 *   )
 * )
 * }}}
 *
 * ==Example: WebSocket Destination==
 * {{{
 * ServiceParameterSchema(
 *   serviceAddress = ServiceAddress("waygrid://destination/websocket"),
 *   parameters = List(
 *     ParameterDef.requiredString("channelId", "WebSocket channel identifier")
 *   )
 * )
 * }}}
 *
 * ==Example: HTTP Webhook Destination==
 * {{{
 * ServiceParameterSchema(
 *   serviceAddress = ServiceAddress("waygrid://destination/webhook"),
 *   parameters = List(
 *     ParameterDef.requiredString("url", "Target webhook URL"),
 *     ParameterDef.optionalString("method", "POST"),
 *     ParameterDef.requiredEnum("authType", "none", "bearer", "basic", "header"),
 *     ParameterDef("authValue", ParameterType.StringType, required = false, sensitive = true,
 *                  description = Some("Authentication token or credentials"))
 *   )
 * )
 * }}}
 *
 * @param serviceAddress The service this schema belongs to
 * @param parameters     List of parameter definitions
 */
case class ServiceParameterSchema(
  serviceAddress: ServiceAddress,
  parameters: List[ParameterDef]
):
  /** Get a parameter definition by name */
  def get(name: String): Option[ParameterDef] =
    parameters.find(_.name == name)

  /** Get all required parameter names */
  def requiredNames: Set[String] =
    parameters.filter(_.required).map(_.name).toSet

  /** Get all sensitive parameter names */
  def sensitiveNames: Set[String] =
    parameters.filter(_.sensitive).map(_.name).toSet

  /** Check if a parameter exists */
  def has(name: String): Boolean =
    parameters.exists(_.name == name)

object ServiceParameterSchema:
  /** Empty schema for services with no parameters */
  def empty(serviceAddress: ServiceAddress): ServiceParameterSchema =
    ServiceParameterSchema(serviceAddress, Nil)
