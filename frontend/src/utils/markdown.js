import DOMPurify from 'dompurify'
import MarkdownIt from 'markdown-it'

const markdown = new MarkdownIt({
  html: false,
  linkify: true,
  typographer: false
})

const slugifyHeading = (value) => String(value || '')
  .trim()
  .toLocaleLowerCase()
  .replace(/[^\p{Letter}\p{Number}\u3400-\u9fff]+/gu, '-')
  .replace(/^-+|-+$/g, '') || 'section'

const headingText = (inlineToken) => {
  if (!inlineToken?.children) return inlineToken?.content || ''
  return inlineToken.children.map((token) => {
    if (token.type === 'text' || token.type === 'code_inline') return token.content
    if (token.type === 'image') return token.content
    if (token.type === 'softbreak' || token.type === 'hardbreak') return ' '
    return ''
  }).join('').replace(/\s+/g, ' ').trim()
}

const sanitize = (html) => DOMPurify.sanitize(html, {
  USE_PROFILES: { html: true },
  FORBID_TAGS: ['form', 'iframe', 'style'],
  FORBID_ATTR: ['style']
})

export const renderMarkdownDocument = (source = '', sanitizer = sanitize) => {
  const environment = {}
  const tokens = markdown.parse(String(source), environment)
  const headings = []
  const counts = new Map()

  for (let index = 0; index < tokens.length; index += 1) {
    const token = tokens[index]
    if (token.type !== 'heading_open') continue
    const text = headingText(tokens[index + 1])
    const base = slugifyHeading(text)
    const count = (counts.get(base) || 0) + 1
    const id = count === 1 ? base : base + '-' + count
    counts.set(base, count)
    token.attrSet('id', id)
    headings.push({ level: Number(token.tag.slice(1)), text, id })
  }

  const html = markdown.renderer.render(tokens, markdown.options, environment)
  return { html: sanitizer(html), headings }
}
