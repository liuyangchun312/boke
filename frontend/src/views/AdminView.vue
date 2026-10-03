<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { onBeforeRouteLeave } from 'vue-router'
import { ArrowUpRight, Edit3, Eye, FileText, Heart, LayoutDashboard, LogOut, MessageSquare, PenLine, Plus, RefreshCw, Search, Send, Trash2, Undo2 } from 'lucide-vue-next'
import AdminComments from '../components/AdminComments.vue'
import EditorModal from '../components/EditorModal.vue'
import RequestState from '../components/RequestState.vue'
import {
  createPost,
  deleteComment,
  deletePost,
  fetchAdminComments,
  fetchAdminPost,
  fetchAllAdminPosts,
  fetchCurrentUser,
  getToken,
  loginAdmin,
  moderateComment,
  publishPost,
  replyToComment,
  resetPostLikes,
  setToken,
  unpublishPost,
  updatePost
} from '../services/api'
import { formatDate, sortPosts } from '../utils/blog'

const authState = ref(getToken() ? 'checking' : 'login')
const authError = ref('')
const authPending = ref(false)
const listLoading = ref(false)
const listError = ref('')
const posts = ref([])
const comments = ref([])
const activeSection = ref('posts')
const currentUser = ref(null)
const loginForm = reactive({ username: '', password: '' })
const statusFilter = ref('ALL')
const categoryFilter = ref('')
const postSearch = ref('')
const replyDirty = ref(false)
const editorPost = ref(undefined)
const editorPending = ref(false)
const editorError = ref('')
const editorDirty = ref(false)
const rowPending = ref(null)
const toast = ref('')
let toastTimer
let mounted = false
let authEpoch = 0
let listEpoch = 0
let mutationEpoch = 0
let authController = new AbortController()
let listController = new AbortController()
let mutationController = new AbortController()

const resetRequests = () => {
  authEpoch += 1
  listEpoch += 1
  mutationEpoch += 1
  authController.abort()
  listController.abort()
  mutationController.abort()
  authController = new AbortController()
  listController = new AbortController()
  mutationController = new AbortController()
}

const categories = computed(() => [...new Set(posts.value.map((post) => post.category).filter(Boolean))])
const filteredPosts = computed(() => posts.value.filter((post) => {
  if (statusFilter.value !== 'ALL' && post.status !== statusFilter.value) return false
  if (categoryFilter.value && post.category !== categoryFilter.value) return false
  const query = postSearch.value.trim().toLocaleLowerCase()
  return !query || [post.title, post.category, ...post.tags].join(' ').toLocaleLowerCase().includes(query)
}))
const stats = computed(() => [
  { label: '全部文章', value: posts.value.length, suffix: '篇' },
  { label: '已发布', value: posts.value.filter((post) => post.status === 'PUBLISHED').length, suffix: '篇' },
  { label: '累计点赞', value: posts.value.reduce((total, post) => total + post.likeCount, 0), suffix: '次' },
  { label: '待审核留言', value: comments.value.filter((comment) => comment.status === 'PENDING').length, suffix: '则' }
])
const approvedCounts = computed(() => {
  const counts = new Map()
  for (const comment of comments.value) if (comment.status === 'APPROVED') counts.set(Number(comment.postId), (counts.get(Number(comment.postId)) || 0) + 1)
  return counts
})
const editorOpen = computed(() => editorPost.value !== undefined)

const announce = (message) => {
  toast.value = message
  window.clearTimeout(toastTimer)
  toastTimer = window.setTimeout(() => { toast.value = '' }, 2800)
}

const isAuthFailure = (error) => error?.status === 401 || error?.status === 403
const handleInteractionAuthFailure = () => {
  if (replyDirty.value) {
    announce('登录已过期。当前回复仍保留，请复制后重新登录。')
  } else {
    logout(false)
    authError.value = '登录已过期，请重新登录。'
  }
}

