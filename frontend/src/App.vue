<script setup>
import { computed } from 'vue'
import { RouterView, useRoute } from 'vue-router'
import SiteFooter from './components/SiteFooter.vue'
import SiteHeader from './components/SiteHeader.vue'

const route = useRoute()
const isAdmin = computed(() => route.name === 'admin')
</script>

<template>
  <div class="app-shell" :class="{ 'is-admin': isAdmin }">
    <a class="skip-link" href="#main-content">跳到正文</a>
    <div class="grain" aria-hidden="true"></div>
    <SiteHeader />
    <RouterView />
    <SiteFooter v-if="!isAdmin" />
  </div>
</template>
