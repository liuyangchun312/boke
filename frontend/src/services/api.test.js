import test, { beforeEach } from 'node:test'
import assert from 'node:assert/strict'
import { createComment, deleteComment, fetchAdminComments, fetchComments, fetchPosts, fetchAllPosts, fetchPost, fetchAdminPosts, fetchCurrentUser, publishPost, unpublishPost, setToken, getToken } from './api.js'

const storage = new Map()
globalThis.window = { localStorage: {
  getItem: (key) => storage.get(key) ?? null,
  setItem: (key, value) => storage.set(key, value),
  removeItem: (key) => storage.delete(key)
} }
const ok = (data) => new Response(JSON.stringify({ success: true, data }), { status: 200 })

beforeEach(() => { storage.clear() })

test('public article requests never send an administrator token', async () => {
  setToken('private-admin-token')
  let sent
  globalThis.fetch = async (url, options) => { sent = { url, options }; return ok({ items: [] }) }
  await fetchPosts({ page: 2, size: 12, category: '地方 人文', keyword: '快阁' })
  assert.equal(sent.options.headers.has('Authorization'), false)
  const query = new URL(sent.url, 'http://localhost').searchParams
  assert.equal(query.get('page'), '2')
  assert.equal(query.get('size'), '12')
  assert.equal(query.get('category'), '地方 人文')
  assert.equal(query.get('keyword'), '快阁')
})

test('administrator requests authenticate and publish transitions use PATCH', async () => {
  setToken('private-admin-token')
  const calls = []
  globalThis.fetch = async (url, options) => { calls.push({ url, options }); return ok({}) }
  await fetchAdminPosts()
  await publishPost(17)
  await unpublishPost(17)
  assert.equal(calls[0].options.headers.get('Authorization'), 'Bearer private-admin-token')
  assert.equal(calls[1].url, '/api/admin/posts/17/publish')
  assert.equal(calls[1].options.method, 'PATCH')
  assert.equal(calls[2].url, '/api/admin/posts/17/unpublish')
  assert.equal(calls[2].options.method, 'PATCH')
})

test('comment moderation endpoints require the administrator token', async () => {
  setToken('private-admin-token')
  const calls = []
  globalThis.fetch = async (url, options) => { calls.push({ url, options }); return ok([]) }
  await fetchAdminComments()
  await deleteComment(9)
  assert.equal(calls[0].url, '/api/admin/comments')
  assert.equal(calls[0].options.headers.get('Authorization'), 'Bearer private-admin-token')
  assert.equal(calls[1].url, '/api/admin/comments/9')
  assert.equal(calls[1].options.method, 'DELETE')
})

test('archives include later API pages instead of silently truncating at 100 posts', async () => {
  globalThis.fetch = async (url) => {
    const page = Number(new URL(url, 'http://localhost').searchParams.get('page'))
    return ok({ items: [{ id: page }], page, totalPages: 3 })
  }
  assert.deepEqual((await fetchAllPosts()).map((post) => post.id), [1, 2, 3])
})

test('reader comments use the public article endpoint without leaking an admin token', async () => {
  setToken('private-admin-token')
  const calls = []
  globalThis.fetch = async (url, options) => { calls.push({ url, options }); return ok([]) }
  await fetchComments(7)
  await createComment(7, { author: '读者', content: '写得真好。' })
  assert.equal(calls[0].url, '/api/posts/7/comments')
  assert.equal(calls[0].options.headers.has('Authorization'), false)
  assert.equal(calls[1].options.method, 'POST')
  assert.equal(calls[1].options.headers.has('Authorization'), false)
  assert.deepEqual(JSON.parse(calls[1].options.body), { author: '读者', content: '写得真好。' })
})

test('missing articles retain HTTP status so the page can show a real 404', async () => {
  globalThis.fetch = async () => new Response(JSON.stringify({ success: false, message: 'Not found' }), { status: 404 })
  await assert.rejects(fetchPost('missing'), (error) => error.status === 404)
})

test('an unauthorized admin request clears the stale login token', async () => {
  setToken('expired')
  globalThis.fetch = async () => new Response('{}', { status: 401 })
  await assert.rejects(fetchAdminPosts(), (error) => error.status === 401)
  assert.equal(getToken(), null)
})

test('a successful HTTP response without an API envelope is not mistaken for empty content', async () => {
  globalThis.fetch = async () => new Response('<html>proxy error</html>', { status: 200 })
  await assert.rejects(fetchPosts(), /数据|响应/)
})

test('a late unauthorized response cannot erase a newer login session', async () => {
  setToken('old-session')
  let respond
  globalThis.fetch = () => new Promise((resolve) => { respond = resolve })
  const pending = fetchAdminPosts()
  setToken('new-session')
  respond(new Response('{}', { status: 401 }))
  await assert.rejects(pending, (error) => error.status === 401)
  assert.equal(getToken(), 'new-session')
})

test('a canceled session verification forwards cancellation to the network request', async () => {
  const controller = new AbortController()
  let requestSignal
  globalThis.fetch = async (url, options) => { requestSignal = options.signal; return ok({ username: 'owner' }) }
  const pending = fetchCurrentUser({ signal: controller.signal })
  controller.abort()
  assert.equal(requestSignal.aborted, true)
  await pending
})