const loadPosts = async () => {
  const epoch = ++listEpoch
  listController.abort()
  listController = new AbortController()
  listLoading.value = true
  listError.value = ''
  try {
    const [postResult, commentResult] = await Promise.all([
      fetchAllAdminPosts({}, { signal: listController.signal }),
      fetchAdminComments({ signal: listController.signal })
    ])
    if (!mounted || epoch !== listEpoch) return
    posts.value = sortPosts(postResult || [])
    comments.value = Array.isArray(commentResult) ? commentResult : []
  } catch (error) {
    if (!mounted || epoch !== listEpoch || error?.name === 'AbortError') return
    if (isAuthFailure(error)) {
      logout(false)
      authError.value = '登录已过期，请重新登录。'
    } else {
      listError.value = error?.message || '暂时无法读取文章列表。'
    }
  } finally {
    if (mounted && epoch === listEpoch) listLoading.value = false
  }
}

const verifySession = async () => {
  if (!getToken()) {
    authState.value = 'login'
    return
  }
  const epoch = ++authEpoch
  authController.abort()
  authController = new AbortController()
  authState.value = 'checking'
  authError.value = ''
  try {
    const user = await fetchCurrentUser({ signal: authController.signal })
    if (!mounted || epoch !== authEpoch) return
    currentUser.value = user
    authState.value = 'ready'
    await loadPosts()
  } catch (error) {
    if (!mounted || epoch !== authEpoch || error?.name === 'AbortError') return
    if (isAuthFailure(error)) {
      setToken(null)
      authState.value = 'login'
      authError.value = '登录已过期，请重新登录。'
    } else {
      authState.value = 'session-error'
      authError.value = error?.message || '暂时无法验证登录状态。'
    }
  }
}

const login = async () => {
  const epoch = ++authEpoch
  authController.abort()
  authController = new AbortController()
  authPending.value = true
  authError.value = ''
  try {
    const result = await loginAdmin(loginForm, { signal: authController.signal })
    if (!mounted || epoch !== authEpoch) return
    setToken(result.token)
    const user = result.user || await fetchCurrentUser({ signal: authController.signal })
    if (!mounted || epoch !== authEpoch) return
    currentUser.value = user
    loginForm.password = ''
    authState.value = 'ready'
    await loadPosts()
  } catch (error) {
    if (!mounted || epoch !== authEpoch || error?.name === 'AbortError') return
    setToken(null)
    authError.value = error?.message || '登录失败，请检查账号和密码。'
  } finally {
    if (mounted && epoch === authEpoch) authPending.value = false
  }
}

function logout(showMessage = true) {
  if (showMessage && replyDirty.value && !window.confirm('回复尚未保存，确定退出吗？')) return
  resetRequests()
  setToken(null)
  currentUser.value = null
  posts.value = []
  comments.value = []
  activeSection.value = 'posts'
  editorPost.value = undefined
  editorDirty.value = false
  replyDirty.value = false
  authPending.value = false
  listLoading.value = false
  editorPending.value = false
  rowPending.value = null
  authState.value = 'login'
  loginForm.password = ''
  if (showMessage) announce('已退出编辑工作台')
}

const startNew = () => {
  editorError.value = ''
  editorPost.value = null
}

const edit = async (post) => {
  const epoch = ++mutationEpoch
  mutationController.abort()
  mutationController = new AbortController()
  rowPending.value = 'edit-' + post.id
  listError.value = ''
  try {
    const result = await fetchAdminPost(post.id, { signal: mutationController.signal })
    if (!mounted || epoch !== mutationEpoch) return
    editorPost.value = result
    editorError.value = ''
  } catch (error) {
    if (!mounted || epoch !== mutationEpoch || error?.name === 'AbortError') return
    if (isAuthFailure(error)) logout(false)
    else listError.value = error?.message || '无法打开这篇文章。'
  } finally {
    if (mounted && epoch === mutationEpoch) rowPending.value = null
  }
}

const closeEditor = () => {
  editorPost.value = undefined
  editorError.value = ''
  editorDirty.value = false
}

