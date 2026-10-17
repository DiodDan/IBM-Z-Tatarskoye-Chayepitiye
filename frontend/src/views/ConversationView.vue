<script setup lang="ts">
import { nextTick, onMounted, ref } from 'vue'
import ConversationSidebar from '../components/ConversationSidebar.vue'
import ConversationPanel from '../components/ConversationPanel.vue'
import { useConversations } from '../composables/useConversations'
import { useTheme } from '../composables/useTheme'
import { ensureUserId } from '../services/identity.service'

const chat = useConversations()
const { theme, setTheme } = useTheme()

const initError = ref<string | null>(null)
const sidebarOpen = ref(false) // mobile drawer
const panel = ref<InstanceType<typeof ConversationPanel> | null>(null)

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

async function newConversation() {
  sidebarOpen.value = false
  chat.startNewConversation()
  await nextTick()
  panel.value?.focusComposer()
}
</script>

<template>
  <div v-if="initError" class="init-error">{{ initError }}</div>

  <div v-else class="layout" :class="{ 'sidebar-open': sidebarOpen }" @keydown.esc="sidebarOpen = false">
    <button v-if="sidebarOpen" class="backdrop" type="button" aria-label="Close conversations" @click="sidebarOpen = false" />

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
      @change-theme="setTheme"
      @close="sidebarOpen = false"
    />

    <ConversationPanel
      ref="panel"
      class="panel"
      :conversation="chat.activeConversation.value"
      :messages="chat.messages.value"
      :is-loading-history="chat.isLoadingHistory.value"
      :is-sending="chat.isSending.value"
      :error="chat.conversationError.value"
      :send-error="chat.sendError.value"
      :on-send="chat.sendMessage"
      @new-conversation="newConversation"
      @open-sidebar="sidebarOpen = true"
    />
  </div>
</template>

<style scoped>
.layout {
  display: grid;
  grid-template-columns: 240px 1fr;
  height: 100vh;
  height: 100dvh;
}
.backdrop {
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
  .sidebar {
    position: absolute;
    inset: 0 auto 0 0;
    width: min(280px, 85vw);
    z-index: 3;
    transform: translateX(-100%);
    transition: transform 0.2s;
  }
  .sidebar-open .sidebar {
    transform: translateX(0);
  }
  .backdrop {
    display: block;
    position: absolute;
    inset: 0;
    z-index: 2;
    background: rgb(0 0 0 / 0.5);
  }
}
</style>
