<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { ArrowLeft, ArrowRight, Check, Copy, Eye, Share2 } from 'lucide-vue-next'
import { RouterLink, useRoute } from 'vue-router'
import CommentSection from '../components/CommentSection.vue'
import RequestState from '../components/RequestState.vue'
import { usePublicPosts } from '../composables/usePublicPosts'
import { fetchPost } from '../services/api'
import { formatDate, normalizePost } from '../utils/blog'
import { renderMarkdownDocument } from '../utils/markdown'
import { applyArticleMeta, applySiteMeta } from '../utils/seo'

const route = useRoute()
const post = ref(null)
const loading = ref(true)
const error = ref('')
const missing = ref(false)
const shareMessage = ref('')
const readingProgress = ref(0)
const { posts } = usePublicPosts()
let requestId = 0
let controller
let shareTimer

const markdownDocument = computed(() => renderMarkdownDocument(post.value?.content || ''))
const currentIndex = computed(() => posts.value.findIndex((item) => item.slug === post.value?.slug))
const newerPost = computed(() => currentIndex.value > 0 ? posts.value[currentIndex.value - 1] : null)
const olderPost = computed(() => currentIndex.value >= 0 ? posts.value[currentIndex.value + 1] : null)

const load = async () => {
  const current = ++requestId
  controller?.abort()
  controller = new AbortController()
  loading.value = true
  error.value = ''
  missing.value = false
  post.value = null
  applySiteMeta({ title: '正在读取文章 · 刘杨春', path: route.fullPath, noindex: true })
  try {
    const result = await fetchPost(String(route.params.slug), { signal: controller.signal })
    if (current !== requestId) return
    post.value = normalizePost(result)
    applyArticleMeta(post.value)
  } catch (cause) {
    if (cause?.name === 'AbortError' || current !== requestId) return
    if (cause?.status === 404) {
      missing.value = true
      applySiteMeta({ title: '文章未找到 · 刘杨春', path: route.fullPath, noindex: true })
    } else {
      error.value = cause?.message || '暂时无法读取这篇文章。'
      applySiteMeta({ title: '文章暂时无法读取 · 刘杨春', path: route.fullPath, noindex: true })
    }
  } finally {
    if (current === requestId) loading.value = false
  }
}

const updateReadingProgress = () => {
  const article = document.querySelector('.article-page')
  if (!article) return
  const start = article.offsetTop
  const distance = article.scrollHeight - window.innerHeight
  readingProgress.value = distance <= 0 ? 100 : Math.min(100, Math.max(0, ((window.scrollY - start) / distance) * 100))
}

const announceShare = (message) => {
  shareMessage.value = message
  window.clearTimeout(shareTimer)
  shareTimer = window.setTimeout(() => { shareMessage.value = '' }, 2800)
}

const share = async () => {
  const data = { title: post.value.title, text: post.value.excerpt, url: window.location.href }
  try {
    if (navigator.share) {
      await navigator.share(data)
      announceShare('分享面板已打开')
    } else {
      await navigator.clipboard.writeText(data.url)
      announceShare('文章链接已复制')
    }
  } catch (cause) {
    if (cause?.name === 'AbortError') announceShare('已取消分享')
    else announceShare('分享失败，请稍后重试')
  }
}

watch(() => route.params.slug, load, { immediate: true })
onMounted(() => {
  updateReadingProgress()
  window.addEventListener('scroll', updateReadingProgress, { passive: true })
  window.addEventListener('resize', updateReadingProgress)
})
onBeforeUnmount(() => {
  requestId += 1
  controller?.abort()
  window.clearTimeout(shareTimer)
  window.removeEventListener('scroll', updateReadingProgress)
  window.removeEventListener('resize', updateReadingProgress)
})
</script>