const saveEditor = async (payload, publishAfter = false) => {
  const epoch = ++mutationEpoch
  mutationController.abort()
  mutationController = new AbortController()
  editorPending.value = true
  editorError.value = ''
  try {
    const finalPayload = { ...payload, status: publishAfter ? 'PUBLISHED' : payload.status }
    const saved = editorPost.value?.id
      ? await updatePost(editorPost.value.id, finalPayload, { signal: mutationController.signal })
      : await createPost(finalPayload, { signal: mutationController.signal })
    if (!mounted || epoch !== mutationEpoch) return
    closeEditor()
    await loadPosts()
    if (!mounted || epoch !== mutationEpoch) return
    announce(publishAfter ? '文章已发布' : finalPayload.status === 'PUBLISHED' ? '已保存发布中的文章' : '草稿已保存')
  } catch (error) {
    if (!mounted || epoch !== mutationEpoch || error?.name === 'AbortError') return
    if (isAuthFailure(error)) editorError.value = '登录已过期。当前未保存内容仍保留，请复制后重新登录。'
    else editorError.value = error?.message || '保存失败，请稍后重试。'
  } finally {
    if (mounted && epoch === mutationEpoch) editorPending.value = false
  }
}

const unpublishEditor = async () => {
  if (!editorPost.value?.id) return
  const epoch = ++mutationEpoch
  mutationController.abort()
  mutationController = new AbortController()
  editorPending.value = true
  editorError.value = ''
  try {
    await unpublishPost(editorPost.value.id, { signal: mutationController.signal })
    if (!mounted || epoch !== mutationEpoch) return
    closeEditor()
    await loadPosts()
    if (!mounted || epoch !== mutationEpoch) return
    announce('文章已转为草稿')
  } catch (error) {
    if (!mounted || epoch !== mutationEpoch || error?.name === 'AbortError') return
    if (isAuthFailure(error)) editorError.value = '登录已过期。当前未保存内容仍保留，请复制后重新登录。'
    else editorError.value = error?.message || '取消发布失败。'
  } finally {
    if (mounted && epoch === mutationEpoch) editorPending.value = false
  }
}

const togglePublication = async (post) => {
  const epoch = ++mutationEpoch
  mutationController.abort()
  mutationController = new AbortController()
  rowPending.value = 'status-' + post.id
  listError.value = ''
  try {
    if (post.status === 'PUBLISHED') await unpublishPost(post.id, { signal: mutationController.signal })
    else await publishPost(post.id, { signal: mutationController.signal })
    if (!mounted || epoch !== mutationEpoch) return
    await loadPosts()
    if (!mounted || epoch !== mutationEpoch) return
    announce(post.status === 'PUBLISHED' ? '文章已转为草稿' : '文章已发布')
  } catch (error) {
    if (!mounted || epoch !== mutationEpoch || error?.name === 'AbortError') return
    if (isAuthFailure(error)) logout(false)
    else listError.value = error?.message || '更新发布状态失败。'
  } finally {
    if (mounted && epoch === mutationEpoch) rowPending.value = null
  }
}

const remove = async (post) => {
  if (!window.confirm('确定删除《' + post.title + '》吗？此操作无法撤销。')) return
  const epoch = ++mutationEpoch
  mutationController.abort()
  mutationController = new AbortController()
  rowPending.value = 'delete-' + post.id
  listError.value = ''
  try {
    await deletePost(post.id, { signal: mutationController.signal })
    if (!mounted || epoch !== mutationEpoch) return
    await loadPosts()
    if (!mounted || epoch !== mutationEpoch) return
    announce('文章已删除')
  } catch (error) {
    if (!mounted || epoch !== mutationEpoch || error?.name === 'AbortError') return
    if (isAuthFailure(error)) logout(false)
    else listError.value = error?.message || '删除失败。'
  } finally {
    if (mounted && epoch === mutationEpoch) rowPending.value = null
  }
}

