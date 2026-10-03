<script setup>
defineProps({ photo: { type: Object, required: true }, compact: { type: Boolean, default: false } })
</script>

<template>
  <span class="photo-credit" :class="{ 'photo-credit--compact': compact }">
    <span v-if="!compact">{{ photo.caption }}<span class="credit-divider"> / </span></span>
    <a
      :href="photo.source"
      target="_blank"
      rel="noopener noreferrer"
      :title="photo.caption + ' · 摄影 ' + photo.author"
      >{{ photo.author }}</a
    >
    <a :href="photo.licenseUrl" target="_blank" rel="noopener noreferrer">{{
      photo.license === 'Public domain' ? '公有领域' : photo.license
    }}</a>
  </span>
</template>
