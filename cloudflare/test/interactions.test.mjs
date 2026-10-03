import { test } from 'node:test'
import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import { Miniflare } from 'miniflare'
import { handleApi } from '../src/api.js'
import { issueToken, ITERATIONS } from '../src/auth.js'

async function fixture(t, beforeInteractionsMigration) {
  const mf = new Miniflare({ modules: true, script: 'export default { fetch() { return new Response("OK") } }', d1Databases: ['DB'] })
  t.after(() => mf.dispose())
  const DB = await mf.getD1Database('DB')
  async function migrate(file) {
    const sql = await readFile(new URL(`../migrations/${file}`, import.meta.url), 'utf8')
    await DB.batch(sql.split(';').filter(s => s.trim()).map(s => DB.prepare(s)))
  }
  await migrate('0001_blog.sql')
  if (beforeInteractionsMigration) await beforeInteractionsMigration(DB)
  await migrate('0002_interactions.sql')
  const env = { DB, BLOG_ADMIN_USERNAME: 'owner', BLOG_ADMIN_DISPLAY_NAME: 'Owner', BLOG_JWT_SECRET: 'x'.repeat(48),
    BLOG_ADMIN_PASSWORD_HASH: `${ITERATIONS}:${'a'.repeat(32)}:${'b'.repeat(64)}` }
  const token = await issueToken(env)
  async function call(path, method = 'GET', data, authenticated = false, extra = {}) {
    const response = await handleApi(new Request(`https://blog.example/api${path}`, { method,
      headers: { ...(data !== undefined ? { 'Content-Type': 'application/json' } : {}), ...(authenticated ? { Authorization: `Bearer ${token}` } : {}), ...extra },
      body: data !== undefined ? JSON.stringify(data) : undefined }), env)
    return { status: response.status, ...(await response.json()) }
  }
  async function createPost(status = 'PUBLISHED') {
    const result = await call('/admin/posts', 'POST', { title: 'Interaction post', category: 'Notes', content: 'Post content', status }, true)
    assert.equal(result.status, 201)
    return result.data
  }
  return { DB, env, call, createPost }
}

const visitor = { 'X-Visitor-Id': 'reader_0123456789' }
const otherVisitor = { 'X-Visitor-Id': 'reader_9876543210' }

test('likes persist independently per visitor and repeated PUTs are idempotent', async t => {
  const { DB, env, call, createPost } = await fixture(t)
  const post = await createPost()
  assert.equal(post.likeCount, 0)
  assert.equal(post.commentCount, 0)
  assert.deepEqual((await call(`/posts/${post.id}/likes`, 'GET', undefined, false, visitor)).data, { likeCount: 0, liked: false })
  for (let i = 0; i < 2; i++) {
    const liked = await call(`/posts/${post.id}/likes`, 'PUT', { liked: true }, false, visitor)
    assert.equal(liked.status, 200)
    assert.deepEqual(liked.data, { likeCount: 1, liked: true })
  }
  assert.deepEqual((await call(`/posts/${post.id}/likes`, 'GET', undefined, false, otherVisitor)).data, { likeCount: 1, liked: false })
  await call(`/posts/${post.id}/likes`, 'PUT', { liked: true }, false, otherVisitor)
  const persistedResponse = await handleApi(new Request(`https://blog.example/api/posts/${post.id}/likes`, { headers: visitor }), { ...env })
  assert.deepEqual((await persistedResponse.json()).data, { likeCount: 2, liked: true })
  assert.equal((await DB.prepare('SELECT count(*) AS n FROM post_likes WHERE postId=?').bind(post.id).first()).n, 2)
  assert.equal((await call(`/posts/${post.id}`)).data.likeCount, 2)
  assert.equal((await call('/posts')).data.items[0].likeCount, 2)
  assert.equal((await call('/admin/posts', 'GET', undefined, true)).data.items[0].likeCount, 2)
  for (let i = 0; i < 2; i++) {
    assert.deepEqual((await call(`/posts/${post.id}/likes`, 'PUT', { liked: false }, false, visitor)).data, { likeCount: 1, liked: false })
  }
  assert.equal((await call(`/admin/posts/${post.id}`, 'PUT', { ...post, title: 'Changed title' }, true)).data.likeCount, 1)
})