<template>
  <main id="main-content" class="article-page">
    <div class="reading-progress" aria-hidden="true"><span :style="{ width: readingProgress + '%' }"></span></div>
    <section v-if="loading" class="article-state container-narrow"><RequestState state="loading" light /></section>
    <section v-else-if="error" class="article-state container-narrow"><RequestState state="error" :message="error" light @retry="load" /></section>
    <section v-else-if="missing" class="article-state article-missing container-narrow">
      <p class="eyebrow">404 / ARTICLE NOT FOUND</p>
      <h1>这篇手记没有找到。</h1>
      <p>文章可能尚未发布，或者地址已经改变。</p>
      <RouterLink class="text-link" :to="{ name: 'home' }"><ArrowLeft :size="17" />回到文章索引</RouterLink>
    </section>
    <template v-else-if="post">
      <section class="article-heading container-narrow">
        <RouterLink class="back-link" :to="{ name: 'home' }"><ArrowLeft :size="16" />返回文章索引</RouterLink>
        <div class="article-heading-meta">
          <span>{{ post.category }}</span><span>{{ formatDate(post.date) }}</span><span>{{ post.readTime }}阅读</span>
          <span class="article-views"><Eye :size="12" />{{ post.viewCount }} 次浏览</span>
        </div>
        <h1>{{ post.title }}</h1>
        <p v-if="post.excerpt" class="article-deck">{{ post.excerpt }}</p>
        <div class="article-byline"><span class="avatar">刘</span><span>刘杨春 · 泰和手记</span><span class="byline-dot"></span><span>更新于 {{ formatDate(post.updatedAt || post.date) }}</span></div>
      </section>
      <figure v-if="post.image" class="article-hero container-wide"><img :src="post.image" :alt="post.title" /><figcaption><span>刘杨春 / 泰和乡土手记</span><span>{{ post.category }}</span></figcaption></figure>
      <section class="article-body container-narrow">
        <article class="article-copy markdown-body" v-html="markdownDocument.html"></article>
        <aside class="article-aside">
          <div v-if="markdownDocument.headings.length" class="aside-block">
            <span class="aside-label">本篇索引</span>
            <ol><li v-for="(heading, index) in markdownDocument.headings" :key="heading.id" :class="'toc-level-' + heading.level"><a :href="'#' + heading.id"><b>{{ String(index + 1).padStart(2, '0') }}</b><span>{{ heading.text }}</span></a></li></ol>
          </div>
          <div v-if="post.tags.length" class="aside-block">
            <span class="aside-label">相关主题</span>
            <div class="aside-tags"><RouterLink v-for="tag in post.tags" :key="tag" :to="{ name: 'home', query: { tag } }">#{{ tag }}</RouterLink></div>
          </div>
          <button class="share-button" type="button" @click="share"><Share2 :size="16" />分享这篇文章</button>
          <p v-if="shareMessage" class="share-feedback" role="status"><Check v-if="shareMessage.includes('复制')" :size="14" /><Copy v-else :size="14" />{{ shareMessage }}</p>
        </aside>
      </section>

      <section class="article-signoff container-narrow">
        <div class="author-note">
          <span class="author-note-mark">刘</span>
          <div><p class="eyebrow">ABOUT THE AUTHOR / 作者</p><h2>刘杨春</h2><p>在泰和生活与行走，记录县城的风物、人文和那些值得慢慢讲述的日常。</p><RouterLink :to="{ name: 'about' }">认识作者 <ArrowRight :size="14" /></RouterLink></div>
        </div>
        <nav v-if="newerPost || olderPost" class="article-pagination" aria-label="相邻文章">
          <RouterLink v-if="newerPost" :to="{ name: 'article', params: { slug: newerPost.slug } }"><span><ArrowLeft :size="14" />新一篇</span><strong>{{ newerPost.title }}</strong></RouterLink>
          <span v-else></span>
          <RouterLink v-if="olderPost" class="next" :to="{ name: 'article', params: { slug: olderPost.slug } }"><span>继续读<ArrowRight :size="14" /></span><strong>{{ olderPost.title }}</strong></RouterLink>
        </nav>
      </section>

      <CommentSection :post-id="post.id" />
      <section class="article-end container-narrow"><RouterLink class="text-link" :to="{ name: 'home' }"><ArrowLeft :size="17" />回到全部文章</RouterLink></section>
    </template>
  </main>
</template>
