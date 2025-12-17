package com.monadial.waygrid.common.domain.model.parameter

/**
 * Reference to a secret stored in the secure store.
 *
 * Secrets are never stored in the Spec/DAG - only references to them.
 * The actual secret value is resolved at runtime, just before service invocation.
 *
 * ==Path Format==
 * Paths follow a hierarchical structure:
 * {{{
 * tenant-id/category/name
 *
 * Examples:
 *   "tenant-123/api-keys/openai"
 *   "tenant-123/webhooks/stripe-signing-secret"
 *   "global/system/encryption-key"
 * }}}
 *
 * ==Field Access==
 * If a secret is stored as a JSON object, you can access specific fields:
 * {{{
 * // Secret stored as: {"apiKey": "sk-123", "orgId": "org-456"}
 * SecretReference("tenant-123/openai", field = Some("apiKey"))
 * }}}
 *
 * ==Version Pinning==
 * By default, the latest version is used. You can pin to a specific version:
 * {{{
 * SecretReference("tenant-123/api-keys/openai", version = Some(SecretVersion("v2")))
 * }}}
 *
 * @param path    Path to the secret in the secure store
 * @param field   Optional field name if the secret is a JSON object
 * @param version Optional version (None = latest)
 */
case class SecretReference(
  path: SecretPath,
  field: Option[String] = None,
  version: Option[SecretVersion] = None
)

object SecretReference:
  def apply(path: String): SecretReference =
    SecretReference(SecretPath(path), None, None)

  def apply(path: String, field: String): SecretReference =
    SecretReference(SecretPath(path), Some(field), None)

/**
 * Path to a secret in the secure store.
 *
 * Format: `tenant-id/category/name` or `global/category/name`
 */
opaque type SecretPath = String

object SecretPath:
  def apply(path: String): SecretPath = path

  extension (p: SecretPath)
    def value: String          = p
    def segments: List[String] = p.split('/').toList

/**
 * Version identifier for a secret.
 *
 * Used for version pinning when you need a specific version
 * rather than the latest.
 */
opaque type SecretVersion = String

object SecretVersion:
  def apply(version: String): SecretVersion = version

  extension (v: SecretVersion)
    def value: String = v
