import { test } from 'node:test'
import assert from 'node:assert/strict'
import { readFile, readdir } from 'node:fs/promises'
import { Miniflare } from 'miniflare'
import { handleApi } from '../src/api.js'
import { ITERATIONS } from '../src/auth.js'

test('all D1 migrations publish ten canonical articles and retain edits and interactions on seed replay', async t => {
  const mf = new Miniflare({ modules: true, script: 'export default { fetch() { return new Response("OK") } }', d1Databases: ['DB'] })
  t.after(() => mf.dispose())
  const DB = await mf.getD1Database('DB')
  const directory = new URL('../migrations/', import.meta.url)
  const files = (await readdir(directory)).filter(file => file.endsWith('.sql')).sort()
  async function applyMigration(file) {
    const migration = await readFile(new URL(file, directory), 'utf8')
    await DB.batch(migration.split(';').filter(sql => sql.trim()).map(sql => DB.prepare(sql)))
  }
  for (const file of files) await applyMigration(file)
  assert.equal((await DB.prepare('SELECT count(*) AS n FROM posts').first()).n, 10)
  const env = { DB, BLOG_ADMIN_USERNAME: 'owner', BLOG_JWT_SECRET: 'x'.repeat(48),
    BLOG_ADMIN_PASSWORD_HASH: `${ITERATIONS}:${'a'.repeat(32)}:${'b'.repeat(64)}` }
  const response = await handleApi(new Request('https://blog.example/api/posts?size=100'), env)
  assert.equal(response.status, 200)
  const { data } = await response.json()
  assert.equal(data.total, 10)
  assert.equal(data.items.length, 10)
  assert.equal(new Set(data.items.map(post => post.slug)).size, 10)
  const canonical = JSON.parse(await readFile(new URL('../../backend/src/main/resources/seed/related-articles.json', import.meta.url), 'utf8'))
  assert.equal(canonical.length, 10)
  for (const article of canonical) {
    const migrated = data.items.find(post => post.slug === article.slug)
    assert.ok(migrated, `Missing article: ${article.slug}`)
    for (const [field, value] of Object.entries(article)) assert.deepEqual(migrated[field], value, `Mismatched ${article.slug}.${field}`)
  }
  for (const post of data.items) {
    assert.equal(post.status, 'PUBLISHED')
    assert.equal(post.likeCount, 0)
    assert.equal(post.commentCount, 0)
    assert.ok(post.content.trim())
    assert.ok(post.coverImage)
  }
  const edited = data.items.find(post => post.slug === 'taihe-farming-season-notes')
  await DB.prepare("UPDATE posts SET title='Owner edited title', content='Owner edited content', status='DRAFT', viewCount=7 WHERE id=?").bind(edited.id).run()
  await DB.prepare("INSERT INTO post_likes(postId,visitorId,createdAt) VALUES (?,'reader_0123456789','2026-10-03T00:00:00Z')").bind(edited.id).run()
  await DB.prepare("INSERT INTO comments(postId,author,content,status,reply,createdAt,repliedAt) VALUES (?,'Reader','Retained comment','APPROVED','Retained reply','2026-10-03T00:00:00Z','2026-10-03T00:00:00Z')").bind(edited.id).run()
  await applyMigration('0003_related_articles.sql')
  assert.equal((await DB.prepare('SELECT count(*) AS n FROM posts').first()).n, 10)
  const retained = await DB.prepare('SELECT id,title,content,status,viewCount FROM posts WHERE slug=?').bind(edited.slug).first()
  assert.deepEqual(retained, { id: edited.id, title: 'Owner edited title', content: 'Owner edited content', status: 'DRAFT', viewCount: 7 })
  assert.equal((await DB.prepare('SELECT count(*) AS n FROM post_likes WHERE postId=?').bind(edited.id).first()).n, 1)
  assert.deepEqual(await DB.prepare('SELECT content,status,reply FROM comments WHERE postId=?').bind(edited.id).first(), {
    content: 'Retained comment', status: 'APPROVED', reply: 'Retained reply'
  })
})
