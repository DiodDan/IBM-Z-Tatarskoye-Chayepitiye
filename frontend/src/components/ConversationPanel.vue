<script setup lang="ts">
import MessageList from './MessageList.vue'
import MessageComposer from './MessageComposer.vue'
import type { ConversationPreview, Message } from '../types/api'

defineProps<{
  conversation: ConversationPreview | null
  messages: Message[]
  isLoadingHistory: boolean
  isSending: boolean
  error: string | null
  sendError: string | null
  onSend: (content: string) => Promise<boolean>
}>()
</script>

<template>
  <section class="panel">
    <header class="header">
      <h1>{{ conversation?.title ?? 'New conversation' }}</h1>
    </header>

    <p v-if="error" class="state error">{{ error }}</p>
    <p v-else-if="isLoadingHistory" class="state">Loading conversation…</p>
    <div v-else-if="messages.length === 0 && !isSending" class="state empty">
      <h2>How can I help you today?</h2>
    </div>
    <MessageList v-else :messages="messages" :is-waiting-for-reply="isSending" />

    <MessageComposer :disabled="isSending || isLoadingHistory" :error="sendError" :on-send="onSend" />
  </section>
</template>

<style scoped>
.panel {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;
  min-width: 0;
}
.header {
  padding: 12px 16px;
  border-bottom: 1px solid var(--border);
}
h1 {
  font-size: 16px;
  font-weight: 600;
  margin: 0;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}
.state {
  flex: 1;
  padding: 24px 16px;
  color: var(--text-muted);
}
.empty {
  display: flex;
  align-items: center;
  justify-content: center;
}
.empty h2 {
  font-weight: 500;
  color: var(--text);
}
.error {
  color: var(--danger);
}
</style>
