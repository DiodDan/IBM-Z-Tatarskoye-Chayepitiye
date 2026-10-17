<script setup lang="ts">
import { nextTick, ref, watch } from 'vue'
import MessageBubble from './MessageBubble.vue'
import type { Message } from '../types/api'

const props = defineProps<{
  messages: Message[]
  isWaitingForReply: boolean
}>()

const container = ref<HTMLElement | null>(null)

watch(
  () => [props.messages.length, props.isWaitingForReply],
  async () => {
    await nextTick()
    container.value?.scrollTo({ top: container.value.scrollHeight, behavior: 'smooth' })
  },
)
</script>

<template>
  <div ref="container" class="list">
    <MessageBubble v-for="message in messages" :key="message.id" :message="message" />
    <div v-if="isWaitingForReply" class="typing" aria-live="polite">AI is thinking…</div>
  </div>
</template>

<style scoped>
.list {
  flex: 1;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 24px 16px;
}
.typing {
  color: var(--text-muted);
  font-style: italic;
}
</style>
