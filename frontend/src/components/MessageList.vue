<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue'
import MessageBubble from './MessageBubble.vue'
import type { Message } from '../types/api'

const props = defineProps<{
  messages: Message[]
  isWaitingForReply: boolean
  /** Admin view: suggestions can't be clicked. */
  readonly?: boolean
  /** Admin view: show each reply's stored model assessment. */
  showRisk?: boolean
  /** Admin view: message to highlight and scroll to; scrolls again whenever `highlightKey` changes. */
  highlightId?: string | null
  highlightKey?: number
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
  /** Assistant rows (admin only): risk of the user prompt it answers. */
  promptRisk: number | null
}

const rows = computed<Row[]>(() => {
  let previousTime = -Infinity
  let lastUserRisk: number | null = null
  const result: Row[] = props.messages.map((message) => {
    const time = Date.parse(message.createdAt)
    const session = time - previousTime >= SESSION_GAP_MS ? new Date(time) : null
    previousTime = time
    if (message.role === 'user') lastUserRisk = (message as { promptRisk?: number | null }).promptRisk ?? null
    return { message, session, promptRisk: message.role === 'assistant' ? lastUserRisk : null }
  })
  if (props.isWaitingForReply) result.push({ message: placeholder, session: null, promptRisk: null })
  return result
})

function formatSession(date: Date): string {
  const time = date.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', hour12: false })
  if (date.toDateString() === new Date().toDateString()) return time
  return `${date.toLocaleDateString([], { day: 'numeric', month: 'short' })}, ${time}`
}

const container = ref<HTMLElement | null>(null)

// New messages: scroll to the bottom — unless a highlighted message should stay in view.
watch(
  () => [props.messages.length, props.isWaitingForReply, props.highlightId, props.highlightKey],
  async () => {
    await nextTick()
    const target = props.highlightId
      ? container.value?.querySelector<HTMLElement>(`[data-message-id="${props.highlightId}"]`)
      : null
    if (target) target.scrollIntoView({ block: 'center', behavior: 'smooth' })
    else container.value?.scrollTo({ top: container.value.scrollHeight, behavior: 'smooth' })
  },
  { immediate: true },
)

const responseNumberOf = (message: Message) => (message as { responseNumber?: number | null }).responseNumber ?? null
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
          :suggestions-disabled="isWaitingForReply || readonly === true"
          :show-risk="showRisk"
          :prompt-risk="row.promptRisk"
          :response-number="responseNumberOf(row.message)"
          :highlighted="!!highlightId && row.message.id === highlightId"
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
