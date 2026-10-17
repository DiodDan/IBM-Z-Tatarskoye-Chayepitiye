<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue'
import MessageBubble from './MessageBubble.vue'
import type { Message } from '../types/api'

const props = defineProps<{
  messages: Message[]
  isWaitingForReply: boolean
}>()

const emit = defineEmits<{ suggest: [text: string] }>()

/** A pause this long starts a new session, which gets its own time divider. */
const SESSION_GAP_MS = 15 * 60_000

const placeholder: Message = {
  id: 'pending-reply',
  role: 'assistant',
  content: 'Waiting for response…',
  createdAt: '',
  status: 'pending',
}

interface Row {
  message: Message
  /** Divider time shown above this message, when it starts a session. */
  session: Date | null
}

const rows = computed<Row[]>(() => {
  let previousTime = -Infinity
  const result: Row[] = props.messages.map((message) => {
    const time = Date.parse(message.createdAt)
    const session = time - previousTime >= SESSION_GAP_MS ? new Date(time) : null
    previousTime = time
    return { message, session }
  })
  if (props.isWaitingForReply) result.push({ message: placeholder, session: null })
  return result
})

function formatSession(date: Date): string {
  const time = date.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', hour12: false })
  if (date.toDateString() === new Date().toDateString()) return time
  return `${date.toLocaleDateString([], { day: 'numeric', month: 'short' })}, ${time}`
}

const container = ref<HTMLElement | null>(null)

watch(
  () => [props.messages.length, props.isWaitingForReply],
  async () => {
    await nextTick()
    container.value?.scrollTo({ top: container.value.scrollHeight, behavior: 'smooth' })
  },
  { immediate: true },
)
</script>

<template>
  <div ref="container" class="list" role="log" aria-label="Messages" aria-live="polite">
    <div class="column">
      <template v-for="row in rows" :key="row.message.id">
        <div v-if="row.session" class="session">
          <span></span>
          <time :datetime="row.session.toISOString()">{{ formatSession(row.session) }}</time>
          <span></span>
        </div>
        <MessageBubble
          :message="row.message"
          :suggestions-disabled="isWaitingForReply"
          @suggest="(text) => emit('suggest', text)"
        />
      </template>
    </div>
  </div>
</template>

<style scoped>
.list {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: 24px var(--gutter);
}
.column {
  max-width: 900px;
  margin: 0 auto;
}
.session {
  display: flex;
  align-items: center;
  gap: 14px;
  margin: 4px 0 18px;
  font-size: 10px;
  color: var(--text-muted);
}
.session span {
  flex: 1;
  height: 1px;
  background: var(--border);
}
</style>
