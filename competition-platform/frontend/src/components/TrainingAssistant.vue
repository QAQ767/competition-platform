<template>
  <ChatAssistant ref="assistant" :competition="competition" :tag="tag" />
</template>
<script setup>
import { ref } from 'vue'
import ChatAssistant from './ChatAssistant.vue'
defineProps({
  competition: { type: String, default: '' },
  tag: { type: String, default: '' },
})
const assistant = ref(null)
defineExpose({
  openWithResource: (site) => assistant.value?.openWithResource(site),
})
</script>
