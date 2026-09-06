<script setup>
import { computed } from 'vue'
import { ArrowUpRight, CalendarDays, Search } from 'lucide-vue-next'
import { RouterLink, useRoute } from 'vue-router'
import RequestState from '../components/RequestState.vue'
import { usePublicPosts } from '../composables/usePublicPosts'
import { buildArchiveGroups, filterPosts, formatDate } from '../utils/blog'

const route = useRoute()
const { posts, categories, tags, loading, error, load } = usePublicPosts()
const filters = computed(() => ({
  category: String(route.query.category || ''),
  tag: String(route.query.tag || ''),
  q: String(route.query.q || '')
}))
const filteredPosts = computed(() => filterPosts(posts.value, filters.value))
const groups = computed(() => buildArchiveGroups(filteredPosts.value))
const hasFilters = computed(() => Boolean(filters.value.category || filters.value.tag || filters.value.q))

const withFilter = (key, value) => ({
  ...(filters.value.category ? { category: filters.value.category } : {}),
  ...(filters.value.tag ? { tag: filters.value.tag } : {}),
  ...(filters.value.q ? { q: filters.value.q } : {}),
  [key]: value || undefined
})
</script>

<template>
  <main id="main-content" class="archive-page">
    <header class="page-intro container-wide">
      <p class="eyebrow">ARCHIVES / 泰和档案</p>
      <h1>循着年月，<br /><em>翻阅泰和。</em></h1>
      <p>按写下的时间整理每一篇乡土手记。</p>
    </header>

    <section class="archive-filters container-wide" aria-label="筛选文章档案">
      <div class="filter-line">
        <span>分类</span>
        <RouterLink :class="{ active: !filters.category }" :to="{ name: 'archives', query: withFilter('category', '') }">全部</RouterLink>
        <RouterLink v-for="category in categories" :key="category" :class="{ active: filters.category === category }" :to="{ name: 'archives', query: withFilter('category', category) }">{{ category }}</RouterLink>
      </div>
      <div v-if="tags.length" class="filter-line">
        <span>主题</span>
        <RouterLink :class="{ active: !filters.tag }" :to="{ name: 'archives', query: withFilter('tag', '') }">全部</RouterLink>
        <RouterLink v-for="tag in tags" :key="tag" :class="{ active: filters.tag === tag }" :to="{ name: 'archives', query: withFilter('tag', tag) }">#{{ tag }}</RouterLink>
      </div>
    </section>

    <section class="archive-index container-wide">
      <RequestState v-if="loading" state="loading" light />
      <RequestState v-else-if="error" state="error" :message="error" light @retry="load" />
      <template v-else-if="groups.length">
        <div class="archive-summary"><CalendarDays :size="17" /><span>共 {{ filteredPosts.length }} 篇文章</span></div>
        <section v-for="group in groups" :key="group.year" class="archive-year">
          <h2>{{ group.year }}</h2>
          <div class="archive-months">
            <div v-for="month in group.months" :key="month.month" class="archive-month">
              <div class="archive-month-label">{{ month.month || '--' }}<small>月</small></div>
              <div class="archive-rows">
                <RouterLink v-for="post in month.posts" :key="post.id" class="archive-row" :to="{ name: 'article', params: { slug: post.slug } }">
                  <span class="archive-date">{{ formatDate(post.date) }}</span>
                  <div><strong>{{ post.title }}</strong><small>{{ post.category }}<template v-if="post.tags.length"> · {{ post.tags.map((tag) => '#' + tag).join(' ') }}</template></small></div>
                  <ArrowUpRight :size="17" />
                </RouterLink>
              </div>
            </div>
          </div>
        </section>
      </template>
      <div v-else class="empty-state">
        <Search :size="24" />
        <p>{{ posts.length ? '没有符合条件的档案。' : '文章档案还是空的。' }}</p>
        <RouterLink v-if="hasFilters" class="text-link" :to="{ name: 'archives' }">清除筛选<ArrowUpRight :size="16" /></RouterLink>
      </div>
    </section>
  </main>
</template>
