<script setup>
import { onBeforeUnmount, ref, watch } from 'vue'
import { Heart, LoaderCircle, MessageCircle, RefreshCw } from 'lucide-vue-next'
import { fetchLikes, setPostLike } from '../services/api'

const props = defineProps({ postId: { type: [Number, String], required: true } })
const liked = ref(false)
const count = ref(0)
const loading = ref(true)
const pending = ref(false)
const error = ref('')
let epoch = 0
let controller

const load = async () => {
  const current = ++epoch
  controller?.abort()
  controller = new AbortController()
  loading.value = true
  pending.value = false
  error.value = ''
  liked.value = false
  count.value = 0
  try {
    const result = await fetchLikes(props.postId, { signal: controller.signal })
    if (current !== epoch) return
    liked.value = result.liked
    count.value = result.likeCount
  } catch (cause) {
    if (current === epoch && cause?.name !== 'AbortError') error.value = cause?.message || '暂时无法读取点赞。'
  } finally {
    if (current === epoch) loading.value = false
  }
}

const toggle = async () => {
  if (pending.value || loading.value) return
  const current = epoch
  pending.value = true
  error.value = ''
  try {
    const result = await setPostLike(props.postId, !liked.value, { signal: controller.signal })
    if (current !== epoch) return
    liked.value = result.liked
    count.value = result.likeCount
  } catch (cause) {
    if (current === epoch && cause?.name !== 'AbortError') error.value = cause?.message || '点赞失败，请重试。'
  } finally {
    if (current === epoch) pending.value = false
  }
}

watch(() => props.postId, load, { immediate: true })
onBeforeUnmount(() => { epoch++; controller?.abort() })
</script>

<template>
  <section class="article-reactions container-narrow" aria-label="文章互动">
    <div class="reaction-actions">
      <button class="like-button" :class="{ 'is-liked': liked }" type="button" :aria-pressed="liked" :aria-label="liked ? '取消点赞' : '点赞这篇文章'" :disabled="loading || pending || Boolean(error)" @click="toggle">
        <LoaderCircle v-if="loading || pending" class="request-state-spinner" :size="19" />
        <Heart v-else :size="19" :fill="liked ? 'currentColor' : 'none'" />
        <span>{{ liked ? '已点赞' : '点赞' }}</span><strong>{{ count }}</strong>
      </button>
      <a class="reaction-comment-link" href="#comments-title"><MessageCircle :size="18" />写留言</a>
    </div>
    <p v-if="error" class="reaction-error" role="alert">{{ error }}<button type="button" class="icon-button" title="重试点赞请求" aria-label="重试点赞请求" @click="load"><RefreshCw :size="16" /></button></p>
  </section>
</template>
