import assert from 'node:assert/strict'
import { readFileSync, writeFileSync } from 'node:fs'

const source = new URL('../../backend/src/main/resources/seed/related-articles.json', import.meta.url)
const target = new URL('../migrations/0003_related_articles.sql', import.meta.url)
const articles = JSON.parse(readFileSync(source, 'utf8'))
assert.equal(articles.length, 10, 'The related content batch must contain exactly ten articles')
assert.equal(new Set(articles.map(article => article.slug)).size, 10, 'Article slugs must be unique')

const columns = ['title', 'slug', 'excerpt', 'content', 'category', 'tags', 'coverImage',
  'status', 'createdAt', 'updatedAt', 'publishedAt']
const literal = value => value == null ? 'NULL' : `'${String(value).replaceAll("'", "''")}'`
const statements = articles.map(article => {
  assert.equal(article.status, 'PUBLISHED')
  const values = columns.map(column => literal(column === 'tags' ? JSON.stringify(article.tags) : article[column]))
  return `INSERT OR IGNORE INTO posts (${columns.join(', ')})\nVALUES (${values.join(', ')});`
})
const sql = '-- Generated from backend/src/main/resources/seed/related-articles.json.\n'
  + '-- Rebuild with node cloudflare/scripts/generate-related-articles.mjs.\n'
  + '-- Existing rows with matching slugs are retained. Apply once through the D1 migration runner.\n\n'
  + statements.join('\n\n') + '\n'

if (process.argv.includes('--check')) {
  assert.equal(readFileSync(target, 'utf8'), sql, 'The D1 migration differs from the canonical content')
  console.log('Related article migration matches all ten canonical articles.')
} else {
  writeFileSync(target, sql, 'utf8')
  console.log('Generated cloudflare/migrations/0003_related_articles.sql with ten articles.')
}
