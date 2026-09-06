<script setup>
import { onBeforeUnmount, ref, watch } from 'vue'
import { Check, LoaderCircle, MessageCircle, Send } from 'lucide-vue-next'
import { createComment, fetchComments } from '../services/api'

const props = defineProps({
  postId: { type: [Number, String], required: true }
})

const comments = ref([])
const loading = ref(true)
const error = ref('')
const submitError = ref('')
const submitted = ref(false)
const submitting = ref(false)
const author = ref('')
const content = ref('')
let controller
let requestId = 0
let submittedTimer

const formatCommentDate = (value) => {
  if (!value) return ''
  try {
    return new Intl.DateTimeFormat('zh-CN', {
      year: 'numeric', month: 'long', day: 'numeric', hour: '2-digit', minute: '2-digit'
    }).format(new Date(value))
  } catch { return String(value).slice(0, 16).replace('T', ' ') }
}

const load = async () => {
  const current = ++requestId
  controller?.abort()
  controller = new AbortController()
  loading.value = true
  error.value = ''
  try {
    const result = await fetchComments(props.postId, { signal: controller.signal })
    if (current === requestId) comments.value = Array.isArray(result) ? result : []
  } catch (cause) {
    if (cause?.name !== 'AbortError' && current === requestId) error.value = cause?.message || '暂时无法读取留言。'
  } finally {
    if (current === requestId) loading.value = false
  }
}

const submit = async () => {
  const cleanAuthor = author.value.trim()
  const cleanContent = content.value.trim()
  submitError.value = ''
  submitted.value = false
  if (!cleanAuthor || !cleanContent) {
    submitError.value = '请留下称呼和想说的话。'
    return
  }
  if (cleanAuthor.length > 40 || cleanContent.length > 1000) {
    submitError.value = '称呼请控制在 40 字内，留言请控制在 1000 字内。'
    return
  }
  submitting.value = true
  try {
    const created = await createComment(props.postId, { author: cleanAuthor, content: cleanContent })
    comments.value.push(created)
    content.value = ''
    submitted.value = true
    window.clearTimeout(submittedTimer)
    submittedTimer = window.setTimeout(() => { submitted.value = false }, 3200)
  } catch (cause) {
    submitError.value = cause?.message || '留言没有提交成功，请稍后重试。'
  } finally {
    submitting.value = false
  }
}

watch(() => props.postId, load, { immediate: true })
onBeforeUnmount(() => {
  requestId += 1
  controller?.abort()
  window.clearTimeout(submittedTimer)
})
</script>

<template>
  <section class="comment-section container-narrow" aria-labelledby="comments-title">
    <div class="comment-heading">
      <div>
        <p class="eyebrow">READER NOTES / 读者留言</p>
        <h2 id="comments-title">在这里，留下一句话。</h2>
      </div>
      <span>{{ comments.length.toString().padStart(2, '0') }} 则</span>
    </div>

    <div class="comment-layout">
      <form class="comment-form" @submit.prevent="submit">
        <label for="comment-author">怎么称呼你</label>
        <input id="comment-author" v-model="author" maxlength="40" autocomplete="name" placeholder="你的名字或昵称" />
        <label for="comment-content">想说的话</label>
        <textarea id="comment-content" v-model="content" maxlength="1000" rows="5" placeholder="聊聊文章，也可以说说你记忆里的泰和。"></textarea>
        <div class="comment-form-foot">
          <p>留言公开可见，请友善交流。</p>
          <button type="submit" :disabled="submitting">
            <LoaderCircle v-if="submitting" class="request-state-spinner" :size="15" />
            <Send v-else :size="15" />
            {{ submitting ? '正在提交' : '发布留言' }}
          </button>
        </div>
        <p v-if="submitError" class="comment-form-message is-error" role="alert">{{ submitError }}</p>
        <p v-else-if="submitted" class="comment-form-message is-success" role="status"><Check :size="14" />留言已发布，谢谢你认真读到这里。</p>
      </form>

      <div class="comment-list" aria-live="polite">
        <div v-if="loading" class="comment-state"><LoaderCircle class="request-state-spinner" :size="20" /><span>正在读取留言</span></div>
        <div v-else-if="error" class="comment-state"><span>{{ error }}</span><button type="button" @click="load">重新加载</button></div>
        <article v-for="comment in comments" v-else :key="comment.id" class="comment-item">
          <div class="comment-avatar">{{ String(comment.author || '读').slice(0, 1) }}</div>
          <div>
            <header><strong>{{ comment.author }}</strong><time :datetime="comment.createdAt">{{ formatCommentDate(comment.createdAt) }}</time></header>
            <p>{{ comment.content }}</p>
          </div>
        </article>
        <div v-if="!loading && !error && !comments.length" class="comment-empty"><MessageCircle :size="21" /><p>这里还很安静。<br />欢迎留下第一则读者手记。</p></div>
      </div>
    </div>
  </section>
</template>
