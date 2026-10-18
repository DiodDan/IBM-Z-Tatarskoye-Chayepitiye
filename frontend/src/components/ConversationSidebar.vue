<script setup lang="ts">
import { computed, ref } from 'vue'
import Add from '@carbon/icons-vue/es/add/20'
import Chip from '@carbon/icons-vue/es/chip/20'
import Close from '@carbon/icons-vue/es/close/20'
import Search from '@carbon/icons-vue/es/search/16'
import ConversationListItem from './ConversationListItem.vue'
import ThemeToggle from './ThemeToggle.vue'
import type { ConversationPreview } from '../types/api'
import type { Theme } from '../services/theme.service'

const props = defineProps<{
  conversations: ConversationPreview[]
  conversationCount: number
  activeConversationId: string | null
  isLoading: boolean
  disabled: boolean
  error: string | null
  theme: Theme
  /** Admin view: no new/rename/delete. */
  readonly?: boolean
  /** Admin view: searching is done by the server (emits `search`); `conversations` are already the results. */
  remoteSearch?: boolean
  searchPlaceholder?: string
  /** Admin view: conversation to flash and scroll to; flashes again whenever `flashKey` changes. */
  flashId?: string | null
  flashKey?: number
}>()

const emit = defineEmits<{
  newConversation: []
  select: [id: string]
  rename: [id: string, title: string]
  delete: [id: string]
  changeTheme: [theme: Theme]
  close: []
  search: [query: string]
}>()

const search = ref('')

// User view: client-side filter over what the sidebar already shows (title + newest message).
const filtered = computed(() => {
  const query = search.value.trim().toLowerCase()
  if (props.remoteSearch || !query) return props.conversations
  return props.conversations.filter((c) => `${c.title} ${c.lastMessage ?? ''}`.toLowerCase().includes(query))
})

function onSearchInput() {
  if (props.remoteSearch) emit('search', search.value)
}
</script>

<template>
  <aside class="sidebar" aria-label="Conversations">
    <header class="brand">
      <span class="cpu-tile"><Chip /></span>
      <span class="name">AI Chat</span>
      <span v-if="readonly" class="admin-tag">Admin</span>
      <button
        v-else
        class="btn icon-only add"
        type="button"
        aria-label="New conversation"
        :disabled="disabled"
        @click="emit('newConversation')"
      >
        <Add />
      </button>
      <button class="btn icon-only mobile-only" type="button" aria-label="Close sidebar" @click="emit('close')">
        <Close />
      </button>
    </header>

    <label class="search">
      <Search />
      <input
        v-model="search"
        type="search"
        :placeholder="searchPlaceholder ?? 'Search conversations...'"
        :title="searchPlaceholder ?? 'Search conversations'"
        aria-label="Search conversations"
        @input="onSearchInput"
      />
    </label>

    <p class="count">
      {{ conversationCount }} conversation{{ conversationCount === 1 ? '' : 's' }}{{ remoteSearch && search.trim() ? ' found' : '' }}
    </p>

    <p v-if="error" class="note error">{{ error }}</p>
    <p v-if="isLoading && filtered.length === 0" class="note">Loading…</p>
    <p v-else-if="filtered.length === 0 && search.trim()" class="note">No conversations found.</p>
    <p v-else-if="filtered.length === 0" class="note">No conversations yet.</p>

    <ul class="list">
      <ConversationListItem
        v-for="conversation in filtered"
        :key="conversation.id"
        :conversation="conversation"
        :active="conversation.id === activeConversationId"
        :disabled="disabled"
        :readonly="readonly"
        :flash="conversation.id === flashId"
        :flash-key="flashKey"
        @select="(id) => emit('select', id)"
        @rename="(id, title) => emit('rename', id, title)"
        @delete="(id) => emit('delete', id)"
      />
    </ul>

    <footer class="footer">
      <ThemeToggle :theme="theme" @change="(value) => emit('changeTheme', value)" />
    </footer>
  </aside>
</template>

<style scoped>
.sidebar {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;
  background: var(--panel);
  border-right: 1px solid var(--border);
}
.brand {
  display: flex;
  align-items: center;
  gap: 10px;
  height: 56px;
  flex-shrink: 0;
  padding: 0 12px 0 14px;
}
.name {
  font-size: 15px;
  font-weight: 700;
}
.add {
  margin-left: auto;
  background: var(--surface-hover);
}
.admin-tag {
  margin-left: auto;
  padding: 1px 8px;
  border-radius: 10px;
  background: var(--chip);
  color: var(--chip-text);
  font-size: 10px;
  font-weight: 700;
  letter-spacing: 0.8px;
  text-transform: uppercase;
}
.search {
  display: flex;
  align-items: center;
  gap: 8px;
  height: 34px;
  margin: 4px 10px 14px;
  padding: 0 10px;
  border: 1px solid var(--border);
  border-radius: 7px;
  background: var(--input);
  color: var(--text-muted);
}
.search:focus-within {
  border-color: var(--accent-border);
}
.search input {
  flex: 1;
  min-width: 0;
  border: none;
  background: transparent;
  font-size: 13px;
  outline: none;
}
.count {
  margin: 0 0 8px;
  padding: 0 14px;
  font-size: 10px;
  font-weight: 600;
  letter-spacing: 1.2px;
  text-transform: uppercase;
  color: var(--text-muted);
}
.note {
  margin: 4px 14px;
  font-size: 13px;
  color: var(--text-muted);
}
.error {
  color: var(--danger);
}
.list {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  list-style: none;
  margin: 0;
  padding: 0 8px 8px;
}
.footer {
  display: flex;
  align-items: center;
  height: 56px;
  flex-shrink: 0;
  padding: 0 12px;
  border-top: 1px solid var(--border);
}
.mobile-only {
  display: none;
}
@media (max-width: 720px) {
  .mobile-only {
    display: inline-flex;
  }
  .add {
    margin-left: auto;
  }
}
</style>
