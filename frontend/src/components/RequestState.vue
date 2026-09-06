<script setup>
import { AlertCircle, FileText, LoaderCircle, RotateCcw } from 'lucide-vue-next'

defineProps({
  state: { type: String, required: true },
  message: { type: String, default: '' },
  light: { type: Boolean, default: false }
})

defineEmits(['retry'])
</script>

<template>
  <div class="request-state" :class="{ 'request-state--light': light }" role="status">
    <LoaderCircle v-if="state === 'loading'" class="request-state-spinner" :size="25" aria-hidden="true" />
    <AlertCircle v-else-if="state === 'error'" :size="25" aria-hidden="true" />
    <FileText v-else :size="25" aria-hidden="true" />
    <p>{{ message || (state === 'loading' ? '正在取回文章…' : state === 'error' ? '暂时无法读取文章。' : '这里还没有文章。') }}</p>
    <button v-if="state === 'error'" class="retry-button" type="button" @click="$emit('retry')"><RotateCcw :size="15" />重试</button>
  </div>
</template>
