<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import Checkmark from '@carbon/icons-vue/es/checkmark/16'
import ChevronDown from '@carbon/icons-vue/es/chevron--down/16'
import Copy from '@carbon/icons-vue/es/copy/16'
import SidePanelClose from '@carbon/icons-vue/es/side-panel--close/16'
import AppModal from './AppModal.vue'
import type { AdminMessage } from '../types/admin'

const props = defineProps<{
  messages: AdminMessage[]
  riskThreshold: number
  isLoading: boolean
}>()

const emit = defineEmits<{ close: [] }>()

/** One AI reply together with the user prompt it answers and that prompt's risk data. */
interface ResponseEntry {
  number: number
  prompt: string
  text: string
  status: AdminMessage['status']
  risk: number | null
  reason: string | null
  responseTimeMs: number | null
}

const responses = computed<ResponseEntry[]>(() => {
  const entries: ResponseEntry[] = []
  let lastUser: AdminMessage | null = null
  for (const message of props.messages) {
    if (message.role === 'user') {
      lastUser = message
      continue
    }
    entries.push({
      number: entries.length + 1,
      prompt: lastUser?.content ?? '',
      text: message.content,
      status: message.status,
      risk: lastUser?.promptRisk ?? null,
      reason: lastUser?.promptRiskReason ?? null,
      responseTimeMs: message.responseTimeMs,
    })
  }
  return entries
})

type Filter = 'all' | 'reason' | 'ascending' | 'descending'
const filters: { id: Filter; label: string }[] = [
  { id: 'all', label: 'All' },
  { id: 'reason', label: 'Reason' },
  { id: 'ascending', label: 'Scores (↓ to ↑)' },
  { id: 'descending', label: 'Scores (↑ to ↓)' },
]
const activeFilter = ref<Filter>('all')

// Unclassified entries (null) always go last, whatever the order.
const byNullLast = <T,>(a: T | null, b: T | null, compare: (x: T, y: T) => number) =>
  a === null ? (b === null ? 0 : 1) : b === null ? -1 : compare(a, b)

const visibleResponses = computed(() => {
  const list = [...responses.value]
  switch (activeFilter.value) {
    case 'reason':
      return list.sort((a, b) => byNullLast(a.reason, b.reason, (x, y) => x.localeCompare(y)) || a.number - b.number)
    case 'ascending':
      return list.sort((a, b) => byNullLast(a.risk, b.risk, (x, y) => x - y) || a.number - b.number)
    case 'descending':
      return list.sort((a, b) => byNullLast(a.risk, b.risk, (x, y) => y - x) || a.number - b.number)
    default:
      return list
  }
})

watch(
  () => props.messages,
  () => {
    activeFilter.value = 'all'
    dialog.value = null
  },
)

const isHigh = (entry: ResponseEntry) => entry.risk !== null && entry.risk >= props.riskThreshold
const scoreText = (entry: ResponseEntry) =>
  entry.risk === null ? 'Pending' : `${entry.risk.toFixed(2)} / 1 (${isHigh(entry) ? 'high' : 'low'} risk)`
const timingText = (ms: number | null) =>
  ms === null ? 'Not measured' : `${ms < 1000 ? `${ms} ms` : `${(ms / 1000).toFixed(1)}s`} response latency`
const responseText = (entry: ResponseEntry) =>
  entry.status === 'blocked'
    ? 'Hidden for safety reasons.'
    : entry.status === 'failed'
      ? 'Generation failed.'
      : entry.text

const copied = ref<number | null>(null)
let copiedTimer: number | undefined

async function copyResponse(entry: ResponseEntry) {
  try {
    await navigator.clipboard.writeText(responseText(entry))
    copied.value = entry.number
    window.clearTimeout(copiedTimer)
    copiedTimer = window.setTimeout(() => (copied.value = null), 1500)
  } catch {
    /* clipboard unavailable: the icon simply doesn't change */
  }
}

const dialog = ref<{ type: 'response' | 'report'; entry: ResponseEntry } | null>(null)
</script>

<template>
  <aside class="responses" aria-label="Responses">
    <header class="head">
      <h2>Responses</h2>
      <button class="icon" type="button" aria-label="Hide responses panel" title="Hide panel" @click="emit('close')">
        <SidePanelClose />
      </button>
    </header>

    <section class="card filters" aria-label="Filters">
      <div class="filters-head">
        <h3>Filters</h3>
        <button class="link" type="button" @click="activeFilter = 'all'">Reset</button>
      </div>
      <div class="chips">
        <button
          v-for="filter in filters"
          :key="filter.id"
          type="button"
          :aria-pressed="activeFilter === filter.id"
          @click="activeFilter = filter.id"
        >
          {{ filter.label }}
        </button>
      </div>
    </section>

    <div class="scroll">
      <p v-if="isLoading" class="empty">Loading…</p>
      <p v-else-if="responses.length === 0" class="empty">No responses in this conversation yet.</p>

      <article v-for="entry in visibleResponses" :key="entry.number" class="card response">
        <header class="response-head">
          <h3>Response №{{ entry.number }}</h3>
          <button
            class="icon round"
            type="button"
            :aria-label="`Copy response ${entry.number}`"
            title="Copy response text"
            @click="copyResponse(entry)"
          >
            <Checkmark v-if="copied === entry.number" />
            <Copy v-else />
          </button>
        </header>
        <p class="preview">{{ responseText(entry) }}</p>
        <div class="right">
          <button class="link" type="button" @click="dialog = { type: 'response', entry }">See full response →</button>
        </div>

        <section class="section">
          <h4>Risk Metrics</h4>
          <div class="row">
            <span>Risk score · Threshold</span>
            <span v-if="entry.risk === null" class="value pending">Pending</span>
            <span v-else class="value" :class="{ high: isHigh(entry) }">
              {{ entry.risk.toFixed(2) }}/{{ riskThreshold.toFixed(2) }}
            </span>
          </div>
        </section>

        <section class="section">
          <h4 class="big">Report</h4>
          <div class="fields">
            <p>Reason · {{ entry.reason ?? 'Pending' }}</p>
            <p>Scores · {{ scoreText(entry) }}</p>
            <p>Timing · {{ timingText(entry.responseTimeMs) }}</p>
          </div>
          <div class="right">
            <button class="open" type="button" @click="dialog = { type: 'report', entry }">
              Open
              <ChevronDown />
            </button>
          </div>
        </section>
      </article>
    </div>

    <AppModal
      v-if="dialog"
      :title="dialog.type === 'response' ? `Response №${dialog.entry.number}` : `Report — Response №${dialog.entry.number}`"
      @close="dialog = null"
    >
      <dl class="details">
        <dt>User prompt</dt>
        <dd>{{ dialog.entry.prompt || '—' }}</dd>
        <template v-if="dialog.type === 'response'">
          <dt>Response</dt>
          <dd class="pre">{{ responseText(dialog.entry) }}</dd>
        </template>
        <template v-else>
          <dt>Risk score</dt>
          <dd>{{ scoreText(dialog.entry) }}</dd>
          <dt>Threshold</dt>
          <dd>{{ riskThreshold.toFixed(2) }}</dd>
          <dt>Reason</dt>
          <dd>{{ dialog.entry.reason ?? 'Pending: not classified yet' }}</dd>
          <dt>Timing</dt>
          <dd>{{ timingText(dialog.entry.responseTimeMs) }}</dd>
        </template>
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
  padding-right: 2px;
}
.empty {
  margin: 24px 8px;
  text-align: center;
  font-size: 13px;
  color: var(--text-muted);
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
