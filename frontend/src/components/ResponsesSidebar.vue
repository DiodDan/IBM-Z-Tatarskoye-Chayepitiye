<script setup lang="ts">
import { nextTick, ref, watch } from 'vue'
import Checkmark from '@carbon/icons-vue/es/checkmark/16'
import ChevronDown from '@carbon/icons-vue/es/chevron--down/16'
import Copy from '@carbon/icons-vue/es/copy/16'
import Renew from '@carbon/icons-vue/es/renew/16'
import SidePanelClose from '@carbon/icons-vue/es/side-panel--close/16'
import AppModal from './AppModal.vue'
import type { AdminResponse, ResponseSort } from '../types/admin'

const props = defineProps<{
  /** AI replies from every conversation, already sorted by the server. */
  responses: AdminResponse[]
  riskThreshold: number
  sort: ResponseSort
  isLoading: boolean
  error: string | null
  /** The reply currently highlighted in the chat. */
  activeMessageId: string | null
}>()

const emit = defineEmits<{
  close: []
  refresh: []
  changeSort: [sort: ResponseSort]
  show: [response: AdminResponse]
}>()

const filters: { id: ResponseSort; label: string }[] = [
  { id: 'newest', label: 'All' },
  { id: 'reason', label: 'Reason' },
  { id: 'risk-asc', label: 'Scores (↓ to ↑)' },
  { id: 'risk-desc', label: 'Scores (↑ to ↓)' },
]

const isHigh = (r: AdminResponse) => r.promptRisk !== null && r.promptRisk >= props.riskThreshold
const scoreText = (r: AdminResponse) =>
  r.promptRisk === null ? 'Pending' : `${r.promptRisk.toFixed(2)} / 1 (${isHigh(r) ? 'high' : 'low'} risk)`
const timingText = (ms: number | null) =>
  ms === null ? 'Not measured' : `${ms < 1000 ? `${ms} ms` : `${(ms / 1000).toFixed(1)}s`} response latency`

// Copies "Response №12": paste it into the search on the left to open the conversation it came from.
const copied = ref<number | null>(null)
let copiedTimer: number | undefined
async function copyNumber(r: AdminResponse) {
  try {
    await navigator.clipboard.writeText(`Response №${r.responseNumber}`)
    copied.value = r.responseNumber
    window.clearTimeout(copiedTimer)
    copiedTimer = window.setTimeout(() => (copied.value = null), 1500)
  } catch {
    /* clipboard unavailable: the icon simply doesn't change */
  }
}

const report = ref<AdminResponse | null>(null)

// Keep the card of the highlighted reply in view (e.g. after pasting its number into the search).
const scroller = ref<HTMLElement | null>(null)
watch(
  () => [props.activeMessageId, props.responses],
  async () => {
    if (!props.activeMessageId) return
    await nextTick()
    scroller.value
      ?.querySelector(`[data-message-id="${props.activeMessageId}"]`)
      ?.scrollIntoView({ block: 'nearest', behavior: 'smooth' })
  },
)
</script>

<template>
  <aside class="responses" aria-label="Responses">
    <header class="head">
      <h2>Responses</h2>
      <div class="head-actions">
        <button class="icon" type="button" aria-label="Reload" title="Reload responses and conversations" @click="emit('refresh')">
          <Renew />
        </button>
        <button class="icon" type="button" aria-label="Hide responses panel" title="Hide panel" @click="emit('close')">
          <SidePanelClose />
        </button>
      </div>
    </header>

    <section class="card filters" aria-label="Filters">
      <div class="filters-head">
        <h3>Filters</h3>
        <button class="link" type="button" @click="emit('changeSort', 'newest')">Reset</button>
      </div>
      <div class="chips">
        <button
          v-for="filter in filters"
          :key="filter.id"
          type="button"
          :aria-pressed="sort === filter.id"
          @click="emit('changeSort', filter.id)"
        >
          {{ filter.label }}
        </button>
      </div>
    </section>

    <div ref="scroller" class="scroll">
      <p v-if="error" class="empty error">{{ error }}</p>
      <p v-else-if="isLoading && responses.length === 0" class="empty">Loading…</p>
      <p v-else-if="responses.length === 0" class="empty">No responses yet.</p>

      <article
        v-for="r in responses"
        :key="r.messageId"
        :data-message-id="r.messageId"
        class="card response"
        :class="{ active: r.messageId === activeMessageId }"
      >
        <header class="response-head">
          <h3>Response №{{ r.responseNumber }}</h3>
          <button
            class="icon round"
            type="button"
            :aria-label="`Copy Response №${r.responseNumber}`"
            title="Copy the response number, then paste it into the search on the left"
            @click="copyNumber(r)"
          >
            <Checkmark v-if="copied === r.responseNumber" />
            <Copy v-else />
          </button>
        </header>
        <p class="source" :title="r.conversationTitle">
          {{ r.conversationTitle }} · User {{ r.userId.slice(0, 8) }}
        </p>
        <p class="preview">{{ r.content }}</p>
        <div class="right">
          <button class="link" type="button" @click="emit('show', r)">See full response →</button>
        </div>

        <section class="section">
          <h4>Risk Metrics</h4>
          <div class="row">
            <span>Risk score · Threshold</span>
            <span v-if="r.promptRisk === null" class="value pending">Pending</span>
            <span v-else class="value" :class="{ high: isHigh(r) }">
              {{ r.promptRisk.toFixed(2) }}/{{ riskThreshold.toFixed(2) }}
            </span>
          </div>
        </section>

        <section class="section">
          <h4 class="big">Report</h4>
          <div class="fields">
            <p>Reason · {{ r.promptRiskReason ?? 'Pending' }}</p>
            <p>Scores · {{ scoreText(r) }}</p>
            <p>Timing · {{ timingText(r.responseTimeMs) }}</p>
          </div>
          <div class="right">
            <button class="open" type="button" @click="report = r">
              Open
              <ChevronDown />
            </button>
          </div>
        </section>
      </article>
    </div>

    <AppModal v-if="report" :title="`Report — Response №${report.responseNumber}`" @close="report = null">
      <dl class="details">
        <dt>Conversation</dt>
        <dd>{{ report.conversationTitle }} (User {{ report.userId.slice(0, 8) }})</dd>
        <dt>User prompt</dt>
        <dd class="pre">{{ report.prompt || '—' }}</dd>
        <dt>Risk score</dt>
        <dd>{{ scoreText(report) }}</dd>
        <dt>Threshold</dt>
        <dd>{{ riskThreshold.toFixed(2) }}</dd>
        <dt>Reason</dt>
        <dd>{{ report.promptRiskReason ?? 'Pending: not classified yet' }}</dd>
        <dt>Timing</dt>
        <dd>{{ timingText(report.responseTimeMs) }}</dd>
      </dl>
    </AppModal>
  </aside>
