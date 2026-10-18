<script setup lang="ts">
import { computed } from 'vue'
import MarkdownText from './MarkdownText.vue'
import { presentResponse } from '../services/responsePresentation'
import ModelScoreCard from './ModelScoreCard.vue'
import Chip from '@carbon/icons-vue/es/chip/20'
import type { Message } from '../types/api'

const props = defineProps<{
  message: Message
  suggestionsDisabled: boolean
  /** Admin view only: show stored assistant response and trajectory model scores. */
  showRisk?: boolean
  promptRisk?: number | null
  /** Admin view: the app-wide "Response №" of this reply. */
  responseNumber?: number | null
  /** Admin view: the message picked in the Responses panel or found by search. */
  highlighted?: boolean
}>()

const emit = defineEmits<{ suggest: [text: string] }>()
function suggest(text: string) {
  if (!props.suggestionsDisabled && props.message.role === 'assistant') emit('suggest', text)
}

const status = computed(() => props.message.status ?? 'allowed')
const isUser = computed(() => props.message.role === 'user')
const presentation = computed(() => presentResponse(props.message.content, props.message.suggestions))

const riskLabel = computed(() => {
  const risk = props.promptRisk
  return typeof risk === 'number' ? `${risk.toFixed(2)} / 1` : 'Pending'
})
</script>

<template>
  <!-- User -->
  <article v-if="isUser" class="message user" :data-message-id="message.id" aria-label="Your message">
    <div class="label">You</div>
    <div class="row">
      <div
        class="bubble"
        :class="{ pending: status === 'pending', 'user-blocked': status === 'blocked', highlighted }"
      >
        {{ message.content }}
        <span v-if="status === 'blocked'" class="blocked-tag">Blocked</span>
      </div>
    </div>
  </article>

  <!-- Assistant -->
  <article
    v-else
    class="message assistant"
    :data-message-id="message.id"
    aria-label="Assistant response"
    :aria-busy="status === 'pending' || undefined"
  >
    <div class="label">Response</div>
    <div class="grid">
      <span class="cpu-tile avatar"><Chip /></span>
      <div class="name">
        AI Assistant
        <span v-if="responseNumber" class="number" :class="{ highlighted }">Response №{{ responseNumber }}</span>
      </div>

      <div v-if="status === 'blocked'" class="bubble notice" :class="{ highlighted }">
        This response was hidden for safety reasons.
      </div>
      <div v-else-if="status === 'failed'" class="bubble notice" :class="{ highlighted }">
        Something went wrong generating this response.
      </div>
      <div v-else class="bubble" :class="{ pending: status === 'pending', highlighted }">
        <MarkdownText :text="presentation.text" />

        <div v-if="presentation.suggestions.length" class="suggestions">
          <button
            v-for="suggestion in presentation.suggestions"
            :key="suggestion"
            type="button"
            :disabled="suggestionsDisabled"
            @click="suggest(suggestion)"
          >
            {{ suggestion }}
          </button>
        </div>
      </div>

      <div v-if="showRisk" class="model-assessment">
        <ModelScoreCard :assessment="message.modelAssessment" />
        <p v-if="promptRisk != null" class="legacy-risk">Legacy user prompt score · {{ riskLabel }}</p>
      </div>
    </div>
  </article>
</template>

<style scoped>
.message {
  margin-bottom: 28px;
}
.label {
  margin-bottom: 8px;
  font-size: 10px;
  font-weight: 600;
  letter-spacing: 1.3px;
  text-transform: uppercase;
  color: var(--text-muted);
}
.bubble {
  font-size: 16px;
  line-height: 1.55;
  border-radius: 12px;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}

/* User */
.user .row {
  display: flex;
  justify-content: flex-end;
}
.user .bubble {
  max-width: min(680px, 80%);
  padding: 9px 16px;
  background: var(--user-bubble);
  color: var(--user-bubble-text);
  box-shadow: var(--shadow);
}
.user-blocked {
  outline: 1px solid var(--danger);
}
.blocked-tag {
  display: block;
  margin-top: 4px;
  font-size: 12px;
  color: var(--danger);
}

/* Assistant */
.grid {
  display: grid;
  grid-template-columns: 32px minmax(0, 1fr);
  column-gap: 12px;
  width: min(100%, 600px);
}
.avatar {
  grid-column: 1;
  grid-row: 1 / span 2;
  border-radius: 50%;
}
.name {
  grid-column: 2;
  margin-bottom: 6px;
  font-size: 11px;
  font-weight: 600;
  color: var(--text-muted);
}
.assistant .bubble {
  grid-column: 2;
  padding: 14px 16px;
  background: var(--surface);
  border: 1px solid var(--border);
  box-shadow: var(--shadow);
}
.bubble p {
  margin: 0;
}
.bubble p + p {
  margin-top: 10px;
}
.notice {
  font-style: italic;
  color: var(--text-muted);
}
.pending {
  opacity: 0.6;
}
.suggestions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 12px;
  white-space: normal;
}
.suggestions button {
  padding: 3px 10px;
  border-radius: 14px;
  background: var(--chip);
  color: var(--chip-text);
  font-size: 11px;
  font-weight: 600;
  line-height: 18px;
}
.suggestions button:hover:not(:disabled) {
  background: var(--chip-hover);
}
.number {
  margin-left: 6px;
  padding: 0 6px;
  border-radius: 8px;
  background: var(--surface-hover);
  font-size: 10px;
}
.number.highlighted {
  background: var(--highlight);
  color: #fff;
}
/* Admin: the response picked in the Responses panel (or found by search). */
.bubble.highlighted {
  border-color: var(--highlight);
  background: var(--highlight-bg);
  box-shadow: 0 0 0 3px var(--highlight-glow);
  animation: glow 1.6s ease-out;
}
@keyframes glow {
  0%,
  40% {
    box-shadow: 0 0 0 7px var(--highlight-glow);
  }
  100% {
    box-shadow: 0 0 0 3px var(--highlight-glow);
  }
}
.model-assessment { grid-column: 2; margin-top: 8px; }
.legacy-risk { font-size: 11px; color: var(--text-muted); }
.risk {
  grid-column: 2;
  margin-top: 6px;
  text-align: right;
  font-size: 10px;
  color: var(--text-muted);
}
@media (max-width: 720px) {
  .grid {
    grid-template-columns: 28px minmax(0, 1fr);
    column-gap: 8px;
  }
  .avatar {
    width: 28px;
    height: 28px;
  }
  .user .bubble {
    max-width: 90%;
  }
}
</style>
