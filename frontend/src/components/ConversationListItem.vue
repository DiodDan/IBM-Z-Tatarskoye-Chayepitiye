<script setup lang="ts">
import { nextTick, ref, watch } from 'vue'
import type { ConversationPreview } from '../types/api'

const props = defineProps<{
  conversation: ConversationPreview
  active: boolean
  disabled: boolean
  /** Admin view: no rename/delete. */
  readonly?: boolean
  /** Admin view: this is the conversation a highlighted response comes from; replays whenever flashKey changes. */
  flash?: boolean
  flashKey?: number
}>()

const emit = defineEmits<{
  select: [id: string]
  rename: [id: string, title: string]
  delete: [id: string]
}>()

const isEditing = ref(false)
const draftTitle = ref('')
const input = ref<HTMLInputElement | null>(null)
const root = ref<HTMLElement | null>(null)

// Restart the flash animation and bring the item into view each time a response from it is shown.
const isFlashing = ref(false)
watch(
  () => [props.flash, props.flashKey],
  async () => {
    if (!props.flash) return
    isFlashing.value = false
    await nextTick()
    isFlashing.value = true
    root.value?.scrollIntoView({ block: 'nearest', behavior: 'smooth' })
  },
  { immediate: true },
)

async function startEditing() {
  draftTitle.value = props.conversation.title
  isEditing.value = true
  await nextTick()
  input.value?.select()
}

function saveTitle() {
  if (!isEditing.value) return
  isEditing.value = false
  const title = draftTitle.value.trim()
  if (title && title !== props.conversation.title) emit('rename', props.conversation.id, title)
}

function confirmDelete() {
  if (confirm(`Delete "${props.conversation.title}"? This cannot be undone.`)) emit('delete', props.conversation.id)
}
</script>

<template>
  <li ref="root" class="item" :class="{ active, flashing: flash && isFlashing }">
    <input
      v-if="isEditing"
      ref="input"
      v-model="draftTitle"
      class="rename-input"
      maxlength="80"
      aria-label="Conversation title"
      @keydown.enter.prevent="saveTitle"
      @keydown.esc="isEditing = false"
      @blur="saveTitle"
    />
    <button
      v-else
      class="link"
      type="button"
      :disabled="disabled"
      :title="conversation.title"
      :aria-current="active ? 'page' : undefined"
      @click="emit('select', conversation.id)"
    >
      <strong>{{ conversation.title }}</strong>
      <small v-if="conversation.lastMessage">{{ conversation.lastMessage }}</small>
    </button>

    <div v-if="active && !isEditing && !readonly" class="actions">
      <button type="button" :disabled="disabled" @click="startEditing">Rename</button>
      <button type="button" :disabled="disabled" @click="confirmDelete">Delete</button>
    </div>
  </li>
</template>

<style scoped>
.item {
  border-radius: 8px;
  margin-bottom: 4px;
}
.item:hover {
  background: var(--surface-hover);
}
.item.active {
  background: var(--selected);
  color: var(--selected-text);
}
/* Admin: "this response came from here" — a glowing ring that pulses, then stays as a thin outline. */
.item.flashing {
  outline: 2px solid var(--highlight);
  outline-offset: -2px;
  animation: flash 1.6s ease-out;
}
@keyframes flash {
  0%,
  40% {
    box-shadow: 0 0 0 4px var(--highlight-glow);
  }
  100% {
    box-shadow: 0 0 0 0 transparent;
  }
}
.link {
  display: block;
  width: 100%;
  padding: 10px 10px;
  text-align: left;
}
.link:disabled {
  opacity: 1;
}
strong,
small {
  display: block;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}
strong {
  font-size: 14px;
  font-weight: 600;
}
small {
  margin-top: 2px;
  font-size: 11px;
  color: var(--text-muted);
}
.active small {
  color: var(--selected-muted);
}
.active .link:not(:last-child) {
  padding-bottom: 4px;
}
.actions {
  display: flex;
  gap: 4px;
  padding: 0 6px 8px;
}
.actions button {
  padding: 2px 6px;
  border-radius: 4px;
  font-size: 11px;
  color: var(--selected-text);
}
.actions button:hover:not(:disabled) {
  background: rgb(127 127 127 / 0.18);
}
.rename-input {
  display: block;
  width: calc(100% - 12px);
  margin: 6px;
  padding: 6px 8px;
  border: 1px solid var(--border);
  border-radius: 6px;
  background: var(--input);
  color: var(--text);
}
</style>
