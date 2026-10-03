<script setup>
import { ArrowUpRight, Clock3, Heart, MessageCircle } from 'lucide-vue-next'
import { RouterLink } from 'vue-router'
import PhotoCredit from './PhotoCredit.vue'
import { FALLBACK_COVER, formatDate } from '../utils/blog'

defineProps({
  post: { type: Object, required: true },
  index: { type: Number, default: 0 },
  lead: { type: Boolean, default: false }
})
const recoverImage = (event) => {
  const image = event.target
  if (image.getAttribute('src') === FALLBACK_COVER) return
  image.removeAttribute('srcset')
  image.src = FALLBACK_COVER
}
</script>

<template>
  <article v-reveal class="story-card" :class="{ 'story-card--lead': lead }">
    <RouterLink
      class="story-card-link"
      :to="{ name: 'article', params: { slug: post.slug } }"
      :aria-label="'阅读《' + post.title + '》'"
    >
      <div class="story-card-image">
        <img
          :src="post.image"
          :srcset="post.imageSrcset || undefined"
          :sizes="
            lead
              ? '(max-width: 760px) 100vw, 60vw'
              : '(max-width: 600px) 100vw, (max-width: 1000px) 50vw, 33vw'
          "
          :alt="post.imageCredit?.caption || post.title"
          width="1280"
          height="960"
          loading="lazy"
          @error="recoverImage"
        /><span class="story-number"
          >{{ String(index + 1).padStart(2, '0')
          }}<template v-if="lead"> / 最新手记</template></span
        ><span class="story-image-arrow" aria-hidden="true"><ArrowUpRight :size="23" /></span>
      </div>
      <div class="story-card-body">
        <div class="story-meta">
          <span>{{ post.category }}</span
          ><time :datetime="post.date">{{ formatDate(post.date) }}</time>
        </div>
        <h3>{{ post.title }}</h3>
        <p>{{ post.excerpt }}</p>
        <div class="story-footer">
          <span><Clock3 :size="13" />{{ post.readTime }}</span
          ><span class="story-interactions"
            ><span :aria-label="post.likeCount + ' 次点赞'"
              ><Heart :size="13" />{{ post.likeCount }}</span
            ><span :aria-label="post.commentCount + ' 则留言'"
              ><MessageCircle :size="13" />{{ post.commentCount }}</span
            ></span
          ><ArrowUpRight :size="18" />
        </div>
      </div>
    </RouterLink>
    <PhotoCredit v-if="post.imageCredit" :photo="post.imageCredit" compact />
  </article>
</template>
