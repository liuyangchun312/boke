import { createRouter, createWebHistory } from 'vue-router'
import { applySiteMeta } from '../utils/seo'

export const routes = [
  { path: '/', name: 'home', component: () => import('../views/HomeView.vue'), meta: { title: '刘杨春的个人博客 · 泰和乡土手记' } },
  { path: '/archives', name: 'archives', component: () => import('../views/ArchivesView.vue'), meta: { title: '泰和档案 · 刘杨春' } },
  { path: '/about', name: 'about', component: () => import('../views/AboutView.vue'), meta: { title: '关于作者 · 刘杨春' } },
  { path: '/posts/:slug', name: 'article', component: () => import('../views/ArticleView.vue'), meta: { title: '文章 · 刘杨春' } },
  { path: '/admin', name: 'admin', component: () => import('../views/AdminView.vue'), meta: { title: '编辑工作台 · 刘杨春', noindex: true } },
  { path: '/:pathMatch(.*)*', name: 'not-found', component: () => import('../views/NotFoundView.vue'), meta: { title: '页面未找到 · 刘杨春', noindex: true } }
]

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes,
  scrollBehavior(to, from, savedPosition) {
    if (savedPosition) return savedPosition
    const top = (document.querySelector('.site-header')?.offsetHeight || 84) + 24
    if (to.hash) return { el: to.hash, top }
    if (to.name === 'home' && (from.name === 'home' || to.query.category || to.query.tag || to.query.q)) return { el: '#story-index', top }
    return { top: 0 }
  }
})

router.afterEach((to, from, failure) => {
  if (failure) return
  // A table-of-contents hash changes the URL, but not the article metadata.
  if (to.name === 'article' && from.name === 'article' && to.params.slug === from.params.slug) return
  applySiteMeta({
    title: to.meta.title,
    path: to.path,
    noindex: Boolean(to.meta.noindex),
    type: to.name === 'article' ? 'article' : 'website'
  })
})

export default router
