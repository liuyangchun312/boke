<script setup>
import { onBeforeUnmount, ref, watch } from 'vue'
import { Check, CornerDownRight, LoaderCircle, MessageCircle, Send } from 'lucide-vue-next'
import { createComment, fetchComments } from '../services/api'

const props = defineProps({
  postId: { type: [Number, String], required: true }
})

const comments = ref([])
const loading = ref(true)
const error = ref('')
const submitError = ref('')
const submitted = ref('')
const submitting = ref(false)
const author = ref('')
const content = ref('')
let controller
let requestId = 0
let submitController

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
  comments.value = []
  submitted.value = ''
  submitError.value = ''
  submitController?.abort()
  submitting.value = false
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
  if (submitting.value) return
  const current = requestId
  const cleanAuthor = author.value.trim()
  const cleanContent = content.value.trim()
  submitError.value = ''
  submitted.value = ''
  if (!cleanAuthor || !cleanContent) {
    submitError.value = '请留下称呼和想说的话。'
    return
  }
  if (cleanAuthor.length > 40 || cleanContent.length > 1000) {
    submitError.value = '称呼请控制在 40 字内，留言请控制在 1000 字内。'
    return
  }
  submitting.value = true
  submitController = new AbortController()
  try {
    const created = await createComment(props.postId, { author: cleanAuthor, content: cleanContent }, { signal: submitController.signal })
    if (current !== requestId) return
    if (created.status === 'APPROVED') comments.value.unshift(created)
    content.value = ''
    submitted.value = created.status === 'APPROVED' ? '留言已发布，谢谢你的分享。' : '留言已提交，审核通过后会显示在这里。'
  } catch (cause) {
    if (current === requestId && cause?.name !== 'AbortError') submitError.value = cause?.message || '留言没有提交成功，请稍后重试。'
  } finally {
    if (current === requestId) submitting.value = false
  }
}

watch(() => props.postId, load, { immediate: true })
onBeforeUnmount(() => {
  requestId += 1
  controller?.abort()
  submitController?.abort()
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
        <input id="comment-author" v-model="author" required maxlength="40" autocomplete="name" :disabled="submitting" placeholder="你的名字或昵称" />
        <label for="comment-content">想说的话</label>
        <textarea id="comment-content" v-model="content" required maxlength="1000" rows="5" :disabled="submitting" placeholder="聊聊文章，也可以说说你记忆里的泰和。"></textarea>
        <div class="comment-form-foot">
          <p>{{ content.length }} / 1000</p>
          <button type="submit" :disabled="submitting">
            <LoaderCircle v-if="submitting" class="request-state-spinner" :size="15" />
            <Send v-else :size="15" />
            {{ submitting ? '正在提交' : '提交留言' }}
          </button>
        </div>
        <p v-if="submitError" class="comment-form-message is-error" role="alert">{{ submitError }}</p>
        <p v-else-if="submitted" class="comment-form-message is-success" role="status"><Check :size="14" />{{ submitted }}</p>
      </form>

      <div class="comment-list" aria-live="polite">
        <div v-if="loading" class="comment-state"><LoaderCircle class="request-state-spinner" :size="20" /><span>正在读取留言</span></div>
        <div v-else-if="error" class="comment-state"><span>{{ error }}</span><button type="button" @click="load">重新加载</button></div>
        <article v-for="comment in comments" v-else :key="comment.id" class="comment-item">
          <div class="comment-avatar">{{ String(comment.author || '读').slice(0, 1) }}</div>
          <div>
            <header><strong>{{ comment.author }}</strong><time :datetime="comment.createdAt">{{ formatCommentDate(comment.createdAt) }}</time></header>
            <p>{{ comment.content }}</p>
            <div v-if="comment.reply" class="author-reply"><header><strong><CornerDownRight :size="14" />刘杨春 <span>作者</span></strong><time :datetime="comment.repliedAt">{{ formatCommentDate(comment.repliedAt) }}</time></header><p>{{ comment.reply }}</p></div>
          </div>
        </article>
        <div v-if="!loading && !error && !comments.length" class="comment-empty"><MessageCircle :size="21" /><p>这里还很安静。<br />欢迎留下第一则读者手记。</p></div>
      </div>
    </div>
  </section>
</template>
