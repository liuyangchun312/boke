<script setup>
import { ArrowUp, LockKeyhole } from 'lucide-vue-next'
import { RouterLink } from 'vue-router'
const currentYear = new Date().getFullYear()
const toTop = () =>
  window.scrollTo({
    top: 0,
    behavior: window.matchMedia('(prefers-reduced-motion: reduce)').matches ? 'instant' : 'smooth'
  })
</script>

<template>
  <footer class="site-footer">
    <div class="container-wide">
      <div class="footer-top">
        <RouterLink class="footer-wordmark" :to="{ name: 'home' }"
          ><span class="eyebrow">LIU YANGCHUN / TAIHE FIELD NOTES</span
          ><strong>泰和乡土手记<span>。</span></strong></RouterLink
        ><button
          class="footer-up"
          type="button"
          title="回到顶部"
          aria-label="回到顶部"
          @click="toTop"
        >
          <ArrowUp :size="24" :stroke-width="1.5" />
        </button>
      </div>
      <div class="footer-bottom">
        <p>一方水土，值得长久记录。</p>
        <nav class="footer-links" aria-label="页脚导航">
          <RouterLink :to="{ name: 'home' }">阅读</RouterLink
          ><RouterLink :to="{ name: 'archives' }">档案</RouterLink
          ><RouterLink :to="{ name: 'about' }">关于</RouterLink
          ><RouterLink :to="{ name: 'about', hash: '#photographs' }">影像出处</RouterLink
          ><RouterLink
            class="footer-admin"
            :to="{ name: 'admin' }"
            title="站点管理"
            aria-label="站点管理"
            ><LockKeyhole :size="14"
          /></RouterLink>
        </nav>
        <span class="footer-copyright">© {{ currentYear }} 刘杨春</span>
      </div>
    </div>
  </footer>
</template>
