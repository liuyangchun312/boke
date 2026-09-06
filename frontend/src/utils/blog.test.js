import test from 'node:test'
import assert from 'node:assert/strict'
import {
  buildArchiveGroups,
  editorPayload,
  filterPosts,
  normalizePost,
  validateDraft
} from './blog.js'
import { renderMarkdownDocument } from './markdown.js'

const posts = [
  {
    id: 1,
    title: '快阁晚晴',
    slug: 'kuaige',
    excerpt: '',
    content: '# 起笔\n\n赣江边的晚风。',
    category: '泰和风物',
    tags: ['快阁', '赣江'],
    status: 'PUBLISHED',
    publishedAt: '2026-09-02T10:00:00Z'
  },
  {
    id: 2,
    title: '白凤乌鸡',
    slug: 'wuji',
    excerpt: '一味乡土。',
    content: '正文',
    category: '地方物产',
    tags: ['饮食'],
    status: 'PUBLISHED',
    publishedAt: '2025-12-10T10:00:00Z'
  }
]

test('normalizePost keeps raw Markdown while deriving display fields', () => {
  const post = normalizePost(posts[0], 0)
  assert.equal(post.content, posts[0].content)
  assert.equal(post.excerpt, '起笔 赣江边的晚风。')
  assert.equal(post.date, '2026-09-02')
  assert.match(post.readTime, /^\d+ 分钟$/)
})

test('filterPosts combines category, tag and text query', () => {
  assert.deepEqual(filterPosts(posts, { category: '泰和风物', tag: '赣江', q: '晚风' }).map((post) => post.id), [1])
  assert.deepEqual(filterPosts(posts, { q: '不存在' }), [])
})

test('buildArchiveGroups groups descending posts by year and month', () => {
  const groups = buildArchiveGroups(posts)
  assert.deepEqual(groups.map((group) => group.year), ['2026', '2025'])
  assert.equal(groups[0].months[0].month, '09')
  assert.equal(groups[0].months[0].posts[0].slug, 'kuaige')
})

test('Markdown heading IDs and TOC share the parser token stream', () => {
  const markdown = '# 同名\n\n同名\n----\n\n~~~md\n# 不是标题\n~~~\n\n### Taihe Notes'
  const document = renderMarkdownDocument(markdown, (html) => html)
  assert.deepEqual(document.headings, [
    { level: 1, text: '同名', id: '同名' },
    { level: 2, text: '同名', id: '同名-2' },
    { level: 3, text: 'Taihe Notes', id: 'taihe-notes' }
  ])
  assert.match(document.html, /<h1 id="同名">同名<\/h1>/)
  assert.match(document.html, /<h2 id="同名-2">同名<\/h2>/)
  assert.doesNotMatch(document.html, /id="不是标题"/)
})

test('editorPayload preserves raw Markdown and normalizes editable fields', () => {
  const raw = '# 标题\n\n- 条目'
  assert.deepEqual(editorPayload({
    title: '  新文章  ',
    slug: 'new-post',
    excerpt: ' 摘要 ',
    category: ' 随笔 ',
    tagsText: '泰和， 赣江, ',
    coverImage: '',
    content: raw
  }, 'DRAFT'), {
    title: '新文章',
    slug: 'new-post',
    excerpt: '摘要',
    content: raw,
    category: '随笔',
    tags: ['泰和', '赣江'],
    coverImage: null,
    status: 'DRAFT'
  })
})

test('validateDraft reports every backend-required field', () => {
  assert.deepEqual(validateDraft({ title: ' ', category: '', content: '\n' }), {
    title: '请填写文章标题',
    category: '请填写文章分类',
    content: '请填写文章正文'
  })
  assert.deepEqual(validateDraft({ title: '题', category: '随笔', content: '正文' }), {})
})
