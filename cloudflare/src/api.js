import { authenticate, issueToken, userView, validConfig, verifyPassword } from './auth.js'

class HttpError extends Error {
  constructor(status, message) { super(message); this.status = status }
}
const fail = (status, message) => { throw new HttpError(status, message) }
const reply = (data, status = 200, message = 'OK') => Response.json({ success: status < 400, message, data, timestamp: new Date().toISOString() }, {
  status, headers: { 'Cache-Control': 'no-store', 'X-Content-Type-Options': 'nosniff' }
})
const postView = row => row ? { ...row, tags: JSON.parse(row.tags) } : null
const required = (value, name, max) => {
  if (typeof value !== 'string' || !value.trim() || value.length > max) fail(400, `Invalid ${name}`)
  return value.trim()
}
const optional = (value, name, max) => value == null || value === '' ? '' : required(value, name, max)
const slugify = value => value.toLowerCase().trim().replace(/[^\p{L}\p{N}]+/gu, '-').replace(/^-|-$/g, '') || 'post'
async function body(request) {
  if (!request.headers.get('Content-Type')?.includes('application/json')) fail(415, 'JSON required')
  const reader = request.body?.getReader()
  if (!reader) fail(400, 'JSON required')
  const chunks = []; let size = 0
  while (true) {
    const { done, value } = await reader.read()
    if (done) break
    size += value.length
    if (size > 524288) { await reader.cancel(); fail(413, 'Request too large') }
    chunks.push(value)
  }
  const bytes = new Uint8Array(size); let offset = 0
  for (const chunk of chunks) { bytes.set(chunk, offset); offset += chunk.length }
  try {
    const result = JSON.parse(new TextDecoder().decode(bytes))
    if (!result || Array.isArray(result) || typeof result !== 'object') fail(400, 'Invalid JSON')
    return result
  } catch { fail(400, 'Invalid JSON') }
}
async function rateLimit(request, env, scope, max) {
  const ip = request.headers.get('CF-Connecting-IP') || 'local'
  const digest = await crypto.subtle.digest('SHA-256', new TextEncoder().encode(`${env.BLOG_JWT_SECRET}:${ip}`))
  const key = `${scope}:${Array.from(new Uint8Array(digest), b => b.toString(16).padStart(2, '0')).join('')}`
  const window = Math.floor(Date.now() / 60000)
  const result = await env.DB.prepare(`INSERT INTO rate_limits(key, window, hits) VALUES (?, ?, 1)
    ON CONFLICT(key) DO UPDATE SET window = excluded.window,
    hits = CASE WHEN rate_limits.window = excluded.window THEN rate_limits.hits + 1 ELSE 1 END RETURNING hits`).bind(key, window).first()
  await env.DB.prepare('DELETE FROM rate_limits WHERE window < ?').bind(window - 10).run()
  if (result.hits > max) fail(429, 'Too many requests. Please try again later.')
}
async function findPost(db, field, value, published = false) {
  const row = await db.prepare(`SELECT * FROM posts WHERE ${field} = ?${published ? " AND status = 'PUBLISHED'" : ''}`).bind(value).first()
  if (!row) fail(404, 'Post not found')
  return postView(row)
}
async function listPosts(db, url, admin) {
  const page = Math.min(1000000, Math.max(1, Number.parseInt(url.searchParams.get('page')) || 1))
  const size = Math.min(100, Math.max(1, Number.parseInt(url.searchParams.get('size')) || 10))
  const filters = admin ? [] : ["status = 'PUBLISHED'"]; const args = []
  for (const field of ['keyword', 'category', 'tag']) {
    const value = (url.searchParams.get(field) || '').trim().toLowerCase()
    if (!value) continue
    if (value.length > 500) fail(400, 'Filter too long')
    if (field === 'keyword') {
      filters.push('(instr(lower(title), ?) > 0 OR instr(lower(excerpt), ?) > 0 OR instr(lower(content), ?) > 0)')
      args.push(value, value, value)
    } else if (field === 'category') { filters.push('lower(category) = ?'); args.push(value) }
    else { filters.push('EXISTS (SELECT 1 FROM json_each(posts.tags) WHERE lower(value) = ?)'); args.push(value) }
  }
  const where = filters.length ? ` WHERE ${filters.join(' AND ')}` : ''
  const results = await db.batch([
    db.prepare(`SELECT count(*) AS total FROM posts${where}`).bind(...args),
    db.prepare(`SELECT * FROM posts${where} ORDER BY createdAt DESC, id DESC LIMIT ? OFFSET ?`).bind(...args, size, (page - 1) * size)
  ])
  const total = results[0].results[0].total
  return { items: results[1].results.map(postView), page, size, total, totalPages: Math.ceil(total / size) }
}
async function savePost(db, input, existing = null) {
  const title = required(input.title, 'title', 300)
  const category = required(input.category, 'category', 100)
  required(input.content, 'content', 200000)
  const content = input.content
  const excerpt = optional(input.excerpt, 'excerpt', 2000) || content.replace(/<[^>]*>/g, '').replace(/\s+/g, ' ').trim().slice(0, 160)
  const requested = optional(input.slug, 'slug', 300)
  const base = !requested && existing ? existing.slug : slugify(requested || title)
  let slug = base
  for (let suffix = 2; ; suffix++) {
    const other = await db.prepare('SELECT id FROM posts WHERE slug = ?').bind(slug).first()
    if (!other || other.id === existing?.id) break
    slug = `${base}-${suffix}`
  }
  const status = input.status ?? 'DRAFT'
  if (!['DRAFT', 'PUBLISHED'].includes(status)) fail(400, 'Invalid status')
  if (input.tags != null && (!Array.isArray(input.tags) || input.tags.length > 30)) fail(400, 'Invalid tags')
  const tags = JSON.stringify([...new Set((input.tags || []).map(t => required(t, 'tag', 100)))])
  const coverImage = optional(input.coverImage, 'coverImage', 2000) || null
  if (coverImage && !/^https?:\/\//i.test(coverImage) && !/^\/(?!\/)/.test(coverImage)) fail(400, 'Invalid cover image URL')
  const now = new Date().toISOString()
  const publishedAt = status === 'PUBLISHED' ? existing?.publishedAt || now : null
  const args = [title, slug, excerpt, content, category, tags, coverImage, status, now, publishedAt]
  try {
    const row = existing
      ? await db.prepare('UPDATE posts SET title=?, slug=?, excerpt=?, content=?, category=?, tags=?, coverImage=?, status=?, updatedAt=?, publishedAt=? WHERE id=? RETURNING *').bind(...args, existing.id).first()
      : await db.prepare('INSERT INTO posts(title,slug,excerpt,content,category,tags,coverImage,status,updatedAt,publishedAt,createdAt) VALUES (?,?,?,?,?,?,?,?,?,?,?) RETURNING *').bind(...args, now).first()
    if (!row) fail(404, 'Post not found')
    return postView(row)
  } catch (error) {
    if (/UNIQUE constraint/i.test(error.message)) fail(409, 'Slug already exists. Please retry.')
    throw error
  }
}
export async function handleApi(request, env) {
  try {
    if (!env.DB || !validConfig(env)) fail(503, 'Service is not configured')
    const url = new URL(request.url)
    let path
    try { path = decodeURIComponent(url.pathname).replace(/\/$/, '') } catch { fail(400, 'Invalid URL') }
    const method = request.method
    if (!['GET', 'POST', 'PUT', 'PATCH', 'DELETE'].includes(method)) fail(405, 'Method not allowed')
    if (method !== 'GET') {
      const origin = request.headers.get('Origin')
      if (origin && origin !== url.origin) fail(403, 'Cross-origin writes are not allowed')
    }
    const db = env.DB
    const admin = path.startsWith('/api/admin/') || path === '/api/admin' || path === '/api/auth/me'
    if (admin && !await authenticate(request, env)) fail(401, 'Authentication required')
    if (path === '/api/auth/login' && method === 'POST') {
      await rateLimit(request, env, 'login', 5)
      const input = await body(request)
      const username = required(input.username, 'username', 100)
      const password = required(input.password, 'password', 1024)
      const matches = await verifyPassword(input.password, env)
      if (!matches || username !== env.BLOG_ADMIN_USERNAME) fail(401, 'Invalid username or password')
      return reply({ token: await issueToken(env), tokenType: 'Bearer', expiresIn: 3600, user: userView(env) })
    }
    if (path === '/api/auth/me' && method === 'GET') return reply(userView(env))
    if (['/api/posts', '/api/admin/posts'].includes(path)) {
      if (method === 'GET') return reply(await listPosts(db, url, admin))
      if (admin && method === 'POST') return reply(await savePost(db, await body(request)), 201)
    }
    if (['/api/categories', '/api/tags'].includes(path) && method === 'GET') {
      const sql = path.endsWith('categories')
        ? "SELECT DISTINCT category AS value FROM posts WHERE status='PUBLISHED' ORDER BY value COLLATE NOCASE"
        : "SELECT DISTINCT json_each.value AS value FROM posts, json_each(posts.tags) WHERE status='PUBLISHED' ORDER BY value COLLATE NOCASE"
      return reply((await db.prepare(sql).all()).results.map(row => row.value))
    }
    let match = path.match(/^\/api\/admin\/posts\/(\d+)(?:\/(publish|unpublish))?$/)
    if (match) {
      const post = await findPost(db, 'id', Number(match[1]))
      if (!match[2]) {
        if (method === 'GET') return reply(post)
        if (method === 'PUT') return reply(await savePost(db, await body(request), post))
        if (method === 'DELETE') { await db.prepare('DELETE FROM posts WHERE id=?').bind(post.id).run(); return reply(null) }
      } else if (method === 'PATCH') {
        const published = match[2] === 'publish'; const now = new Date().toISOString()
        return reply(postView(await db.prepare('UPDATE posts SET status=?, publishedAt=?, updatedAt=? WHERE id=? RETURNING *')
          .bind(published ? 'PUBLISHED' : 'DRAFT', published ? post.publishedAt || now : null, now, post.id).first()))
      }
    }
    match = path.match(/^\/api\/posts\/(\d+)\/comments$/)
    if (match) {
      const id = Number(match[1])
      await findPost(db, 'id', id, true)
      if (method === 'GET') return reply((await db.prepare('SELECT * FROM comments WHERE postId=? ORDER BY createdAt DESC, id DESC').bind(id).all()).results)
      if (method === 'POST') {
        await rateLimit(request, env, 'comment', 3)
        const input = await body(request)
        const author = required(input.author, 'author', 40); const content = required(input.content, 'content', 1000)
        const row = await db.prepare("INSERT INTO comments(postId,author,content,createdAt) SELECT id,?,?,? FROM posts WHERE id=? AND status='PUBLISHED' RETURNING *")
          .bind(author, content, new Date().toISOString(), id).first()
        if (!row) fail(404, 'Post not found')
        return reply(row, 201)
      }
    }
    if (path === '/api/admin/comments' && method === 'GET') return reply((await db.prepare('SELECT * FROM comments ORDER BY createdAt DESC, id DESC').all()).results)
    match = path.match(/^\/api\/admin\/comments\/(\d+)$/)
    if (match && method === 'DELETE') {
      const row = await db.prepare('DELETE FROM comments WHERE id=? RETURNING id').bind(Number(match[1])).first()
      if (!row) fail(404, 'Comment not found')
      return reply(null)
    }
    match = path.match(/^\/api\/posts\/(?:slug\/([^/]+)|([^/]+))$/)
    if (match && method === 'GET') {
      const value = match[1] || match[2]
      const field = !match[1] && /^\d+$/.test(value) ? 'id' : 'slug'
      const row = await db.prepare(`UPDATE posts SET viewCount=viewCount+1 WHERE ${field}=? AND status='PUBLISHED' RETURNING *`).bind(field === 'id' ? Number(value) : value).first()
      if (!row) fail(404, 'Post not found')
      return reply(postView(row))
    }
    fail(404, 'API route not found')
  } catch (error) {
    if (error instanceof HttpError) return reply(null, error.status, error.message)
    console.error('Blog API failed:', error.message)
    return reply(null, 500, 'Internal server error')
  }
}
