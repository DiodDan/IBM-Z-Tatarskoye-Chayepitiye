<script setup lang="ts">
import ConversationListItem from './ConversationListItem.vue'
import ThemeToggle from './ThemeToggle.vue'
import type { ConversationPreview } from '../types/api'
import type { Theme } from '../services/theme.service'

defineProps<{
  conversations: ConversationPreview[]
  conversationCount: number
  activeConversationId: string | null
  isLoading: boolean
  disabled: boolean
  error: string | null
  theme: Theme
}>()

const emit = defineEmits<{
  newConversation: []
  select: [id: string]
  rename: [id: string, title: string]
  delete: [id: string]
  toggleTheme: []
}>()
</script>

<template>
  <aside class="sidebar">
    <button class="primary new-btn" type="button" :disabled="disabled" @click="emit('newConversation')">
      + New conversation
    </button>

    <p class="count">{{ conversationCount }} conversation{{ conversationCount === 1 ? '' : 's' }}</p>

    <p v-if="error" class="error">{{ error }}</p>
    <p v-if="isLoading" class="muted">Loading…</p>
    <p v-else-if="conversations.length === 0" class="muted">No conversations yet.</p>

    <ul class="list">
      <ConversationListItem
        v-for="conversation in conversations"
        :key="conversation.id"
        :conversation="conversation"
        :active="conversation.id === activeConversationId"
        :disabled="disabled"
        @select="(id) => emit('select', id)"
        @rename="(id, title) => emit('rename', id, title)"
        @delete="(id) => emit('delete', id)"
      />
    </ul>

    <ThemeToggle :theme="theme" @toggle="emit('toggleTheme')" />
  </aside>
</template>

<style scoped>
.sidebar {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 12px;
  background: var(--surface-1);
  border-right: 1px solid var(--border);
  height: 100%;
  min-height: 0;
}
.new-btn {
  width: 100%;
}
.count {
  font-size: 13px;
  color: var(--text-muted);
  margin: 4px 2px;
}
.list {
  list-style: none;
  margin: 0;
  padding: 0;
  flex: 1;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.muted {
  color: var(--text-muted);
  font-size: 14px;
  margin: 4px 2px;
}
.error {
  color: var(--danger);
  font-size: 14px;
  margin: 4px 2px;
}
</style>
