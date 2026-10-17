<script setup lang="ts">
import { onMounted, ref } from 'vue'
import ConversationSidebar from '../components/ConversationSidebar.vue'
import ConversationPanel from '../components/ConversationPanel.vue'
import { useConversations } from '../composables/useConversations'
import { useTheme } from '../composables/useTheme'
import { ensureUserId } from '../services/identity.service'

const chat = useConversations()
const { theme, toggleTheme } = useTheme()

const initError = ref<string | null>(null)
const sidebarOpen = ref(false) // mobile drawer

onMounted(() => {
  try {
    ensureUserId()
  } catch (err) {
    initError.value = (err as Error).message
    return
  }
  chat.loadConversations()
})

function select(id: string) {
  sidebarOpen.value = false
  chat.selectConversation(id)
}

function newConversation() {
  sidebarOpen.value = false
  chat.startNewConversation()
}
</script>

<template>
  <div v-if="initError" class="init-error">{{ initError }}</div>

  <div v-else class="layout" :class="{ 'sidebar-open': sidebarOpen }">
    <button class="menu-btn" type="button" aria-label="Open conversations" @click="sidebarOpen = !sidebarOpen">
      ☰
    </button>

    <ConversationSidebar
      class="sidebar"
      :conversations="chat.conversations.value"
      :conversation-count="chat.conversationCount.value"
      :active-conversation-id="chat.activeConversationId.value"
      :is-loading="chat.isLoadingList.value"
      :disabled="chat.isSending.value || chat.isMutating.value"
      :error="chat.listError.value"
      :theme="theme"
      @new-conversation="newConversation"
      @select="select"
      @rename="chat.renameConversation"
      @delete="chat.deleteConversation"
      @toggle-theme="toggleTheme"
    />

    <ConversationPanel
      class="panel"
      :conversation="chat.activeConversation.value"
      :messages="chat.messages.value"
      :is-loading-history="chat.isLoadingHistory.value"
      :is-sending="chat.isSending.value"
      :error="chat.conversationError.value"
      :send-error="chat.sendError.value"
      :on-send="chat.sendMessage"
    />
  </div>
</template>

<style scoped>
.layout {
  display: grid;
  grid-template-columns: 280px 1fr;
  height: 100vh;
  height: 100dvh;
}
.menu-btn {
  display: none;
}
.init-error {
  padding: 24px;
  color: var(--danger);
}

/* Phones: sidebar becomes a slide-in drawer */
@media (max-width: 720px) {
  .layout {
    grid-template-columns: 1fr;
    position: relative;
  }
  .menu-btn {
    display: block;
    position: absolute;
    top: 8px;
    right: 8px;
    z-index: 3;
  }
  .sidebar {
    position: absolute;
    inset: 0 auto 0 0;
    width: 280px;
    z-index: 2;
    transform: translateX(-100%);
    transition: transform 0.2s;
  }
  .sidebar-open .sidebar {
    transform: translateX(0);
  }
}
</style>
