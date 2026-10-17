const COOKIE_NAME = 'user_id'
// The backend only accepts UUID version 4 (backend-requirements.md §4).
const UUID_V4_REGEX = /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i

function readCookie(name: string): string | null {
  const match = document.cookie.split('; ').find((part) => part.startsWith(name + '='))
  return match ? decodeURIComponent(match.slice(name.length + 1)) : null
}

/**
 * crypto.randomUUID() only exists on HTTPS or localhost. When the demo is opened
 * via http://<server-ip>, fall back to crypto.getRandomValues (available everywhere).
 */
function createUuidV4(): string {
  if (typeof crypto.randomUUID === 'function') return crypto.randomUUID()
  const bytes = crypto.getRandomValues(new Uint8Array(16))
  bytes[6] = (bytes[6] & 0x0f) | 0x40 // version 4
  bytes[8] = (bytes[8] & 0x3f) | 0x80 // RFC 4122 variant
  const hex = Array.from(bytes, (b) => b.toString(16).padStart(2, '0')).join('')
  return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20)}`
}

/**
 * Makes sure the browser has an anonymous user_id cookie.
 * Call once before the first API request. Throws if cookies are blocked.
 */
export function ensureUserId(): string {
  const existing = readCookie(COOKIE_NAME)
  if (existing && UUID_V4_REGEX.test(existing)) return existing

  const id = createUuidV4()
  const secure = location.protocol === 'https:' ? '; Secure' : ''
  document.cookie = `${COOKIE_NAME}=${id}; Path=/; SameSite=Lax; Max-Age=31536000${secure}`

  if (readCookie(COOKIE_NAME) !== id) {
    throw new Error('Cookies are blocked. Please enable cookies to use this app.')
  }
  return id
}

/** Same fallback, exported for the mock backend. */
export const newId = createUuidV4