const removeReaderComment = async (comment) => {
  if (!window.confirm('确定删除“' + comment.author + '”的这则留言吗？')) return
  const epoch = ++mutationEpoch
  mutationController.abort()
  mutationController = new AbortController()
  rowPending.value = 'comment-' + comment.id
  listError.value = ''
  try {
    await deleteComment(comment.id, { signal: mutationController.signal })
    if (!mounted || epoch !== mutationEpoch) return
    comments.value = comments.value.filter((item) => item.id !== comment.id)
    announce('留言已删除')
  } catch (error) {
    if (!mounted || epoch !== mutationEpoch || error?.name === 'AbortError') return
    if (isAuthFailure(error)) handleInteractionAuthFailure()
    else announce(error?.message || '删除留言失败。')
  } finally {
    if (mounted && epoch === mutationEpoch) rowPending.value = null
  }
}

const mutateInteraction = async (key, action, onSuccess, message) => {
  if (rowPending.value) return
  const epoch = ++mutationEpoch
  mutationController.abort()
  mutationController = new AbortController()
  rowPending.value = key
  listError.value = ''
  try {
    const result = await action({ signal: mutationController.signal })
    if (!mounted || epoch !== mutationEpoch) return
    onSuccess(result)
    announce(message)
  } catch (error) {
    if (!mounted || epoch !== mutationEpoch || error?.name === 'AbortError') return
    if (isAuthFailure(error)) handleInteractionAuthFailure()
    else announce(error?.message || '操作失败，请稍后重试。')
  } finally {
    if (mounted && epoch === mutationEpoch) rowPending.value = null
  }
}

const replaceComment = (comment) => { comments.value = comments.value.map((item) => item.id === comment.id ? comment : item) }
const reviewComment = (comment, status) => mutateInteraction('review-' + comment.id,
  (options) => moderateComment(comment.id, status, options), replaceComment,
  status === 'APPROVED' ? '留言已审核通过' : '留言已隐藏')
const saveReply = (comment, content, done) => mutateInteraction('reply-' + comment.id,
  (options) => replyToComment(comment.id, content, options),
  (result) => { replaceComment(result); done(); replyDirty.value = false }, content ? '回复已保存' : '回复已移除')
const clearLikes = (post) => {
  if (!window.confirm('确定清空《' + post.title + '》的全部点赞吗？此操作无法撤销。')) return
  return mutateInteraction('likes-' + post.id, (options) => resetPostLikes(post.id, options),
    () => { post.likeCount = 0 }, '点赞已清空')
}
const switchSection = (section) => {
  if (section === activeSection.value) return
  if (replyDirty.value && !window.confirm('回复尚未保存，确定切换页面吗？')) return
  replyDirty.value = false
  activeSection.value = section
}

const onBeforeUnload = (event) => {
  if (!editorDirty.value && !editorPending.value && !replyDirty.value) return
  event.preventDefault()
  event.returnValue = ''
}

onBeforeRouteLeave(() => {
  if (editorPending.value) return window.confirm('保存请求仍在进行，确定离开编辑工作台吗？')
  if (replyDirty.value) return window.confirm('回复尚未保存，确定离开编辑工作台吗？')
  return !editorDirty.value || window.confirm('尚有未保存的修改，确定离开编辑工作台吗？')
})
onMounted(() => {
  mounted = true
  window.addEventListener('beforeunload', onBeforeUnload)
  verifySession()
})
onBeforeUnmount(() => {
  mounted = false
  resetRequests()
  window.removeEventListener('beforeunload', onBeforeUnload)
  window.clearTimeout(toastTimer)
})
</script>

