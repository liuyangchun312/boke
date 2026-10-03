import assert from 'node:assert/strict'
import { spawn } from 'node:child_process'
import { mkdtemp, readFile, rm, stat } from 'node:fs/promises'
import { createServer } from 'node:http'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import { fileURLToPath } from 'node:url'
import test from 'node:test'

const script = fileURLToPath(new URL('./import-related-articles.mjs', import.meta.url))
const articles = JSON.parse(await readFile(new URL('../src/main/resources/seed/related-articles.json', import.meta.url), 'utf8'))

async function fixture(t) {
  const directory = await mkdtemp(join(tmpdir(), 'blog-related-import-'))
  const receipt = join(directory, 'receipt.json')
  const posts = [{ id: 1, slug: articles[0].slug, content: 'Preserved owner edit' }]
  const writes = []
  let nextId = 2
  let failSlug
  const server = createServer(async (request, response) => {
    assert.equal(request.headers.authorization, 'Bearer test-admin-jwt')
    response.setHeader('Content-Type', 'application/json')
    if (request.method === 'GET') {
      response.end(JSON.stringify({ success: true, data: { items: posts, totalPages: 1 } }))
    } else if (request.method === 'POST') {
      let body = ''
      for await (const chunk of request) body += chunk
      const article = JSON.parse(body)
      if (article.slug === failSlug) {
        response.writeHead(503).end(JSON.stringify({ success: false }))
        return
      }
      assert.equal(article.createdAt, undefined)
      assert.equal(article.status, 'PUBLISHED')
      const created = { ...article, id: nextId++ }
      posts.push(created)
      writes.push(created)
      response.writeHead(201).end(JSON.stringify({ success: true, data: created }))
    } else {
      response.writeHead(405).end(JSON.stringify({ success: false }))
    }
  })
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve))
  t.after(async () => {
    await new Promise(resolve => server.close(resolve))
    await rm(directory, { recursive: true, force: true })
  })
  const run = apply => new Promise((resolve, reject) => {
    const args = [script, '--base-url', `http://127.0.0.1:${server.address().port}`, '--receipt', receipt]
    if (apply) args.push('--apply')
    const child = spawn(process.execPath, args, { env: { ...process.env, BLOG_IMPORT_TOKEN: 'test-admin-jwt' } })
    let output = ''
    child.stdout.on('data', chunk => { output += chunk })
    child.stderr.on('data', chunk => { output += chunk })
    child.on('error', reject)
    child.on('exit', code => resolve({ code, output }))
  })
  return { receipt, posts, writes, run, setFailure: slug => { failSlug = slug } }
}

test('preview is read-only; apply keeps edits and receipt preserves later deletions and renames', async t => {
  const api = await fixture(t)
  const preview = await api.run(false)
  assert.equal(preview.code, 0, preview.output)
  assert.equal(api.writes.length, 0)
  await assert.rejects(stat(api.receipt), { code: 'ENOENT' })

  const applied = await api.run(true)
  assert.equal(applied.code, 0, applied.output)
  assert.equal(api.writes.length, 9)
  assert.equal(api.posts[0].content, 'Preserved owner edit')
  assert.equal(JSON.parse(await readFile(api.receipt, 'utf8')).handledSlugs.length, 10)
  api.posts.splice(api.posts.findIndex(post => post.slug === articles[2].slug), 1)
  api.posts.find(post => post.slug === articles[1].slug).slug = 'renamed-by-owner'
  const rerun = await api.run(true)
  assert.equal(rerun.code, 0, rerun.output)
  assert.equal(api.writes.length, 9)
  assert.equal(api.posts.some(post => post.slug === articles[2].slug), false)
  assert.equal(api.posts.some(post => post.slug === articles[1].slug), false)
})

test('a partial API failure retains progress so retry only creates the remaining articles', async t => {
  const api = await fixture(t)
  api.setFailure(articles[3].slug)
  const partial = await api.run(true)
  assert.notEqual(partial.code, 0)
  assert.equal(api.writes.length, 2)
  assert.deepEqual(JSON.parse(await readFile(api.receipt, 'utf8')).handledSlugs, articles.slice(0, 3).map(article => article.slug))
  api.setFailure(undefined)
  const retry = await api.run(true)
  assert.equal(retry.code, 0, retry.output)
  assert.equal(api.writes.length, 9)
  assert.equal(new Set(api.posts.map(post => post.slug)).size, 10)
})
