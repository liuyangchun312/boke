<script setup>
import { computed, nextTick, ref, watch } from 'vue'
import { Menu, Search, X } from 'lucide-vue-next'
import { RouterLink, useRoute, useRouter } from 'vue-router'

const route = useRoute()
const router = useRouter()
const mobileMenuOpen = ref(false)
const searchOpen = ref(false)
const searchQuery = ref('')
const searchInput = ref(null)

const publicQuery = computed(() => ({
  ...(route.query.category ? { category: route.query.category } : {}),
  ...(route.query.tag ? { tag: route.query.tag } : {}),
  ...(route.query.q ? { q: route.query.q } : {})
}))

const toggleSearch = async () => {
  searchOpen.value = !searchOpen.value
  searchQuery.value = String(route.query.q || '')
  if (searchOpen.value) {
    await nextTick()
    searchInput.value?.focus()
  }
}

const search = () => {
  const q = searchQuery.value.trim()
  router.push({ name: 'home', query: { ...publicQuery.value, q: q || undefined } })
  searchOpen.value = false
  mobileMenuOpen.value = false
}

watch(() => route.fullPath, () => {
  mobileMenuOpen.value = false
  searchOpen.value = false
})
</script>

<template>
  <header class="site-header">
    <RouterLink class="brand" :to="{ name: 'home' }" aria-label="返回刘杨春博客首页">
      <span class="brand-mark">刘</span>
      <span class="brand-copy"><strong>刘杨春</strong><small>TAIHE FIELD NOTES</small></span>
    </RouterLink>

    <nav class="main-nav" :class="{ 'is-open': mobileMenuOpen }" aria-label="主导航">
      <RouterLink class="nav-link" :to="{ name: 'home' }">阅读</RouterLink>
      <RouterLink class="nav-link" :to="{ name: 'archives', query: publicQuery }">泰和档案</RouterLink>
      <RouterLink class="nav-link" :to="{ name: 'about' }">关于我</RouterLink>
    </nav>

    <div class="header-actions">
      <button class="icon-button" :class="{ active: searchOpen }" type="button" title="搜索文章" aria-label="搜索文章" :aria-expanded="searchOpen" @click="toggleSearch"><Search :size="18" :stroke-width="1.8" /></button>
      <button class="icon-button menu-trigger" type="button" title="打开菜单" aria-label="打开菜单" :aria-expanded="mobileMenuOpen" @click="mobileMenuOpen = !mobileMenuOpen"><X v-if="mobileMenuOpen" :size="20" /><Menu v-else :size="20" /></button>
    </div>
  </header>

  <form v-if="searchOpen" class="search-dock" role="search" @submit.prevent="search">
    <div class="search-dock-input">
      <Search :size="20" />
      <input ref="searchInput" v-model="searchQuery" type="search" placeholder="搜索标题、主题或标签" aria-label="搜索文章" @keyup.esc="searchOpen = false" />
      <button v-if="searchQuery" class="icon-button" type="button" title="清除搜索" aria-label="清除搜索" @click="searchQuery = ''"><X :size="17" /></button>
    </div>
    <button class="search-submit" type="submit">搜索</button>
  </form>
</template>
