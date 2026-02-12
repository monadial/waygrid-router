/**
 * ProjectSlug - URL-friendly identifier for a project
 * Example: "backend-api", "payment-service"
 */
export type ProjectSlug = string & { readonly __projectSlug: unique symbol }

const SLUG_REGEX = /^[a-z0-9]+(?:-[a-z0-9]+)*$/

export function createProjectSlug(value: string): ProjectSlug {
  const normalized = value.toLowerCase().trim()
  if (!SLUG_REGEX.test(normalized)) {
    throw new Error(`Invalid ProjectSlug: ${value}`)
  }
  return normalized as ProjectSlug
}