</template>

<style scoped>
.responses {
  display: flex;
  flex-direction: column;
  gap: 6px;
  min-height: 0;
  height: 100%;
  padding: 12px;
  background: var(--panel);
  border-left: 1px solid var(--border);
}
.head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 4px;
}
.head-actions {
  display: flex;
  gap: 2px;
}
h2,
h3 {
  margin: 0;
}
.head h2,
.filters h3 {
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 1.2px;
  text-transform: uppercase;
}
.card {
  padding: 8px 10px;
  border: 1px solid var(--border);
  border-radius: 8px;
  background: var(--surface);
  box-shadow: var(--shadow);
}
.response.active {
  border-color: var(--highlight);
  box-shadow: 0 0 0 2px var(--highlight-glow);
}
.filters-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}
.chips {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}
.chips button {
  padding: 2px 9px;
  border: 1px solid var(--border);
  border-radius: 12px;
  font-size: 11px;
  font-weight: 600;
}
.chips button[aria-pressed='true'] {
  border-color: transparent;
  background: var(--chip);
  color: var(--chip-text);
}
.link {
  color: var(--chip-text);
  font-size: 11px;
  font-weight: 600;
  text-decoration: underline;
  text-underline-offset: 2px;
}
:root[data-theme='dark'] .link {
  color: #d7a9c8;
}
.icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 24px;
  height: 24px;
  border-radius: 5px;
  color: var(--text-muted);
}
.icon:hover {
  background: var(--surface-hover);
  color: var(--text);
}
.round {
  border-radius: 50%;
}
.scroll {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 2px 2px 2px 0;
}
.empty {
  margin: 24px 8px;
  text-align: center;
  font-size: 13px;
  color: var(--text-muted);
}
.empty.error {
  color: var(--danger);
}
.response-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.response-head h3 {
  font-size: 15px;
  font-weight: 700;
}
.source {
  margin: 0;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
  font-size: 11px;
  color: var(--text-muted);
}
.preview {
  display: -webkit-box;
  margin: 2px 0 0;
  overflow: hidden;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
  font-size: 12px;
  line-height: 17px;
}
.right {
  display: flex;
  justify-content: flex-end;
  margin: 3px 0;
}
.section {
  margin-top: 4px;
  padding-top: 5px;
  border-top: 1px solid var(--border);
}
h4 {
  margin: 0;
  font-size: 12px;
  font-weight: 700;
}
h4.big {
  font-size: 15px;
}
.row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 2px;
  font-size: 12px;
}
.value {
  flex-shrink: 0;
  font-weight: 700;
}
.value.pending {
  color: var(--text-muted);
  font-weight: 600;
}
/* High risk: red with a glow, as in the design. */
.value.high {
  color: #ff2a2a;
  text-shadow:
    0 0 4px rgb(255 0 0 / 0.6),
    0 0 10px rgb(255 0 0 / 0.3);
}
.fields {
  margin-top: 4px;
  font-size: 12px;
  line-height: 18px;
  overflow-wrap: anywhere;
}
.fields p {
  margin: 0;
}
.open {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  padding: 2px 6px;
  border-radius: 5px;
  font-size: 12px;
  font-weight: 600;
}
.open:hover {
  background: var(--surface-hover);
}
.details {
  display: grid;
  grid-template-columns: 110px minmax(0, 1fr);
  gap: 12px 16px;
  margin: 0;
  font-size: 15px;
}
.details dt {
  font-weight: 600;
  color: var(--text-muted);
}
.details dd {
  margin: 0;
  overflow-wrap: anywhere;
}
.pre {
  white-space: pre-wrap;
}
</style>
