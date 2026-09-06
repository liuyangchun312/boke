<script setup>
import { ChevronRight, Clock3 } from 'lucide-vue-next'
import { RouterLink } from 'vue-router'
import { formatDate } from '../utils/blog'

defineProps({
  post: { type: Object, required: true },
  index: { type: Number, default: 0 },
  lead: { type: Boolean, default: false }
})
</script>

<template>
  <article class="story-card" :class="['accent-' + post.accent, lead ? 'story-card--lead' : 'story-card--compact']">
    <RouterLink class="story-card-link" :to="{ name: 'article', params: { slug: post.slug } }" :aria-label="'阅读《' + post.title + '》'">
      <div class="story-card-image"><img :src="post.image" :alt="post.title" loading="lazy" /><span class="story-number">{{ String(index + 1).padStart(2, '0') }}</span></div>
      <div class="story-card-body">
        <div class="story-meta"><span>{{ post.category }}</span><span>{{ formatDate(post.date) }}</span></div>
        <h3>{{ post.title }}</h3>
        <p>{{ post.excerpt }}</p>
        <div class="story-footer"><span><Clock3 :size="14" />{{ post.readTime }}</span><ChevronRight :size="17" /></div>
      </div>
    </RouterLink>
  </article>
</template>
