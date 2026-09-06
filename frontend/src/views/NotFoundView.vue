<script setup>
import { ArrowLeft } from 'lucide-vue-next'
import { RouterLink } from 'vue-router'
</script>

<template>
  <main id="main-content" class="not-found-page container-wide">
    <p class="eyebrow">404 / PAGE NOT FOUND</p>
    <strong>404</strong>
    <h1>这一页没有留下手记。</h1>
    <p>地址可能已经改变，也可能从未存在。</p>
    <RouterLink class="text-link" :to="{ name: 'home' }"><ArrowLeft :size="17" />回到文章首页</RouterLink>
  </main>
</template>