test('likes reject invalid identities and non-boolean input and keep drafts private', async t => {
  const { call, createPost } = await fixture(t)
  const post = await createPost()
  for (const headers of [{}, { 'X-Visitor-Id': 'short' }, { 'X-Visitor-Id': 'x'.repeat(129) }, { 'X-Visitor-Id': 'has.invalid.chars' }]) {
    assert.equal((await call(`/posts/${post.id}/likes`, 'GET', undefined, false, headers)).status, 400)
    assert.equal((await call(`/posts/${post.id}/likes`, 'PUT', { liked: true }, false, headers)).status, 400)
  }
  for (const data of [{}, { liked: 'true' }, { liked: 1 }, { liked: null }]) {
    assert.equal((await call(`/posts/${post.id}/likes`, 'PUT', data, false, visitor)).status, 400)
  }
  assert.deepEqual((await call(`/posts/${post.id}/likes`, 'GET', undefined, false, visitor)).data, { likeCount: 0, liked: false })
  assert.equal((await call(`/posts/${post.id}/likes`, 'PUT', { liked: true }, false, { ...visitor, Origin: 'https://evil.example' })).status, 403)
  const draft = await createPost('DRAFT')
  for (const id of [draft.id, 999999]) {
    assert.equal((await call(`/posts/${id}/likes`, 'GET', undefined, false, visitor)).status, 404)
    assert.equal((await call(`/posts/${id}/likes`, 'PUT', { liked: true }, false, visitor)).status, 404)
  }
  await call(`/posts/${post.id}/likes`, 'PUT', { liked: true }, false, visitor)
  const unpublished = await call(`/admin/posts/${post.id}/unpublish`, 'PATCH', undefined, true)
  assert.equal(unpublished.data.likeCount, 1)
  assert.equal((await call(`/posts/${post.id}/likes`, 'GET', undefined, false, visitor)).status, 404)
  assert.equal((await call(`/posts/${post.id}/likes`, 'PUT', { liked: false }, false, visitor)).status, 404)
  const published = await call(`/admin/posts/${post.id}/publish`, 'PATCH', undefined, true)
  assert.equal(published.data.likeCount, 1)
})

test('admin can reset likes and deleting a post removes its likes', async t => {
  const { DB, call, createPost } = await fixture(t)
  const post = await createPost()
  await call(`/posts/${post.id}/likes`, 'PUT', { liked: true }, false, visitor)
  assert.equal((await call(`/admin/posts/${post.id}/likes`, 'DELETE')).status, 401)
  const reset = await call(`/admin/posts/${post.id}/likes`, 'DELETE', undefined, true)
  assert.equal(reset.status, 200)
  assert.equal(reset.data, null)
  assert.deepEqual((await call(`/posts/${post.id}/likes`, 'GET', undefined, false, visitor)).data, { likeCount: 0, liked: false })
  assert.equal((await call('/admin/posts/999999/likes', 'DELETE', undefined, true)).status, 404)
  await call(`/posts/${post.id}/likes`, 'PUT', { liked: true }, false, visitor)
  assert.equal((await call(`/admin/posts/${post.id}`, 'DELETE', undefined, true)).status, 200)
  assert.equal((await DB.prepare('SELECT count(*) AS n FROM post_likes').first()).n, 0)
})

test('new comments wait for moderation and only approved comments appear in public counts', async t => {
  const { call, createPost } = await fixture(t)
  const post = await createPost()
  const submitted = await call(`/posts/${post.id}/comments`, 'POST', { author: ' Reader ', content: ' Hello ' })
  assert.equal(submitted.status, 201)
  assert.equal(submitted.data.status, 'PENDING')
  assert.equal(submitted.data.author, 'Reader')
  assert.equal(submitted.data.content, 'Hello')
  assert.equal(submitted.data.reply, null)
  assert.equal(submitted.data.repliedAt, null)
  assert.deepEqual((await call(`/posts/${post.id}/comments`)).data, [])
  assert.equal((await call(`/posts/${post.id}`)).data.commentCount, 0)
  assert.equal((await call('/admin/comments', 'GET', undefined, true)).data[0].status, 'PENDING')
  const id = submitted.data.id
  assert.equal((await call(`/admin/comments/${id}/status`, 'PATCH', { status: 'APPROVED' })).status, 401)
  const approved = await call(`/admin/comments/${id}/status`, 'PATCH', { status: 'APPROVED' }, true)
  assert.equal(approved.status, 200)
  assert.equal(approved.data.status, 'APPROVED')
  assert.equal((await call(`/posts/${post.id}/comments`)).data[0].id, id)
  assert.equal((await call(`/posts/${post.id}`)).data.commentCount, 1)
  assert.equal((await call('/posts')).data.items[0].commentCount, 1)
  assert.equal((await call(`/admin/posts/${post.id}`, 'GET', undefined, true)).data.commentCount, 1)
  const hidden = await call(`/admin/comments/${id}/status`, 'PATCH', { status: 'HIDDEN' }, true)
  assert.equal(hidden.data.status, 'HIDDEN')
  assert.deepEqual((await call(`/posts/${post.id}/comments`)).data, [])
  assert.equal((await call('/admin/posts', 'GET', undefined, true)).data.items[0].commentCount, 0)
  const pending = await call(`/admin/comments/${id}/status`, 'PATCH', { status: 'PENDING' }, true)
  assert.equal(pending.data.status, 'PENDING')
  assert.equal((await call('/admin/comments', 'GET', undefined, true)).data.length, 1)
  await call(`/admin/comments/${id}/status`, 'PATCH', { status: 'APPROVED' }, true)
  await call(`/admin/posts/${post.id}/unpublish`, 'PATCH', undefined, true)
  assert.equal((await call(`/posts/${post.id}/comments`)).status, 404)
  assert.equal((await call(`/posts/${post.id}/comments`, 'POST', { author: 'Reader', content: 'Draft comment' })).status, 404)
  assert.equal((await call(`/admin/comments/${id}`, 'DELETE', undefined, true)).status, 200)
  assert.equal((await call(`/admin/posts/${post.id}`, 'GET', undefined, true)).data.commentCount, 0)
})

