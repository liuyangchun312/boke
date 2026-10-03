<script setup>
import { computed } from 'vue'
import { ArrowDown, ArrowRight, ArrowUpRight, MapPin, Search, X } from 'lucide-vue-next'
import { RouterLink, useRoute } from 'vue-router'
import PostCard from '../components/PostCard.vue'
import PhotoCredit from '../components/PhotoCredit.vue'
import RequestState from '../components/RequestState.vue'
import { usePublicPosts } from '../composables/usePublicPosts'
import { photographs } from '../data/photographs'
import { filterPosts } from '../utils/blog'

const route = useRoute()
const { posts, categories, tags, loading, error, load } = usePublicPosts()
const filters = computed(() => ({
  category: String(route.query.category || ''),
  tag: String(route.query.tag || ''),
  q: String(route.query.q || '')
}))
const hasFilters = computed(() =>
  Boolean(filters.value.category || filters.value.tag || filters.value.q)
)
const filteredPosts = computed(() => filterPosts(posts.value, filters.value))
const leadPost = computed(() => filteredPosts.value[0])
const secondaryPosts = computed(() => filteredPosts.value.slice(1))
const sectionTitle = computed(() =>
  filters.value.q
    ? `关于“${filters.value.q}”`
    : filters.value.tag
      ? `#${filters.value.tag}`
      : filters.value.category || '最近写下的'
)
const withFilter = (key, value) => ({ ...route.query, [key]: value || undefined })
const topics = [
  {
    title: '江河与田野',
    english: 'RIVERS & FIELDS',
    category: '江河田园',
    photo: photographs.fields,
    note: '水路、农时，与四季的细小变化。'
  },
  {
    title: '小城的日常',
    english: 'EVERYDAY TAIHE',
    category: '小城日常',
    photo: photographs.village,
    note: '走进街巷，留一段时间给生活。'
  },
  {
    title: '人文与旧迹',
    english: 'PLACES & MEMORIES',
    category: '人文古迹',
    photo: photographs.street,
    note: '从一首诗、一处地名，读懂家乡。'
  }
]
</script>

