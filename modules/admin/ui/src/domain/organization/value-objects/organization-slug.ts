/**
 * OrganizationSlug - URL-friendly identifier for an organization
 * Example: "acme-corp", "my-startup"
 */
export type OrganizationSlug = string & { readonly __organizationSlug: unique symbol }

const SLUG_REGEX = /^[a-z0-9]+(?:-[a-z0-9]+)*$/

export function createOrganizationSlug(value: string): OrganizationSlug {
  const normalized = value.toLowerCase().trim()
  if (!SLUG_REGEX.test(normalized)) {
    throw new Error(`Invalid OrganizationSlug: ${value}. Must be lowercase alphanumeric with hyphens.`)
  }
  return normalized as OrganizationSlug
}

export function isValidOrganizationSlug(value: string): value is OrganizationSlug {
  return SLUG_REGEX.test(value.toLowerCase())
}
