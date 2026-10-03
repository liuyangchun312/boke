<script setup>
import { computed, reactive, ref } from 'vue'
import { Check, EyeOff, MessageSquare, Reply, Search, Send, Trash2, X } from 'lucide-vue-next'

const props = defineProps({
  comments: { type: Array, required: true },
  posts: { type: Array, required: true },
  pending: { type: Boolean, default: false }
})
const emit = defineEmits(['moderate', 'reply', 'delete', 'dirty'])
const search = ref('')
const status = ref('ALL')
const article = ref('')
const replyId = ref(null)
const drafts = reactive({})
const labels = { PENDING: '待审核', APPROVED: '已公开', HIDDEN: '已隐藏' }
const postMap = computed(() => new Map(props.posts.map((post) => [Number(post.id), post])))
const filtered = computed(() => props.comments.filter((comment) => {
  if (status.value !== 'ALL' && comment.status !== status.value) return false
  if (article.value && String(comment.postId) !== article.value) return false
  const query = search.value.trim().toLocaleLowerCase()
  return !query || [comment.author, comment.content, comment.reply, postMap.value.get(Number(comment.postId))?.title].filter(Boolean).join(' ').toLocaleLowerCase().includes(query)
}))
const date = (value) => {
  if (!value) return ''
  try {
    return new Intl.DateTimeFormat('zh-CN', { year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' }).format(new Date(value))
  } catch { return String(value).slice(0, 16).replace('T', ' ') }
}
const openReply = (comment) => {
  if (replyId.value !== null && drafts[replyId.value] !== (props.comments.find((item) => item.id === replyId.value)?.reply || '') && !window.confirm('回复尚未保存，确定打开另一则留言吗？')) return
  replyId.value = comment.id
  drafts[comment.id] = comment.reply || ''
  emit('dirty', false)
}
const closeReply = () => { replyId.value = null; emit('dirty', false) }
const submitReply = (comment) => emit('reply', comment, drafts[comment.id].trim(), closeReply)
</script>

<template>
  <div class="comment-manager">
    <div class="comment-manager-filters">
      <label class="admin-search"><Search :size="16" /><input v-model="search" type="search" placeholder="搜索称呼、留言或回复" aria-label="搜索留言" /></label>
      <select v-model="status" aria-label="按留言状态筛选"><option value="ALL">全部状态</option><option v-for="(label, value) in labels" :key="value" :value="value">{{ label }}</option></select>
      <select v-model="article" aria-label="按文章筛选留言"><option value="">全部文章</option><option v-for="post in posts" :key="post.id" :value="String(post.id)">{{ post.title }}</option></select>
      <span class="filtered-count">{{ filtered.length }} 则</span>
    </div>
    <div v-if="filtered.length" class="admin-comment-list">
      <article v-for="comment in filtered" :key="comment.id" class="admin-comment-row managed-comment">
        <div class="comment-avatar">{{ String(comment.author || '读').slice(0, 1) }}</div>
        <div class="admin-comment-copy">
          <header><strong>{{ comment.author }}</strong><span class="comment-status" :class="'status-' + comment.status?.toLowerCase()">{{ labels[comment.status] || '已公开' }}</span><time>{{ date(comment.createdAt) }}</time></header>
          <p>{{ comment.content }}</p>
          <small>来自《{{ postMap.get(Number(comment.postId))?.title || '已删除的文章' }}》</small>
          <div v-if="comment.reply && replyId !== comment.id" class="admin-saved-reply"><strong><Reply :size="14" />作者回复</strong><p>{{ comment.reply }}</p><time>{{ date(comment.repliedAt) }}</time></div>
          <form v-if="replyId === comment.id" class="admin-reply-form" @submit.prevent="submitReply(comment)">
            <label :for="'reply-' + comment.id">作者回复</label>
            <textarea :id="'reply-' + comment.id" v-model="drafts[comment.id]" maxlength="1000" rows="3" :disabled="pending" @input="emit('dirty', true)"></textarea>
            <div><span>{{ drafts[comment.id].length }} / 1000</span><button class="secondary-button compact" type="button" :disabled="pending" @click="closeReply"><X :size="15" />取消</button><button class="primary-button compact" type="submit" :disabled="pending"><Send :size="15" />{{ comment.reply && !drafts[comment.id].trim() ? '移除回复' : '保存回复' }}</button></div>
          </form>
        </div>
        <div class="comment-moderation-actions">
          <button v-if="comment.status !== 'APPROVED'" class="icon-button approve" type="button" title="审核通过并公开" :aria-label="'公开' + comment.author + '的留言'" :disabled="pending" @click="emit('moderate', comment, 'APPROVED')"><Check :size="17" /></button>
          <button v-if="comment.status !== 'HIDDEN'" class="icon-button" type="button" title="隐藏留言" :aria-label="'隐藏' + comment.author + '的留言'" :disabled="pending" @click="emit('moderate', comment, 'HIDDEN')"><EyeOff :size="17" /></button>
          <button class="icon-button" type="button" title="回复留言" :aria-label="'回复' + comment.author + '的留言'" :disabled="pending" @click="openReply(comment)"><Reply :size="17" /></button>
          <button class="icon-button danger" type="button" title="删除留言" :aria-label="'删除' + comment.author + '的留言'" :disabled="pending" @click="emit('delete', comment)"><Trash2 :size="17" /></button>
        </div>
      </article>
    </div>
    <div v-else class="admin-empty"><MessageSquare :size="23" /><p>{{ comments.length ? '没有符合筛选条件的留言' : '还没有读者留言' }}</p></div>
  </div>
</template>
