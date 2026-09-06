<script setup>
import { LockKeyhole } from 'lucide-vue-next'
import { RouterLink } from 'vue-router'

const currentYear = new Date().getFullYear()
</script>

<template>
  <footer class="site-footer container-wide">
    <div class="footer-brand"><span class="brand-mark">刘</span><span>LIU YANGCHUN / TAIHE</span></div>
    <div class="footer-links">
      <RouterLink :to="{ name: 'home' }">文章索引</RouterLink>
      <RouterLink :to="{ name: 'archives' }">泰和档案</RouterLink>
      <RouterLink :to="{ name: 'about' }">关于作者</RouterLink>
      <RouterLink class="footer-admin" :to="{ name: 'admin' }" aria-label="站点管理"><LockKeyhole :size="11" />管理</RouterLink>
    </div>
    <div class="footer-meta"><span>从家乡出发，把日子写下来。</span><span>© {{ currentYear }} 刘杨春</span></div>
  </footer>
</template>
