<script setup lang="ts">
import { nextTick, ref } from 'vue'
import type { ConversationPreview } from '../types/api'

const props = defineProps<{
  conversation: ConversationPreview
  active: boolean
  disabled: boolean
}>()

const emit = defineEmits<{
  select: [id: string]
  rename: [id: string, title: string]
  delete: [id: string]
}>()

const isEditing = ref(false)
const draftTitle = ref('')
const input = ref<HTMLInputElement | null>(null)

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
  if (confirm(`Delete "${props.conversation.title}"?`)) emit('delete', props.conversation.id)
}
</script>

<template>
  <li class="item" :class="{ active }">
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
      class="title"
      type="button"
      :disabled="disabled"
      :title="conversation.title"
      @click="emit('select', conversation.id)"
    >
      {{ conversation.title }}
    </button>

    <div v-if="!isEditing" class="actions">
      <button type="button" class="icon" :disabled="disabled" aria-label="Rename" @click="startEditing">✎</button>
      <button type="button" class="icon" :disabled="disabled" aria-label="Delete" @click="confirmDelete">🗑</button>
    </div>
  </li>
</template>

<style scoped>
.item {
  display: flex;
  align-items: center;
  gap: 4px;
  border-radius: 8px;
}
.item.active,
.item:hover {
  background: var(--surface-hover);
}
.title {
  flex: 1;
  min-width: 0;
  text-align: left;
  background: none;
  border: none;
  padding: 8px 10px;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}
.actions {
  display: none;
  gap: 2px;
  padding-right: 4px;
}
.item:hover .actions,
.item.active .actions {
  display: flex;
}
.icon {
  background: none;
  border: none;
  padding: 4px 6px;
  font-size: 13px;
}
.rename-input {
  flex: 1;
  margin: 2px;
}
</style>