test('admin replies are trimmed, visible with approved comments, and blank content clears the reply', async t => {
  const { call, createPost } = await fixture(t)
  const post = await createPost()
  const submitted = await call(`/posts/${post.id}/comments`, 'POST', { author: 'Reader', content: 'Hello' })
  const id = submitted.data.id
  assert.equal((await call(`/admin/comments/${id}/reply`, 'PUT', { content: 'Thank you' })).status, 401)
  const replied = await call(`/admin/comments/${id}/reply`, 'PUT', { content: ' Thank you ' }, true)
  assert.equal(replied.status, 200)
  assert.equal(replied.data.reply, 'Thank you')
  assert.ok(Number.isFinite(Date.parse(replied.data.repliedAt)))
  assert.equal(replied.data.status, 'PENDING')
  assert.deepEqual((await call(`/posts/${post.id}/comments`)).data, [])
  await call(`/admin/comments/${id}/status`, 'PATCH', { status: 'APPROVED' }, true)
  assert.equal((await call(`/posts/${post.id}/comments`)).data[0].reply, 'Thank you')
  const edited = await call(`/admin/comments/${id}/reply`, 'PUT', { content: 'Updated reply' }, true)
  assert.equal(edited.data.reply, 'Updated reply')
  const cleared = await call(`/admin/comments/${id}/reply`, 'PUT', { content: ' \n\t ' }, true)
  assert.equal(cleared.data.reply, null)
  assert.equal(cleared.data.repliedAt, null)
  assert.equal((await call(`/posts/${post.id}/comments`)).data[0].reply, null)
})

test('comment management validates status and reply input and returns missing resources', async t => {
  const { call, createPost } = await fixture(t)
  const post = await createPost()
  const submitted = await call(`/posts/${post.id}/comments`, 'POST', { author: 'Reader', content: 'Hello' })
  const id = submitted.data.id
  for (const status of ['approved', 'DELETED', '', null, 1]) {
    assert.equal((await call(`/admin/comments/${id}/status`, 'PATCH', { status }, true)).status, 400)
  }
  for (const content of [null, 1, 'x'.repeat(1001)]) {
    assert.equal((await call(`/admin/comments/${id}/reply`, 'PUT', { content }, true)).status, 400)
  }
  assert.equal((await call(`/admin/comments/${id}/reply`, 'PUT', {}, true)).status, 400)
  assert.equal((await call(`/admin/comments/${id}/reply`, 'PUT', { content: 'x'.repeat(1000) }, true)).status, 200)
  assert.equal((await call('/admin/comments/999999/status', 'PATCH', { status: 'APPROVED' }, true)).status, 404)
  assert.equal((await call('/admin/comments/999999/reply', 'PUT', { content: 'Hello' }, true)).status, 404)
  assert.equal((await call(`/admin/comments/${id}/status`, 'PATCH', { status: 'APPROVED' }, true, { Origin: 'https://evil.example' })).status, 403)
})

test('migration preserves existing visible comments as approved', async t => {
  const { call } = await fixture(t, async DB => {
    await DB.prepare("INSERT INTO posts(title,slug,excerpt,content,category,status,createdAt,updatedAt) VALUES ('Legacy','legacy','','Content','Notes','PUBLISHED','2026-01-01','2026-01-01')").run()
    await DB.prepare("INSERT INTO comments(postId,author,content,createdAt) VALUES (1,'Legacy reader','Existing comment','2026-01-01')").run()
  })
  const comments = await call('/posts/1/comments')
  assert.equal(comments.status, 200)
  assert.equal(comments.data.length, 1)
  assert.equal(comments.data[0].status, 'APPROVED')
  assert.equal(comments.data[0].reply, null)
  assert.equal(comments.data[0].repliedAt, null)
  assert.equal((await call('/posts/1')).data.commentCount, 1)
})
