import { authenticate, issueToken, userView, validConfig, verifyPassword } from './auth.js'

class HttpError extends Error {
  constructor(status, message) { super(message); this.status = status }
}
const fail = (status, message) => { throw new HttpError(status, message) }
const reply = (data, status = 200, message = 'OK') => Response.json({ success: status < 400, message, data, timestamp: new Date().toISOString() }, {
  status, headers: { 'Cache-Control': 'no-store', 'X-Content-Type-Options': 'nosniff' }
})
const postView = row => row ? { ...row, tags: JSON.parse(row.tags) } : null
const postColumns = `posts.*,
  (SELECT count(*) FROM post_likes WHERE postId=posts.id) AS likeCount,
  (SELECT count(*) FROM comments WHERE postId=posts.id AND status='APPROVED') AS commentCount`
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
  const row = await db.prepare(`SELECT ${postColumns} FROM posts WHERE ${field} = ?${published ? " AND status = 'PUBLISHED'" : ''}`).bind(value).first()
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
    db.prepare(`SELECT ${postColumns} FROM posts${where} ORDER BY createdAt DESC, id DESC LIMIT ? OFFSET ?`).bind(...args, size, (page - 1) * size)
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
    return await findPost(db, 'id', row.id)
  } catch (error) {
    if (/UNIQUE constraint/i.test(error.message)) fail(409, 'Slug already exists. Please retry.')
    throw error
  }
}
function visitorId(request) {
  const value = request.headers.get('X-Visitor-Id')
  if (!value || !/^[A-Za-z0-9_-]{16,128}$/.test(value)) fail(400, 'Invalid visitor identity')
  return value
}
function likeQuery(db, id, visitor) {
  return db.prepare(`SELECT
    (SELECT count(*) FROM post_likes WHERE postId=posts.id) AS likeCount,
    EXISTS (SELECT 1 FROM post_likes WHERE postId=posts.id AND visitorId=?) AS liked
    FROM posts WHERE id=? AND status='PUBLISHED'`).bind(visitor, id)
}
function likeView(row) {
  if (!row) fail(404, 'Post not found')
  return { likeCount: row.likeCount, liked: Boolean(row.liked) }
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
    let match = path.match(/^\/api\/admin\/posts\/(\d+)(?:\/(publish|unpublish|likes))?$/)
    if (match) {
      const post = await findPost(db, 'id', Number(match[1]))
      if (!match[2]) {
        if (method === 'GET') return reply(post)
        if (method === 'PUT') return reply(await savePost(db, await body(request), post))
        if (method === 'DELETE') { await db.prepare('DELETE FROM posts WHERE id=?').bind(post.id).run(); return reply(null) }
      } else if (match[2] === 'likes' && method === 'DELETE') {
        await db.prepare('DELETE FROM post_likes WHERE postId=?').bind(post.id).run()
        return reply(null)
      } else if (['publish', 'unpublish'].includes(match[2]) && method === 'PATCH') {
        const published = match[2] === 'publish'; const now = new Date().toISOString()
        await db.prepare('UPDATE posts SET status=?, publishedAt=?, updatedAt=? WHERE id=?')
          .bind(published ? 'PUBLISHED' : 'DRAFT', published ? post.publishedAt || now : null, now, post.id).run()
        return reply(await findPost(db, 'id', post.id))
      }
    }
    match = path.match(/^\/api\/posts\/(\d+)\/likes$/)
    if (match && ['GET', 'PUT'].includes(method)) {
      const id = Number(match[1]); const visitor = visitorId(request)
      if (method === 'GET') return reply(likeView(await likeQuery(db, id, visitor).first()))
      const input = await body(request)
      if (typeof input.liked !== 'boolean') fail(400, 'Invalid liked value')
      const change = input.liked
        ? db.prepare("INSERT INTO post_likes(postId,visitorId,createdAt) SELECT id,?,? FROM posts WHERE id=? AND status='PUBLISHED' ON CONFLICT(postId,visitorId) DO NOTHING")
          .bind(visitor, new Date().toISOString(), id)
        : db.prepare("DELETE FROM post_likes WHERE postId=? AND visitorId=? AND EXISTS (SELECT 1 FROM posts WHERE id=? AND status='PUBLISHED')")
          .bind(id, visitor, id)
      const results = await db.batch([change, likeQuery(db, id, visitor)])
      return reply(likeView(results[1].results[0]))
    }
    match = path.match(/^\/api\/posts\/(\d+)\/comments$/)
    if (match) {
      const id = Number(match[1])
      await findPost(db, 'id', id, true)
      if (method === 'GET') return reply((await db.prepare("SELECT * FROM comments WHERE postId=? AND status='APPROVED' ORDER BY createdAt DESC, id DESC").bind(id).all()).results)
      if (method === 'POST') {
        await rateLimit(request, env, 'comment', 3)
        const input = await body(request)
        const author = required(input.author, 'author', 40); const content = required(input.content, 'content', 1000)
        const row = await db.prepare("INSERT INTO comments(postId,author,content,createdAt,status) SELECT id,?,?,?,'PENDING' FROM posts WHERE id=? AND status='PUBLISHED' RETURNING *")
          .bind(author, content, new Date().toISOString(), id).first()
        if (!row) fail(404, 'Post not found')
        return reply(row, 201)
      }
    }
    if (path === '/api/admin/comments' && method === 'GET') return reply((await db.prepare('SELECT * FROM comments ORDER BY createdAt DESC, id DESC').all()).results)
    match = path.match(/^\/api\/admin\/comments\/(\d+)(?:\/(status|reply))?$/)
    if (match) {
      const id = Number(match[1])
      if (!match[2] && method === 'DELETE') {
        const row = await db.prepare('DELETE FROM comments WHERE id=? RETURNING id').bind(id).first()
        if (!row) fail(404, 'Comment not found')
        return reply(null)
      }
      if (match[2] === 'status' && method === 'PATCH') {
        const input = await body(request)
        if (!['PENDING', 'APPROVED', 'HIDDEN'].includes(input.status)) fail(400, 'Invalid comment status')
        const row = await db.prepare('UPDATE comments SET status=? WHERE id=? RETURNING *').bind(input.status, id).first()
        if (!row) fail(404, 'Comment not found')
        return reply(row)
      }
      if (match[2] === 'reply' && method === 'PUT') {
        const input = await body(request)
        if (typeof input.content !== 'string' || input.content.length > 1000) fail(400, 'Invalid reply content')
        const content = input.content.trim() || null
        const row = await db.prepare('UPDATE comments SET reply=?, repliedAt=? WHERE id=? RETURNING *')
          .bind(content, content ? new Date().toISOString() : null, id).first()
        if (!row) fail(404, 'Comment not found')
        return reply(row)
      }
    }
    match = path.match(/^\/api\/posts\/(?:slug\/([^/]+)|([^/]+))$/)
    if (match && method === 'GET') {
      const value = match[1] || match[2]
      const field = !match[1] && /^\d+$/.test(value) ? 'id' : 'slug'
      const row = await db.prepare(`UPDATE posts SET viewCount=viewCount+1 WHERE ${field}=? AND status='PUBLISHED' RETURNING *`).bind(field === 'id' ? Number(value) : value).first()
      if (!row) fail(404, 'Post not found')
      return reply(await findPost(db, 'id', row.id, true))
    }
    fail(404, 'API route not found')
  } catch (error) {
    if (error instanceof HttpError) return reply(null, error.status, error.message)
    console.error('Blog API failed:', error.message)
    return reply(null, 500, 'Internal server error')
  }
}
