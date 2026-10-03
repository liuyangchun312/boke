import { SignJWT, jwtVerify } from 'jose'

const encoder = new TextEncoder()
export const ITERATIONS = 100000
export async function passwordHash(password, salt, iterations = ITERATIONS) {
  const key = await crypto.subtle.importKey('raw', encoder.encode(password), 'PBKDF2', false, ['deriveBits'])
  const bits = await crypto.subtle.deriveBits({ name: 'PBKDF2', hash: 'SHA-256', salt: encoder.encode(salt), iterations }, key, 256)
  return Array.from(new Uint8Array(bits), byte => byte.toString(16).padStart(2, '0')).join('')
}
export function validConfig(env) {
  const parts = (env.BLOG_ADMIN_PASSWORD_HASH || '').split(':')
  return encoder.encode(env.BLOG_JWT_SECRET || '').length >= 32 &&
    typeof env.BLOG_ADMIN_USERNAME === 'string' && env.BLOG_ADMIN_USERNAME.trim() &&
    parts[0] === String(ITERATIONS) && /^[a-f0-9]{32}$/.test(parts[1]) && /^[a-f0-9]{64}$/.test(parts[2])
}
export function userView(env) {
  return { username: env.BLOG_ADMIN_USERNAME, displayName: env.BLOG_ADMIN_DISPLAY_NAME || env.BLOG_ADMIN_USERNAME, role: 'ADMIN' }
}
export async function verifyPassword(password, env) {
  const [iterations, salt, expected] = env.BLOG_ADMIN_PASSWORD_HASH.split(':')
  const actual = await passwordHash(password, salt, Number(iterations))
  let difference = 0
  for (let i = 0; i < expected.length; i++) difference |= actual.charCodeAt(i) ^ expected.charCodeAt(i)
  return difference === 0
}
async function signingKey(env) {
  // Rotating either the password hash or JWT secret also invalidates existing sessions.
  return new Uint8Array(await crypto.subtle.digest('SHA-256', encoder.encode(`${env.BLOG_JWT_SECRET}:${env.BLOG_ADMIN_PASSWORD_HASH}`)))
}
export async function issueToken(env) {
  return new SignJWT({ role: 'ADMIN' }).setProtectedHeader({ alg: 'HS256' })
    .setSubject(env.BLOG_ADMIN_USERNAME).setIssuer('blog-cloudflare').setAudience('blog-admin')
    .setIssuedAt().setExpirationTime('1h').sign(await signingKey(env))
}
export async function authenticate(request, env) {
  try {
    const authorization = request.headers.get('Authorization') || ''
    if (!authorization.startsWith('Bearer ')) return false
    const { payload } = await jwtVerify(authorization.slice(7), await signingKey(env), {
      algorithms: ['HS256'], issuer: 'blog-cloudflare', audience: 'blog-admin', requiredClaims: ['exp', 'iat', 'sub']
    })
    return payload.sub === env.BLOG_ADMIN_USERNAME && payload.role === 'ADMIN'
  } catch { return false }
}
