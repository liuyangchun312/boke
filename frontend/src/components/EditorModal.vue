<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { Check, Send, Undo2, X } from 'lucide-vue-next'
import { editorPayload, validateDraft } from '../utils/blog'

const props = defineProps({
  post: { type: Object, default: null },
  pending: { type: Boolean, default: false },
  serverError: { type: String, default: '' }
})
const emit = defineEmits(['close', 'save', 'publish', 'unpublish', 'dirty'])

const panel = ref(null)
const closeButton = ref(null)
const errors = ref({})
const draft = reactive({
  title: props.post?.title || '',
  slug: props.post?.slug || '',
  excerpt: props.post?.excerpt || '',
  category: props.post?.category || '',
  tagsText: Array.isArray(props.post?.tags) ? props.post.tags.join(', ') : '',
  coverImage: props.post?.coverImage || '',
  content: props.post?.content || '',
  status: props.post?.status || 'DRAFT'
})
const original = JSON.stringify(draft)
const isDirty = computed(() => JSON.stringify(draft) !== original)
const isPublished = computed(() => props.post?.status === 'PUBLISHED')

watch(isDirty, (value) => emit('dirty', value), { immediate: true })

const requestClose = () => {
  if (props.pending) return
  if (isDirty.value && !window.confirm('尚有未保存的修改，确定要离开编辑器吗？')) return
  emit('close')
}

const submit = (action) => {
  const found = validateDraft(draft)
  errors.value = found
  if (Object.keys(found).length) {
    nextTick(() => panel.value?.querySelector('[aria-invalid="true"]')?.focus())
    return
  }
  const status = isPublished.value && action === 'save' ? 'PUBLISHED' : 'DRAFT'
  emit(action, editorPayload(draft, status))
}

const requestUnpublish = () => {
  if (isDirty.value && !window.confirm('取消发布不会保存当前修改，确定继续吗？')) return
  emit('unpublish')
}

const onKeydown = (event) => {
  if (event.key === 'Escape') {
    event.preventDefault()
    requestClose()
    return
  }
  if (event.key !== 'Tab' || !panel.value) return
  const focusable = [...panel.value.querySelectorAll('button:not(:disabled), input:not(:disabled), textarea:not(:disabled), select:not(:disabled), [href]')]
  if (!focusable.length) return
  const first = focusable[0]
  const last = focusable[focusable.length - 1]
  if (event.shiftKey && document.activeElement === first) {
    event.preventDefault()
    last.focus()
  } else if (!event.shiftKey && document.activeElement === last) {
    event.preventDefault()
    first.focus()
  }
}

onMounted(() => {
  document.addEventListener('keydown', onKeydown)
  nextTick(() => closeButton.value?.focus())
})
onBeforeUnmount(() => {
  document.removeEventListener('keydown', onKeydown)
  emit('dirty', false)
})
</script>

<template>
  <div class="editor-overlay" @mousedown.self="requestClose">
    <aside ref="panel" class="editor-drawer" role="dialog" aria-modal="true" aria-labelledby="editor-title">
      <header class="editor-head">
        <div><p class="eyebrow">EDITOR / {{ post ? 'EDIT' : 'NEW' }}</p><h2 id="editor-title">{{ draft.title || '未命名文章' }}</h2></div>
        <button ref="closeButton" class="icon-button" type="button" title="关闭编辑器" aria-label="关闭编辑器" :disabled="pending" @click="requestClose"><X :size="19" /></button>
      </header>
      <div class="editor-form">
        <p v-if="serverError" class="editor-error" role="alert">{{ serverError }}</p>
        <label for="article-title">标题 <span aria-hidden="true">*</span></label>
        <input id="article-title" v-model="draft.title" type="text" placeholder="输入文章标题" :aria-invalid="Boolean(errors.title)" :aria-describedby="errors.title ? 'title-error' : undefined" @input="delete errors.title" />
        <small v-if="errors.title" id="title-error" class="field-error">{{ errors.title }}</small>

        <label for="article-slug">文章地址</label>
        <input id="article-slug" v-model="draft.slug" type="text" placeholder="留空则根据标题生成" :readonly="Boolean(post)" />
        <small class="field-note">{{ post ? '已发布或保存的文章地址保持锁定，避免旧链接失效。' : '可使用英文、数字和短横线；留空由系统生成。' }}</small>

        <label for="article-excerpt">摘要</label>
        <textarea id="article-excerpt" v-model="draft.excerpt" rows="3" placeholder="用一两句话介绍这篇文章"></textarea>

        <label for="article-category">分类 <span aria-hidden="true">*</span></label>
        <input id="article-category" v-model="draft.category" type="text" list="known-categories" placeholder="例如：泰和风物" :aria-invalid="Boolean(errors.category)" :aria-describedby="errors.category ? 'category-error' : undefined" @input="delete errors.category" />
        <slot name="category-options"></slot>
        <small v-if="errors.category" id="category-error" class="field-error">{{ errors.category }}</small>

        <label for="article-tags">标签 <small>用逗号分隔</small></label>
        <input id="article-tags" v-model="draft.tagsText" type="text" placeholder="快阁, 赣江" />

        <label for="article-image">封面图 URL</label>
        <input id="article-image" v-model="draft.coverImage" type="url" placeholder="https://…" />

        <label for="article-body">正文 <small>Markdown</small> <span aria-hidden="true">*</span></label>
        <textarea id="article-body" v-model="draft.content" class="body-editor" rows="16" placeholder="从这里开始写…" :aria-invalid="Boolean(errors.content)" :aria-describedby="errors.content ? 'content-error' : undefined" @input="delete errors.content"></textarea>
        <small v-if="errors.content" id="content-error" class="field-error">{{ errors.content }}</small>
      </div>
      <footer class="editor-foot">
        <button class="secondary-button" type="button" :disabled="pending" @click="requestClose">取消</button>
        <button v-if="isPublished" class="secondary-button danger-outline" type="button" :disabled="pending" @click="requestUnpublish"><Undo2 :size="16" />取消发布</button>
        <button class="primary-button" type="button" :disabled="pending" @click="submit('save')"><Check :size="17" />{{ pending ? '正在保存…' : isPublished ? '保存已发布修改' : '保存草稿' }}</button>
        <button v-if="!isPublished" class="primary-button publish-button" type="button" :disabled="pending" @click="submit('publish')"><Send :size="16" />{{ pending ? '正在发布…' : '发布文章' }}</button>
      </footer>
    </aside>
  </div>
</template>
