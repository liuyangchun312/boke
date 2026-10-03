const API_BASE_URL = (import.meta.env?.VITE_API_BASE_URL || '/api').replace(/\/$/, '')
const TOKEN_KEY = 'liuyangchun_admin_token'
const VISITOR_KEY = 'liuyangchun_visitor_id'
let memoryVisitorId
const createVisitorId = () => globalThis.crypto.randomUUID?.()
  || Array.from(globalThis.crypto.getRandomValues(new Uint8Array(16)), (byte) => byte.toString(16).padStart(2, '0')).join('')

const getVisitorId = () => {
  try {
    const saved = window.localStorage.getItem(VISITOR_KEY)
    if (/^[A-Za-z0-9_-]{16,128}$/.test(saved || '')) return saved
  } catch { /* Private browsing may disable storage. */ }
  memoryVisitorId ||= createVisitorId()
  try { window.localStorage.setItem(VISITOR_KEY, memoryVisitorId) } catch { /* Keep the identity for this session. */ }
  return memoryVisitorId
}

const visitorOptions = (options) => {
  const headers = new Headers(options.headers)
  headers.set('X-Visitor-Id', getVisitorId())
  return { ...options, headers, auth: false }
}

export class ApiError extends Error {
  constructor(message, status = 0) {
    super(message)
    this.name = 'ApiError'
    this.status = status
  }
}

export const getToken = () => {
  try { return window.localStorage.getItem(TOKEN_KEY) }
  catch { return null }
}

export const setToken = (token) => {
  if (token) window.localStorage.setItem(TOKEN_KEY, token)
  else window.localStorage.removeItem(TOKEN_KEY)
}

const request = async (path, options = {}) => {
  const { auth = false, signal, ...fetchOptions } = options
  const headers = new Headers(fetchOptions.headers)
  headers.set('Accept', 'application/json')
  if (fetchOptions.body && !headers.has('Content-Type')) headers.set('Content-Type', 'application/json')
  const token = auth ? getToken() : null
  if (token) headers.set('Authorization', `Bearer ${token}`)
  const controller = new AbortController()
  const cancel = () => controller.abort(signal.reason)
  if (signal?.aborted) cancel()
  else signal?.addEventListener('abort', cancel, { once: true })
  const timeout = setTimeout(() => controller.abort(), 15000)
  try {
    const response = await fetch(`${API_BASE_URL}${path}`, { ...fetchOptions, headers, signal: controller.signal })
    const payload = await response.json().catch(() => null)
    if (!response.ok || payload?.success === false) {
      if (auth && response.status === 401 && getToken() === token) setToken(null)
      const message = payload?.message === 'Invalid username or password'
        ? '用户名或密码不正确'
        : payload?.message || `请求失败 (${response.status})`
      throw new ApiError(message, response.status)
    }
    if (payload?.success !== true || !Object.hasOwn(payload, 'data')) {
      throw new ApiError('服务器响应格式异常，请稍后重试', response.status)
    }
    return payload.data
  } catch (error) {
    if (error instanceof ApiError || signal?.aborted) throw error
    throw new ApiError(controller.signal.aborted ? '请求超时，请重试' : '暂时无法连接网站，请稍后重试')
  } finally {
    clearTimeout(timeout)
    signal?.removeEventListener('abort', cancel)
  }
}

const listQuery = (params) => {
  const query = new URLSearchParams()
  for (const [key, value] of Object.entries({ page: 1, size: 10, ...params })) {
    if (value !== undefined && value !== null && value !== '') query.set(key, String(value))
  }
  return query.toString()
}

export const fetchPosts = (params = {}, options = {}) => request(`/posts?${listQuery(params)}`, options)
export const fetchPost = (slug, options = {}) => request(`/posts/slug/${encodeURIComponent(slug)}`, options)
export const fetchCategories = (options = {}) => request('/categories', options)
export const fetchTags = (options = {}) => request('/tags', options)
export const fetchComments = (postId, options = {}) => request(`/posts/${encodeURIComponent(postId)}/comments`, options)
export const createComment = (postId, comment, options = {}) => request(`/posts/${encodeURIComponent(postId)}/comments`, {
  ...options,
  method: 'POST',
  body: JSON.stringify(comment)
})
export const fetchAdminComments = (options = {}) => request('/admin/comments', { ...options, auth: true })
export const deleteComment = (id, options = {}) => request(`/admin/comments/${encodeURIComponent(id)}`, { ...options, method: 'DELETE', auth: true })
export const fetchLikes = (postId, options = {}) => request(`/posts/${encodeURIComponent(postId)}/likes`, visitorOptions(options))
export const setPostLike = (postId, liked, options = {}) => request(`/posts/${encodeURIComponent(postId)}/likes`, {
  ...visitorOptions(options), method: 'PUT', body: JSON.stringify({ liked })
})
export const moderateComment = (id, status, options = {}) => request(`/admin/comments/${encodeURIComponent(id)}/status`, {
  ...options, auth: true, method: 'PATCH', body: JSON.stringify({ status })
})
export const replyToComment = (id, content, options = {}) => request(`/admin/comments/${encodeURIComponent(id)}/reply`, {
  ...options, auth: true, method: 'PUT', body: JSON.stringify({ content })
})
export const resetPostLikes = (id, options = {}) => request(`/admin/posts/${encodeURIComponent(id)}/likes`, {
  ...options, auth: true, method: 'DELETE'
})
export const fetchAdminPosts = (params = {}, options = {}) => request(`/admin/posts?${listQuery(params)}`, { ...options, auth: true })
export const fetchAdminPost = (id, options = {}) => request(`/admin/posts/${encodeURIComponent(id)}`, { ...options, auth: true })
export const fetchCurrentUser = (options = {}) => request('/auth/me', { ...options, auth: true })

const fetchAll = async (fetchPage, params, options) => {
  const items = []
  let page = 1
  let totalPages = 1
  do {
    const result = await fetchPage({ ...params, page, size: 100 }, options)
    if (!Array.isArray(result?.items) || !Number.isInteger(result.totalPages) || result.totalPages < 0) {
      throw new ApiError('文章列表数据异常，请稍后重试')
    }
    items.push(...result.items)
    totalPages = result.totalPages
    page++
  } while (page <= totalPages)
  return items
}

export const fetchAllPosts = (params = {}, options = {}) => fetchAll(fetchPosts, params, options)
export const fetchAllAdminPosts = (params = {}, options = {}) => fetchAll(fetchAdminPosts, params, options)
export const loginAdmin = (credentials, options = {}) => request('/auth/login', {
  ...options,
  auth: false,
  method: 'POST',
  body: JSON.stringify(credentials)
})
export const createPost = (post, options = {}) => request('/admin/posts', {
  ...options,
  auth: true,
  method: 'POST',
  body: JSON.stringify(post)
})
export const updatePost = (id, post, options = {}) => request(`/admin/posts/${id}`, {
  ...options,
  auth: true,
  method: 'PUT',
  body: JSON.stringify(post)
})
export const deletePost = (id, options = {}) => request(`/admin/posts/${id}`, { ...options, method: 'DELETE', auth: true })
export const publishPost = (id, options = {}) => request(`/admin/posts/${id}/publish`, { ...options, method: 'PATCH', auth: true })
export const unpublishPost = (id, options = {}) => request(`/admin/posts/${id}/unpublish`, { ...options, method: 'PATCH', auth: true })
