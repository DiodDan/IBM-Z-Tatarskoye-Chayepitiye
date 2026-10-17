<script setup lang="ts">
import { computed } from 'vue'
import type { Message } from '../types/api'

const props = defineProps<{ message: Message }>()

const status = computed(() => props.message.status ?? 'allowed')
const isUser = computed(() => props.message.role === 'user')
</script>

<template>
  <div class="row" :class="isUser ? 'from-user' : 'from-assistant'">
    <!-- Hidden AI reply -->
    <div v-if="!isUser && status === 'blocked'" class="bubble notice blocked">
      This response was hidden for safety reasons.
    </div>

    <!-- AI failed -->
    <div v-else-if="!isUser && status === 'failed'" class="bubble notice failed">
      Something went wrong generating this response.
    </div>

    <!-- Normal message (user messages always show their own text) -->
    <div v-else class="bubble" :class="{ pending: status === 'pending', 'user-blocked': isUser && status === 'blocked' }">
      <p class="content">{{ message.content }}</p>
      <span v-if="isUser && status === 'blocked'" class="label">Blocked</span>
    </div>
  </div>
</template>

<style scoped>
.row {
  display: flex;
}
.from-user {
  justify-content: flex-end;
}
.bubble {
  max-width: min(680px, 85%);
  padding: 10px 14px;
  border-radius: 14px;
  background: var(--surface-1);
  border: 1px solid var(--border);
}
.from-user .bubble {
  background: var(--accent-soft);
  border-color: transparent;
}
.content {
  margin: 0;
  white-space: pre-wrap;
  word-break: break-word;
}
.pending {
  opacity: 0.6;
}
.notice {
  font-style: italic;
  color: var(--text-muted);
}
.blocked {
  border-color: var(--danger);
}
.user-blocked {
  outline: 1px solid var(--danger);
}
.label {
  display: inline-block;
  margin-top: 6px;
  font-size: 12px;
  color: var(--danger);
}
</style>
