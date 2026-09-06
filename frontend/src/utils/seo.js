const SITE_NAME = '刘杨春的泰和乡土手记'
const DEFAULT_DESCRIPTION = '刘杨春的个人博客，记录江西省吉安市泰和县的风物、人文与日常。'

const siteOrigin = () => {
  const configured = String(import.meta.env?.VITE_SITE_URL || '').trim()
  if (configured) return configured.replace(/\/$/, '')
  return typeof window === 'undefined' ? '' : window.location.origin
}

const absoluteUrl = (value = '/') => {
  if (!value) return siteOrigin()
  try { return new URL(value, `${siteOrigin() || 'http://localhost'}/`).href }
  catch { return value }
}

const upsertMeta = (attribute, key, content) => {
  let element = document.head.querySelector(`meta[${attribute}="${key}"]`)
  if (!content) {
    element?.remove()
    return
  }
  if (!element) {
    element = document.createElement('meta')
    element.setAttribute(attribute, key)
    document.head.appendChild(element)
  }
  element.setAttribute('content', content)
}

const upsertCanonical = (href) => {
  let element = document.head.querySelector('link[rel="canonical"]')
  if (!element) {
    element = document.createElement('link')
    element.rel = 'canonical'
    document.head.appendChild(element)
  }
  element.href = href
}

const clearStructuredData = () => document.getElementById('article-structured-data')?.remove()

export const applySiteMeta = ({
  title = SITE_NAME,
  description = DEFAULT_DESCRIPTION,
  path = '/',
  noindex = false,
  type = 'website',
  image = ''
} = {}) => {
  document.title = title
  const canonical = absoluteUrl(path)
  upsertMeta('name', 'description', description)
  upsertMeta('name', 'robots', noindex ? 'noindex, nofollow' : 'index, follow')
  upsertMeta('property', 'og:locale', 'zh_CN')
  upsertMeta('property', 'og:site_name', SITE_NAME)
  upsertMeta('property', 'og:type', type)
  upsertMeta('property', 'og:title', title)
  upsertMeta('property', 'og:description', description)
  upsertMeta('property', 'og:url', canonical)
  upsertMeta('property', 'og:image', image ? absoluteUrl(image) : '')
  upsertMeta('name', 'twitter:card', image ? 'summary_large_image' : 'summary')
  upsertMeta('name', 'twitter:title', title)
  upsertMeta('name', 'twitter:description', description)
  upsertMeta('name', 'twitter:image', image ? absoluteUrl(image) : '')
  upsertCanonical(canonical)
  if (type !== 'article') {
    upsertMeta('property', 'article:published_time', '')
    upsertMeta('property', 'article:modified_time', '')
    upsertMeta('property', 'article:section', '')
  }
  clearStructuredData()
}

export const applyArticleMeta = (article) => {
  if (!article) return
  const title = `${article.title} | ${SITE_NAME}`
  const path = `/posts/${encodeURIComponent(article.slug)}`
  applySiteMeta({ title, description: article.excerpt || DEFAULT_DESCRIPTION, path, type: 'article', image: article.image })
  upsertMeta('property', 'article:published_time', article.publishedAt || '')
  upsertMeta('property', 'article:modified_time', article.updatedAt || '')
  upsertMeta('property', 'article:section', article.category || '')

  const script = document.createElement('script')
  script.id = 'article-structured-data'
  script.type = 'application/ld+json'
  script.textContent = JSON.stringify({
    '@context': 'https://schema.org',
    '@type': 'BlogPosting',
    headline: article.title,
    description: article.excerpt,
    image: article.image ? [absoluteUrl(article.image)] : undefined,
    datePublished: article.publishedAt || undefined,
    dateModified: article.updatedAt || article.publishedAt || undefined,
    mainEntityOfPage: absoluteUrl(path),
    author: { '@type': 'Person', name: '刘杨春' },
    publisher: { '@type': 'Person', name: '刘杨春' },
    articleSection: article.category,
    keywords: article.tags?.join(',') || undefined,
    inLanguage: 'zh-CN'
  })
  document.head.appendChild(script)
}

export { DEFAULT_DESCRIPTION, SITE_NAME }