<template>
  <main id="main-content" class="home-page">
    <section class="field-hero" aria-labelledby="journal-title">
      <picture class="field-hero-image"
        ><source media="(max-width: 600px)" srcset="/photos/taihe-river-640.webp" />
        <img
          src="/photos/taihe-river-1800.webp"
          :alt="photographs.river.caption + '，河水沿着绿岸流过村庄'"
          width="1800"
          height="1200"
          fetchpriority="high"
      /></picture>
      <div class="field-hero-content container-wide">
        <div class="hero-overline">
          <span class="issue-dot"></span><span>刘杨春的地方记录</span
          ><span class="hero-edition">TAIHE FIELD NOTES</span>
        </div>
        <h1 id="journal-title">
          <span>泰和</span><span>乡土手记<span class="title-stop">。</span></span>
        </h1>
        <p class="field-hero-intro">从赣江边出发，<br />把家乡的风物与日常，慢慢写下来。</p>
        <a class="hero-read-link" href="#story-index"
          ><span>翻开手记</span><ArrowDown :size="18"
        /></a>
      </div>
      <div class="field-hero-bottom container-wide">
        <span><MapPin :size="13" />江西 · 吉安 · 泰和</span
        ><PhotoCredit :photo="photographs.river" compact />
      </div>
    </section>
    <div class="journal-colophon container-wide">
      <span>一方水土，一本手记。</span><span>乡土 / 人文 / 日常</span><span>BY LIU YANGCHUN</span>
    </div>
    <section id="story-index" class="story-index container-wide">
      <div v-reveal class="section-heading">
        <div>
          <p class="eyebrow">01 / THE JOURNAL</p>
          <h2>{{ sectionTitle }}</h2>
        </div>
        <RouterLink class="index-link" :to="{ name: 'archives', query: route.query }"
          >按年月翻阅<ArrowUpRight :size="18"
        /></RouterLink>
      </div>
      <div class="category-strip" aria-label="文章分类">
        <div class="category-list">
          <RouterLink
            class="category-pill"
            :class="{ active: !filters.category }"
            :to="{ name: 'home', query: withFilter('category', '') }"
            >全部手记</RouterLink
          ><RouterLink
            v-for="category in categories"
            :key="category"
            class="category-pill"
            :class="{ active: filters.category === category }"
            :to="{ name: 'home', query: withFilter('category', category) }"
            >{{ category }}</RouterLink
          >
        </div>
        <span class="category-count" aria-live="polite"
          >{{ loading ? '--' : String(filteredPosts.length).padStart(2, '0') }} 篇</span
        >
      </div>
      <div v-if="hasFilters" class="active-filter">
        <span v-if="filters.q">搜索：{{ filters.q }}</span
        ><span v-if="filters.tag">主题：{{ filters.tag }}</span
        ><RouterLink :to="{ name: 'home', hash: '#story-index' }"
          ><X :size="14" />清除筛选</RouterLink
        >
      </div>
      <RequestState v-if="loading" state="loading" light />
      <RequestState v-else-if="error" state="error" :message="error" light @retry="load" />
      <template v-else-if="filteredPosts.length"
        ><PostCard :post="leadPost" :index="0" lead />
        <div class="story-grid">
          <PostCard
            v-for="(post, index) in secondaryPosts"
            :key="post.id"
            :post="post"
            :index="index + 1"
          /></div
      ></template>
      <div v-else class="empty-state">
        <Search :size="24" />
        <p>{{ posts.length ? '没有找到匹配的手记。' : '第一篇手记还在路上。' }}</p>
        <RouterLink
          v-if="posts.length"
          class="text-link"
          :to="{ name: 'home', hash: '#story-index' }"
          >查看全部手记<ArrowRight :size="16"
        /></RouterLink>
      </div>
      <div v-if="!loading && !error && tags.length" class="tag-section">
        <span class="tag-intro">字里行间</span>
        <div class="tag-list">
          <RouterLink
            v-for="tag in tags"
            :key="tag"
            class="tag-link"
            :class="{ active: filters.tag === tag }"
            :to="{ name: 'home', query: withFilter('tag', filters.tag === tag ? '' : tag) }"
            >{{ tag }}</RouterLink
          >
        </div>
      </div>
    </section>
    <section class="field-topics">
      <div class="container-wide">
        <div v-reveal class="section-heading">
          <div>
            <p class="eyebrow">02 / A SENSE OF PLACE</p>
            <h2>在泰和，慢一点。</h2>
          </div>
          <p class="section-note">把熟悉的地方，重新看一遍。</p>
        </div>
        <div class="topic-grid">
          <article v-for="(topic, index) in topics" :key="topic.title" v-reveal class="topic">
            <RouterLink :to="{ name: 'home', query: { category: topic.category } }"
              ><div class="topic-image">
                <img
                  :src="topic.photo.image"
                  :srcset="topic.photo.srcset"
                  sizes="(max-width: 600px) 100vw, (max-width: 900px) 45vw, 30vw"
                  :alt="topic.photo.caption"
                  width="1280"
                  height="960"
                  loading="lazy"
                /><span>{{ String(index + 1).padStart(2, '0') }}</span>
              </div>
              <div class="topic-heading">
                <h3>{{ topic.title }}</h3>
                <ArrowUpRight :size="24" />
              </div>
              <small>{{ topic.english }}</small>
              <p>{{ topic.note }}</p></RouterLink
            ><PhotoCredit :photo="topic.photo" compact />
          </article>
        </div>
      </div>
    </section>
    <section v-reveal class="letter-section container-wide">
      <div class="letter-label">
        <span class="eyebrow">03 / A NOTE FROM HOME</span
        ><span class="letter-seal" aria-hidden="true">刘</span>
      </div>
      <div class="letter-copy">
        <h2>家乡不只是远方。<br />也是眼前，<em>值得认真过的一天。</em></h2>
        <p>
          我是刘杨春。写快阁的诗，写赣江的风，也写街巷里真实而普通的生活。这本手记，留给泰和，也留给愿意停一停的你。
        </p>
        <RouterLink class="text-link" :to="{ name: 'about' }"
          >关于我与这本手记<ArrowUpRight :size="18"
        /></RouterLink>
      </div>
    </section>
  </main>
</template>
