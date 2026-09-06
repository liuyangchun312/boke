<script setup>
import { computed } from 'vue'
import { ArrowUpRight, Search, Tag } from 'lucide-vue-next'
import { RouterLink, useRoute } from 'vue-router'
import PostCard from '../components/PostCard.vue'
import RequestState from '../components/RequestState.vue'
import { usePublicPosts } from '../composables/usePublicPosts'
import { filterPosts } from '../utils/blog'

const route = useRoute()
const { posts, categories, tags, loading, error, load } = usePublicPosts()

const filters = computed(() => ({
  category: String(route.query.category || ''),
  tag: String(route.query.tag || ''),
  q: String(route.query.q || '')
}))
const filteredPosts = computed(() => filterPosts(posts.value, filters.value))
const leadPost = computed(() => filteredPosts.value[0])
const secondaryPosts = computed(() => filteredPosts.value.slice(1))
const sectionTitle = computed(() => {
  if (filters.value.q) return '搜索“' + filters.value.q + '”'
  if (filters.value.tag) return '#' + filters.value.tag
  return filters.value.category || '写给家乡的几篇文章'
})

const categoryQuery = (category) => ({
  ...(filters.value.q ? { q: filters.value.q } : {}),
  ...(category ? { category } : {})
})
const tagQuery = (tag) => ({
  ...(filters.value.q ? { q: filters.value.q } : {}),
  ...(tag ? { tag } : {})
})
</script>

<template>
  <main id="main-content" class="home-page">
    <section class="hero container-wide">
      <div class="hero-copy reveal reveal-delay-1">
        <p class="issue-label"><span class="issue-dot"></span>江西 · 吉安 · 泰和</p>
        <h1>刘杨春的<br /><em>泰和乡土手记。</em></h1>
        <p class="hero-intro">从赣江边的小城出发，记录快阁的月色、武山的白凤乌鸡，以及泰和街巷里不该被匆忙略过的日常。</p>
        <RouterLink v-if="leadPost" class="text-link" :to="{ name: 'article', params: { slug: leadPost.slug } }">读一篇泰和故事<ArrowUpRight :size="17" /></RouterLink>
        <a v-else class="text-link" href="#story-index">查看文章索引<ArrowUpRight :size="17" /></a>
      </div>
      <div class="hero-art reveal reveal-delay-2">
        <div class="hero-art-frame">
          <img v-if="leadPost" :src="leadPost.image" :alt="leadPost.title" />
          <div v-else class="hero-placeholder" aria-hidden="true"><span>泰和</span><small>JIANGXI / TAIHE</small></div>
          <div class="image-caption"><span>乡土影像 · 01</span><span>JIANGXI / TAIHE</span></div>
        </div>
        <div class="hero-stamp"><span>TAI</span><strong>和</strong><span>JIANGXI</span></div>
      </div>
    </section>

    <section class="category-strip container-wide" aria-label="文章分类">
      <span class="strip-label">索引 / CATEGORIES</span>
      <div class="category-list">
        <RouterLink class="category-pill" :class="{ active: !filters.category }" :to="{ name: 'home', query: categoryQuery('') }">全部</RouterLink>
        <RouterLink v-for="category in categories" :key="category" class="category-pill" :class="{ active: filters.category === category }" :to="{ name: 'home', query: categoryQuery(category) }">{{ category }}</RouterLink>
      </div>
      <span class="category-count">{{ filteredPosts.length.toString().padStart(2, '0') }} 篇</span>
    </section>

    <section id="story-index" class="story-index container-wide">
      <div class="section-heading">
        <div><p class="eyebrow">TAIHE NOTES / 泰和文章</p><h2>{{ sectionTitle }}</h2></div>
        <span class="heading-rule"></span>
      </div>
      <RequestState v-if="loading" state="loading" light />
      <RequestState v-else-if="error" state="error" :message="error" light @retry="load" />
      <div v-else-if="filteredPosts.length" class="story-grid">
        <PostCard :post="leadPost" :index="0" lead />
        <div class="story-secondary"><PostCard v-for="(post, index) in secondaryPosts" :key="post.id" :post="post" :index="index + 1" /></div>
      </div>
      <div v-else class="empty-state">
        <Search :size="24" />
        <p>{{ posts.length ? '没有找到匹配的文章。' : '第一篇泰和手记还在路上。' }}</p>
        <RouterLink v-if="posts.length" class="text-link" :to="{ name: 'home' }">清除筛选<ArrowUpRight :size="16" /></RouterLink>
      </div>
    </section>

    <section v-if="!loading && !error && tags.length" class="tag-section container-wide">
      <div class="tag-intro"><Tag :size="17" /><span>按主题浏览</span></div>
      <div class="tag-list">
        <RouterLink v-for="tag in tags" :key="tag" class="tag-link" :class="{ active: filters.tag === tag }" :to="{ name: 'home', query: tagQuery(filters.tag === tag ? '' : tag) }">#{{ tag }}</RouterLink>
      </div>
    </section>

    <section class="letter-section container-wide">
      <div class="letter-copy">
        <p class="eyebrow">ABOUT LIU YANGCHUN / 关于作者</p>
        <h2>从泰和出发，<br /><em>把家乡认真写下来。</em></h2>
        <p>刘杨春在这里记录快阁的晚晴、赣江的风，也记录县城里真实而普通的一天。</p>
      </div>
      <RouterLink class="about-link-panel" :to="{ name: 'about' }"><span>关于刘杨春与这本泰和手记</span><ArrowUpRight :size="20" /></RouterLink>
    </section>
  </main>
</template>
