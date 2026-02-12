/**
 * ULID - Universally Unique Lexicographically Sortable Identifier
 * Mirrors: com.monadial.waygrid.common.domain.value.ULIDValue
 */
export type ULID = string & { readonly __brand: unique symbol }

export function isValidULID(value: string): value is ULID {
  return /^[0-9A-HJKMNP-TV-Z]{26}$/.test(value)
}

export function createULID(value: string): ULID {
  if (!isValidULID(value)) {
    throw new Error(`Invalid ULID: ${value}`)
  }
  return value
}