<template>
  <main id="main-content" class="admin-page">
    <section v-if="authState === 'checking'" class="admin-auth-state container-wide"><RequestState state="loading" light message="正在验证登录状态…" /></section>
    <section v-else-if="authState === 'session-error'" class="admin-auth-state container-wide"><RequestState state="error" light :message="authError" @retry="verifySession" /><button class="secondary-button" type="button" @click="logout(false)">返回登录</button></section>

    <section v-else-if="authState === 'login'" class="admin-login container-wide">
      <div class="login-manifesto"><p class="eyebrow">LIU YANGCHUN / WRITING DESK</p><h1>把家乡<br /><em>认真写下来。</em></h1><p>这是刘杨春的写作后台。整理泰和故事、地方风物与一路所见。</p><span class="login-issue">TAIHE JOURNAL <i></i> PRIVATE AREA</span></div>
      <form class="login-card" @submit.prevent="login">
        <div class="login-card-head"><span class="login-icon"><PenLine :size="18" /></span><div><p class="eyebrow">EDITOR LOGIN</p><h2>进入编辑工作台</h2></div></div>
        <label for="username">账号</label><input id="username" v-model="loginForm.username" type="text" placeholder="输入账号" autocomplete="username" required />
        <label for="password">密码</label><input id="password" v-model="loginForm.password" type="password" placeholder="输入密码" autocomplete="current-password" required />
        <p v-if="authError" class="login-error" role="alert">{{ authError }}</p>
        <button class="primary-button" type="submit" :disabled="authPending">{{ authPending ? '正在登录…' : '登录工作台' }}<ArrowUpRight :size="17" /></button>
      </form>
    </section>

    <section v-else class="admin-workspace">
      <aside class="admin-sidebar">
        <div class="admin-logo"><span class="brand-mark">刘</span><span>写作台</span></div>
        <div class="admin-sidebar-label">WORKSPACE</div>
        <nav class="admin-nav" aria-label="编辑工作台导航">
          <button type="button" :class="{ active: activeSection === 'posts' }" @click="switchSection('posts')"><LayoutDashboard :size="17" />文章管理 <span>{{ posts.length }}</span></button>
          <button type="button" :class="{ active: activeSection === 'comments' }" @click="switchSection('comments')"><MessageSquare :size="17" />留言管理 <span>{{ comments.length }}</span></button>
        </nav>
        <div class="sidebar-bottom"><div class="mini-profile"><span class="avatar">刘</span><div><strong>{{ currentUser?.displayName || currentUser?.username }}</strong><small>作者 / Admin</small></div></div><button class="logout-button" type="button" title="退出登录" aria-label="退出登录" @click="logout()"><LogOut :size="17" /></button></div>
      </aside>
      <section class="admin-content">
        <header class="admin-topbar"><div><p class="eyebrow">LIU YANGCHUN · TAIHE JOURNAL</p><h1>{{ activeSection === 'posts' ? '文章管理' : '留言管理' }}</h1></div><div class="admin-top-actions"><button class="secondary-button mobile-logout" type="button" @click="logout()"><LogOut :size="16" />退出</button><button v-if="activeSection === 'posts'" class="primary-button compact" type="button" @click="startNew"><Plus :size="17" />新建文章</button></div></header>

        <div class="admin-stats"><div v-for="stat in stats" :key="stat.label" class="admin-stat"><span>{{ stat.label }}</span><strong>{{ String(stat.value).padStart(2, '0') }}<small>{{ stat.suffix }}</small></strong></div></div>

        <section v-if="activeSection === 'posts'" class="admin-list-panel">
          <div class="panel-heading">
            <div><p class="eyebrow">CONTENT / CONTENTS</p><h2>文章存档 <span>{{ filteredPosts.length }}</span></h2></div>
            <div class="panel-actions">
              <label class="admin-search"><Search :size="16" /><input v-model="postSearch" type="search" placeholder="搜索文章" aria-label="搜索文章" /></label>
              <select v-model="statusFilter" aria-label="按发布状态筛选"><option value="ALL">全部状态</option><option value="PUBLISHED">已发布</option><option value="DRAFT">草稿</option></select>
              <select v-model="categoryFilter" aria-label="按分类筛选"><option value="">全部分类</option><option v-for="category in categories" :key="category" :value="category">{{ category }}</option></select>
            </div>
          </div>
          <RequestState v-if="listLoading" state="loading" light />
          <RequestState v-else-if="listError" state="error" light :message="listError" @retry="loadPosts" />
          <template v-else>
            <div class="article-table-head"><span>文章</span><span>分类</span><span>状态</span><span>更新于</span><span></span></div>
            <div class="article-table">
              <div v-for="post in filteredPosts" :key="post.id" class="article-row">
                <div class="row-title"><div class="row-thumb"><img :src="post.image" :alt="post.title" loading="lazy" /></div><div><strong>{{ post.title }}</strong><small class="row-interactions"><span :title="post.viewCount + ' 次浏览'"><Eye :size="12" />{{ post.viewCount }}</span><span :title="post.likeCount + ' 次点赞'"><Heart :size="12" />{{ post.likeCount }}</span><span :title="(approvedCounts.get(Number(post.id)) || 0) + ' 则公开留言'"><MessageSquare :size="12" />{{ approvedCounts.get(Number(post.id)) || 0 }}</span></small></div></div>
                <span class="row-category">{{ post.category }}</span>
                <span class="status-dot" :class="{ draft: post.status === 'DRAFT' }"><i></i>{{ post.status === 'PUBLISHED' ? '已发布' : '草稿' }}</span>
                <span class="row-date">{{ formatDate(post.updatedAt || post.createdAt) }}</span>
                <div class="row-actions">
                  <button class="icon-button" type="button" title="编辑文章" :aria-label="'编辑《' + post.title + '》'" :disabled="Boolean(rowPending)" @click="edit(post)"><Edit3 :size="16" /></button>
                  <button class="icon-button" type="button" :title="post.status === 'PUBLISHED' ? '取消发布' : '发布文章'" :aria-label="(post.status === 'PUBLISHED' ? '取消发布《' : '发布《') + post.title + '》'" :disabled="Boolean(rowPending)" @click="togglePublication(post)"><Undo2 v-if="post.status === 'PUBLISHED'" :size="16" /><Send v-else :size="16" /></button>
                  <button class="icon-button" type="button" title="清空点赞" :aria-label="'清空《' + post.title + '》的点赞'" :disabled="Boolean(rowPending) || !post.likeCount" @click="clearLikes(post)"><RefreshCw :size="16" /></button>
                  <button class="icon-button danger" type="button" title="删除文章" :aria-label="'删除《' + post.title + '》'" :disabled="Boolean(rowPending)" @click="remove(post)"><Trash2 :size="16" /></button>
                </div>
              </div>
            </div>
            <div v-if="!filteredPosts.length" class="admin-empty"><FileText :size="23" /><p>{{ posts.length ? '没有符合筛选条件的文章' : '还没有文章' }}</p><button class="text-link" type="button" @click="startNew">新建第一篇<Plus :size="16" /></button></div>
          </template>
        </section>

        <section v-else class="admin-list-panel comment-admin-panel">
          <div class="panel-heading"><div><p class="eyebrow">READER NOTES / COMMENTS</p><h2>读者留言 <span>{{ comments.length }}</span></h2></div></div>
          <RequestState v-if="listLoading" state="loading" light />
          <RequestState v-else-if="listError" state="error" light :message="listError" @retry="loadPosts" />
          <AdminComments v-else :comments="comments" :posts="posts" :pending="Boolean(rowPending)" @moderate="reviewComment" @reply="saveReply" @delete="removeReaderComment" @dirty="replyDirty = $event" />
        </section>
      </section>
    </section>

    <EditorModal v-if="editorOpen" :post="editorPost" :pending="editorPending" :server-error="editorError" @dirty="editorDirty = $event" @close="closeEditor" @save="saveEditor($event, false)" @publish="saveEditor($event, true)" @unpublish="unpublishEditor">
      <template #category-options><datalist id="known-categories"><option v-for="category in categories" :key="category" :value="category"></option></datalist></template>
    </EditorModal>
    <transition name="toast"><div v-if="toast" class="toast" role="status">{{ toast }}</div></transition>
  </main>
</template>
