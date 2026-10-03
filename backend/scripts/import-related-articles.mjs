import { mkdir, readFile, rename, writeFile } from 'node:fs/promises'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const args = process.argv.slice(2)
const knownOptions = new Set(['--base-url', '--receipt', '--apply'])
for (let index = 0; index < args.length; index++) {
  if (!knownOptions.has(args[index])) throw new Error(`Unknown option: ${args[index]}`)
  if (args[index] !== '--apply') {
    if (!args[index + 1] || args[index + 1].startsWith('--')) throw new Error(`Missing value for ${args[index]}`)
    index++
  }
}
const option = name => args.includes(name) ? args[args.indexOf(name) + 1] : undefined
const base = option('--base-url')
if (!base) throw new Error('Provide --base-url for the Java API. Preview is the default; --apply imports.')
const baseUrl = new URL(base)
if (!['http:', 'https:'].includes(baseUrl.protocol) || baseUrl.username || baseUrl.password
    || baseUrl.search || baseUrl.hash) throw new Error('Use an HTTP(S) API origin without credentials or query parameters.')
if (baseUrl.protocol !== 'https:' && !['localhost', '127.0.0.1', '[::1]'].includes(baseUrl.hostname)) {
  throw new Error('Use HTTPS for a remote API.')
}
const apiBase = baseUrl.href.replace(/\/$/, '')
const token = process.env.BLOG_IMPORT_TOKEN
if (!token) throw new Error('Set BLOG_IMPORT_TOKEN to an authenticated Java administrator JWT.')
const receiptPath = resolve(option('--receipt') || fileURLToPath(new URL('../data/related-articles-import.json', import.meta.url)))
const seed = 'related-articles-2026-10-v1'
const articles = JSON.parse(await readFile(new URL('../src/main/resources/seed/related-articles.json', import.meta.url), 'utf8'))
if (articles.length !== 10 || new Set(articles.map(article => article.slug)).size !== 10) {
  throw new Error('The canonical batch must contain exactly ten unique article slugs.')
}
let receipt = { seed, apiBase, handledSlugs: [] }
try {
  receipt = JSON.parse(await readFile(receiptPath, 'utf8'))
} catch (error) {
  if (error.code !== 'ENOENT') throw error
}
if (receipt.seed !== seed || receipt.apiBase !== apiBase || !Array.isArray(receipt.handledSlugs)) {
  throw new Error('The receipt belongs to another batch/API. Provide its original receipt or a distinct --receipt path.')
}
const handled = new Set(receipt.handledSlugs)
const request = async (path, options = {}) => {
  const response = await fetch(apiBase + path, { ...options, redirect: 'error',
    headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json', ...options.headers } })
  if (!response.ok) throw new Error(`Java API returned HTTP ${response.status} for ${path}`)
  const result = await response.json()
  if (!result.success) throw new Error(`Java API did not confirm success for ${path}`)
  return result.data
}
const existing = new Set()
for (let page = 1; ; page++) {
  const data = await request(`/api/admin/posts?page=${page}&size=100`)
  if (!Array.isArray(data.items) || !Number.isInteger(data.totalPages)) throw new Error('Unexpected Java post list response')
  data.items.forEach(post => existing.add(post.slug.toLowerCase()))
  if (page >= data.totalPages) break
}
const plan = articles.map(article => ({ title: article.title, slug: article.slug,
  action: handled.has(article.slug) ? 'retained-by-receipt'
    : existing.has(article.slug.toLowerCase()) ? 'retain-existing' : 'create' }))
console.table(plan)
if (!args.includes('--apply')) {
  console.log('Preview only. Add --apply to import this plan; keep the receipt for future runs.')
} else {
  const saveReceipt = async () => {
    receipt.handledSlugs = [...handled]
    await mkdir(dirname(receiptPath), { recursive: true })
    const temporary = receiptPath + '.tmp'
    await writeFile(temporary, JSON.stringify(receipt, null, 2) + '\n', 'utf8')
    await rename(temporary, receiptPath)
  }
  await saveReceipt()
  for (let index = 0; index < articles.length; index++) {
    const article = articles[index]
    if (plan[index].action === 'create') {
      const { createdAt, updatedAt, publishedAt, ...body } = article
      const created = await request('/api/admin/posts', { method: 'POST', body: JSON.stringify(body) })
      if (created.slug !== article.slug) {
        await request(`/api/admin/posts/${created.id}`, { method: 'DELETE' })
        throw new Error(`A concurrent editor occupied ${article.slug}; the import stopped without retaining a suffixed duplicate.`)
      }
    }
    handled.add(article.slug)
    await saveReceipt()
  }
  console.log(`Imported or retained ten articles. Receipt: ${receiptPath}`)
}
