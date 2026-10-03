import { test } from 'node:test'
import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import { Miniflare } from 'miniflare'
import { handleApi } from '../src/api.js'
import { passwordHash, ITERATIONS } from '../src/auth.js'

test('D1 API: authentication, publishing, search, moderation and limits', async t => {
  const mf = new Miniflare({ modules: true, script: 'export default { fetch() { return new Response("OK") } }', d1Databases: ['DB'] })
  t.after(() => mf.dispose())
  const DB = await mf.getD1Database('DB')
  const migration = await readFile(new URL('../migrations/0001_blog.sql', import.meta.url), 'utf8')
  await DB.batch(migration.split(';').filter(s => s.trim()).map(s => DB.prepare(s)))
  const salt = 'a'.repeat(32)
  const env = { DB, BLOG_ADMIN_USERNAME: 'owner', BLOG_ADMIN_DISPLAY_NAME: 'Owner', BLOG_JWT_SECRET: 'x'.repeat(48),
    BLOG_ADMIN_PASSWORD_HASH: `${ITERATIONS}:${salt}:${await passwordHash('test-password-123', salt)}` }
  let token
  async function call(path, method = 'GET', data, authenticated = false, extra = {}) {
    const response = await handleApi(new Request(`https://blog.example/api${path}`, { method,
      headers: { ...(data ? { 'Content-Type': 'application/json' } : {}), ...(authenticated ? { Authorization: `Bearer ${token}` } : {}), ...extra },
      body: data ? JSON.stringify(data) : undefined }), env)
    return { status: response.status, ...(await response.json()) }
  }
  assert.equal((await call('/admin/posts')).status, 401)
  assert.equal((await call('/auth/login', 'POST', { username: 'owner', password: 'wrong' })).status, 401)
  const login = await call('/auth/login', 'POST', { username: 'owner', password: 'test-password-123' })
  assert.equal(login.status, 200); token = login.data.token
  assert.equal((await call('/auth/me', 'GET', null, true)).data.role, 'ADMIN')
  const input = { title: 'First post', slug: '123', category: 'Notes', content: '# Markdown\n\n  keep whitespace\n', tags: ['Vue', 'Vue'] }
  const created = await call('/admin/posts', 'POST', input, true)
  assert.equal(created.status, 201)
  const id = created.data.id
  assert.equal(created.data.content, input.content)
  assert.deepEqual(created.data.tags, ['Vue'])
  assert.equal((await call('/posts')).data.total, 0)
  assert.equal((await call('/posts/slug/123')).status, 404)
  assert.equal((await call(`/posts/${id}/comments`, 'POST', { author: 'Reader', content: 'Hello' })).status, 404)
  assert.equal((await call(`/admin/posts/${id}/publish`, 'PATCH', null, true)).status, 200)
  assert.equal((await call('/posts/slug/123')).data.viewCount, 1)
  assert.equal((await call('/posts?keyword=markdown&category=notes&tag=vue')).data.total, 1)
  assert.deepEqual((await call('/categories')).data, ['Notes'])
  assert.deepEqual((await call('/tags')).data, ['Vue'])
  assert.equal((await call('/posts?page=2&size=1')).data.items.length, 0)
  const comment = await call(`/posts/${id}/comments`, 'POST', { author: 'Reader', content: 'Hello' })
  assert.equal(comment.status, 201)
  assert.equal((await call('/admin/comments', 'GET', null, true)).data.length, 1)
  assert.equal((await call(`/admin/comments/${comment.data.id}`, 'DELETE', null, true)).status, 200)
  assert.equal((await call(`/posts/${id}/comments`, 'POST', { author: 'a'.repeat(41), content: 'Hello' })).status, 400)
  assert.equal((await call(`/posts/${id}/comments`, 'POST', { author: 'Reader', content: 'Hello' })).status, 201)
  assert.equal((await call(`/posts/${id}/comments`, 'POST', { author: 'Reader', content: 'Hello' })).status, 429)
  assert.equal((await call('/admin/posts', 'POST', input, true, { Origin: 'https://evil.example' })).status, 403)
  const updated = await call(`/admin/posts/${id}`, 'PUT', { ...input, slug: '', title: 'Renamed', status: 'PUBLISHED' }, true)
  assert.equal(updated.data.slug, '123')
  const duplicate = await call('/admin/posts', 'POST', input, true)
  assert.equal(duplicate.data.slug, '123-2')
  assert.equal((await call(`/admin/posts/${id}/unpublish`, 'PATCH', null, true)).data.status, 'DRAFT')
  assert.equal((await call('/posts/slug/123')).status, 404)
  assert.deepEqual((await call('/tags')).data, [])
  assert.equal((await call(`/admin/posts/${id}`, 'DELETE', null, true)).status, 200)
  assert.equal((await DB.prepare('SELECT count(*) AS n FROM comments').first()).n, 0)
  const originalHash = env.BLOG_ADMIN_PASSWORD_HASH
  env.BLOG_ADMIN_PASSWORD_HASH = `${ITERATIONS}:${salt}:${await passwordHash('new-password-123', salt)}`
  assert.equal((await call('/auth/me', 'GET', null, true)).status, 401)
  env.BLOG_ADMIN_PASSWORD_HASH = originalHash
  token = `${token.slice(0, -10)}AAAAAAAAAA`
  assert.equal((await call('/auth/me', 'GET', null, true)).status, 401)
  for (let i = 0; i < 3; i++) await call('/auth/login', 'POST', { username: 'owner', password: 'wrong' })
  assert.equal((await call('/auth/login', 'POST', { username: 'owner', password: 'test-password-123' })).status, 429)
  env.BLOG_JWT_SECRET = ''
  assert.equal((await call('/posts')).status, 503)
})
