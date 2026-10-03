<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { ArrowRight, Menu, Search, X } from 'lucide-vue-next'
import { RouterLink, useRoute, useRouter } from 'vue-router'

const route = useRoute()
const router = useRouter()
const mobileMenuOpen = ref(false)
const searchOpen = ref(false)
const searchQuery = ref('')
const searchInput = ref(null)
const searchDialog = ref(null)
const searchTrigger = ref(null)
const menuTrigger = ref(null)
const navigation = ref(null)
let mobileViewport
const publicQuery = computed(() => ({
  ...(route.query.category ? { category: route.query.category } : {}),
  ...(route.query.tag ? { tag: route.query.tag } : {}),
  ...(route.query.q ? { q: route.query.q } : {})
}))

const closeSearch = () => {
  searchDialog.value?.close()
  searchOpen.value = false
  searchTrigger.value?.focus()
}
const toggleSearch = async () => {
  if (searchOpen.value) return closeSearch()
  mobileMenuOpen.value = false
  searchQuery.value = String(route.query.q || '')
  searchOpen.value = true
  searchDialog.value.showModal()
  await nextTick()
  searchInput.value?.focus()
}
const search = () => {
  const q = searchQuery.value.trim()
  closeSearch()
  router.push({
    name: 'home',
    query: { ...publicQuery.value, q: q || undefined },
    hash: '#story-index'
  })
}
const toggleMobileMenu = async () => {
  mobileMenuOpen.value = !mobileMenuOpen.value
  if (!mobileMenuOpen.value) return
  await nextTick()
  navigation.value?.querySelector('a')?.focus()
}
const closeMenuOnDesktop = (event) => {
  if (!event.matches) mobileMenuOpen.value = false
}
const closeMenuOnFocusExit = (event) => {
  if (
    mobileMenuOpen.value &&
    event.relatedTarget &&
    !event.currentTarget.contains(event.relatedTarget)
  )
    mobileMenuOpen.value = false
}
const handleEscape = (event) => {
  if (event.key !== 'Escape' || !mobileMenuOpen.value) return
  mobileMenuOpen.value = false
  menuTrigger.value?.focus()
}
watch(
  () => route.fullPath,
  () => {
    mobileMenuOpen.value = false
    searchDialog.value?.close()
    searchOpen.value = false
  }
)
watch([mobileMenuOpen, searchOpen], ([menu, search]) => {
  document.body.style.overflow = menu || search ? 'hidden' : ''
})
onMounted(() => {
  document.addEventListener('keydown', handleEscape)
  mobileViewport = window.matchMedia('(max-width: 760px)')
  mobileViewport.addEventListener('change', closeMenuOnDesktop)
})
onBeforeUnmount(() => {
  document.removeEventListener('keydown', handleEscape)
  mobileViewport?.removeEventListener('change', closeMenuOnDesktop)
  document.body.style.overflow = ''
})
</script>

<template>
  <header class="site-header" @focusout="closeMenuOnFocusExit">
    <div class="header-inner container-wide">
      <RouterLink class="brand" :to="{ name: 'home' }" aria-label="返回刘杨春的泰和乡土手记首页"
        ><span class="brand-mark">刘</span
        ><span class="brand-copy"
          ><strong>泰和乡土手记</strong><small>LIU YANGCHUN / FIELD NOTES</small></span
        ></RouterLink
      >
      <nav
        id="main-navigation"
        ref="navigation"
        class="main-nav"
        :class="{ 'is-open': mobileMenuOpen }"
        aria-label="主导航"
      >
        <RouterLink class="nav-link" :to="{ name: 'home' }"><span>01</span>阅读手记</RouterLink
        ><RouterLink class="nav-link" :to="{ name: 'archives', query: publicQuery }"
          ><span>02</span>年月档案</RouterLink
        ><RouterLink class="nav-link" :to="{ name: 'about' }"><span>03</span>关于作者</RouterLink>
        <p class="mobile-nav-note">从家乡出发，把日子写下来。</p>
      </nav>
      <div class="header-actions">
        <button
          ref="searchTrigger"
          class="icon-button"
          type="button"
          title="搜索文章"
          aria-label="搜索文章"
          :aria-expanded="searchOpen"
          aria-controls="journal-search"
          @click="toggleSearch"
        >
          <Search :size="19" :stroke-width="1.5" /></button
        ><button
          ref="menuTrigger"
          class="icon-button menu-trigger"
          type="button"
          :title="mobileMenuOpen ? '关闭菜单' : '打开菜单'"
          :aria-label="mobileMenuOpen ? '关闭菜单' : '打开菜单'"
          :aria-expanded="mobileMenuOpen"
          aria-controls="main-navigation"
          @click="toggleMobileMenu"
        >
          <X v-if="mobileMenuOpen" :size="22" /><Menu v-else :size="22" />
        </button>
      </div>
    </div>
  </header>
  <dialog
    id="journal-search"
    ref="searchDialog"
    class="search-dialog"
    aria-labelledby="search-title"
    @close="searchOpen = false"
    @click="
      (event) => {
        if (event.target === searchDialog) closeSearch()
      }
    "
  >
    <div class="search-dialog-head">
      <p class="eyebrow">SEARCH THE JOURNAL</p>
      <button
        class="icon-button"
        type="button"
        title="关闭搜索"
        aria-label="关闭搜索"
        @click="closeSearch"
      >
        <X :size="21" />
      </button>
    </div>
    <h2 id="search-title">找一篇手记。</h2>
    <form class="search-dock" role="search" @submit.prevent="search">
      <div class="search-dock-input">
        <Search :size="21" :stroke-width="1.5" /><input
          ref="searchInput"
          v-model="searchQuery"
          type="search"
          placeholder="地名、风物，或一段日常"
          aria-label="搜索标题、正文或标签"
        />
      </div>
      <button class="search-submit" type="submit" title="开始搜索" aria-label="开始搜索">
        <ArrowRight :size="23" />
      </button>
    </form>
  </dialog>
</template>
