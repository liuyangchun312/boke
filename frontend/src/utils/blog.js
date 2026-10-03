export const FALLBACK_COVER = '/covers/taihe-kuaige.svg'

const accents = ['coral', 'ink', 'sage', 'ochre']

export const stripMarkdown = (value = '') => String(value)
  .replace(/```[\s\S]*?```/g, ' ')
  .replace(/!\[[^\]]*\]\([^)]*\)/g, ' ')
  .replace(/\[([^\]]+)\]\([^)]*\)/g, '$1')
  .replace(/<[^>]+>/g, ' ')
  .replace(/[#>*_`~|\-]+/g, ' ')
  .replace(/\s+/g, ' ')
  .trim()

export const normalizePost = (post, index = 0) => {
  const content = String(post?.content || '')
  const rawDate = post?.publishedAt || post?.updatedAt || post?.createdAt || ''
  const date = rawDate ? String(rawDate).slice(0, 10) : ''

  return {
    ...post,
    title: String(post?.title || ''),
    slug: String(post?.slug || ''),
    excerpt: String(post?.excerpt || '').trim() || stripMarkdown(content).slice(0, 150),
    content,
    category: String(post?.category || ''),
    tags: Array.isArray(post?.tags) ? post.tags.filter(Boolean) : [],
    date,
    readTime: `${Math.max(1, Math.ceil(stripMarkdown(content).length / 500))} 分钟`,
    image: post?.coverImage || post?.image || FALLBACK_COVER,
    accent: accents[index % accents.length],
    viewCount: Number(post?.viewCount || 0),
    likeCount: Number(post?.likeCount || 0),
    commentCount: Number(post?.commentCount || 0)
  }
}

export const sortPosts = (posts = []) => [...posts]
  .map(normalizePost)
  .sort((a, b) => (b.publishedAt || b.updatedAt || b.createdAt || '').localeCompare(
    a.publishedAt || a.updatedAt || a.createdAt || ''
  ))
  .map((post, index) => ({ ...post, accent: accents[index % accents.length] }))

export const filterPosts = (posts = [], filters = {}) => {
  const category = String(filters.category || '').trim()
  const tag = String(filters.tag || '').trim()
  const query = String(filters.q || '').trim().toLocaleLowerCase()

  return sortPosts(posts).filter((post) => {
    if (category && post.category !== category) return false
    if (tag && !post.tags.includes(tag)) return false
    if (!query) return true
    return [post.title, post.excerpt, post.category, post.content, ...post.tags]
      .join(' ')
      .toLocaleLowerCase()
      .includes(query)
  })
}

export const buildArchiveGroups = (posts = []) => {
  const years = new Map()
  for (const post of sortPosts(posts)) {
    const [year = '未注明', month = ''] = post.date.split('-')
    if (!years.has(year)) years.set(year, new Map())
    const months = years.get(year)
    if (!months.has(month)) months.set(month, [])
    months.get(month).push(post)
  }

  return [...years].map(([year, months]) => ({
    year,
    months: [...months].map(([month, groupedPosts]) => ({ month, posts: groupedPosts }))
  }))
}

export const formatDate = (value) => {
  if (!value) return '日期未注明'
  const [year, month, day] = String(value).slice(0, 10).split('-')
  return [year, month, day].filter(Boolean).join('.')
}

export const editorPayload = (draft, status = draft.status || 'DRAFT') => ({
  title: String(draft.title || '').trim(),
  slug: String(draft.slug || '').trim(),
  excerpt: String(draft.excerpt || '').trim(),
  content: String(draft.content || ''),
  category: String(draft.category || '').trim(),
  tags: String(draft.tagsText || '').split(/[,，]/).map((tag) => tag.trim()).filter(Boolean),
  coverImage: String(draft.coverImage || '').trim() || null,
  status
})

export const validateDraft = (draft) => {
  const errors = {}
  if (!String(draft?.title || '').trim()) errors.title = '请填写文章标题'
  if (!String(draft?.category || '').trim()) errors.category = '请填写文章分类'
  if (!String(draft?.content || '').trim()) errors.content = '请填写文章正文'
  return errors
}
