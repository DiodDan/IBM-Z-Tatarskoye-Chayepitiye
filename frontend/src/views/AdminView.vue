<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import SidePanelOpen from '@carbon/icons-vue/es/side-panel--open/16'
import ConversationSidebar from '../components/ConversationSidebar.vue'
import ConversationPanel from '../components/ConversationPanel.vue'
import ResponsesSidebar from '../components/ResponsesSidebar.vue'
import { useAdminConversations } from '../composables/useAdminConversations'
import { useTheme } from '../composables/useTheme'

// Read-only admin page: every user's conversations plus stored response and trajectory model scores. Opened at /admin (or /#/admin).
const admin = useAdminConversations()
const { theme, setTheme } = useTheme()
const responsesOpen = ref(true)

onMounted(() => {
  document.title = 'AI Chat — Admin'
  admin.loadConversations()
  admin.loadResponses()
})

const ownerLabel = computed(() => {
  const id = admin.activeConversation.value?.userId
  return id ? `User ${id.slice(0, 8)}` : null
})

// The left-panel ring marks the conversation the highlighted response comes from.
const flashId = computed(() => (admin.highlightedMessageId.value ? admin.activeConversationId.value : null))

// Admins can't send; the panel requires a handler, so this one always refuses.
const refuseSend = async () => false
</script>

<template>
  <div class="layout" :class="{ 'without-responses': !responsesOpen }">
    <ConversationSidebar
      :conversations="admin.conversations.value"
      :conversation-count="admin.conversationCount.value"
      :active-conversation-id="admin.activeConversationId.value"
      :is-loading="admin.isLoadingList.value"
      :disabled="false"
      :error="admin.listError.value"
      :theme="theme"
      readonly
      remote-search
      search-placeholder="Search or paste Response №…"
      :flash-id="flashId"
      :flash-key="admin.highlightPulse.value"
      @select="(id) => admin.openConversation(id)"
      @search="admin.search"
      @change-theme="setTheme"
    />

    <ConversationPanel
      :conversation="admin.activeConversation.value"
      :messages="admin.messages.value"
      :is-loading-history="admin.isLoadingHistory.value"
      :is-sending="false"
      :error="admin.conversationError.value"
      :send-error="null"
      :on-send="refuseSend"
      :owner-label="ownerLabel"
      :highlight-id="admin.highlightedMessageId.value"
      :highlight-key="admin.highlightPulse.value"
      readonly
      show-risk
    >
      <template #actions>
        <button
          v-if="!responsesOpen"
          class="btn icon-only"
          type="button"
          aria-label="Show responses panel"
          title="Show responses panel"
          @click="responsesOpen = true"
        >
          <SidePanelOpen />
        </button>
      </template>
    </ConversationPanel>

    <ResponsesSidebar
      v-if="responsesOpen"
      :responses="admin.responses.value"
      :risk-threshold="admin.riskThreshold.value"
      :sort="admin.responseSort.value"
      :is-loading="admin.isLoadingResponses.value"
      :error="admin.responsesError.value"
      :active-message-id="admin.highlightedMessageId.value"
      @close="responsesOpen = false"
      @refresh="admin.refresh"
      @change-sort="admin.loadResponses"
      @show="admin.showResponse"
      @jump="(conversationId, messageId) => admin.openConversation(conversationId, messageId)"
    />
  </div>
</template>

<style scoped>
/* Desktop only (team decision): three columns. */
.layout {
  display: grid;
  grid-template-columns: 240px minmax(0, 1fr) 360px;
  height: 100vh;
  height: 100dvh;
  min-width: 1000px;
}
.layout.without-responses {
  grid-template-columns: 240px minmax(0, 1fr);
}
@media (min-width: 1500px) {
  .layout {
    grid-template-columns: 260px minmax(0, 1fr) 400px;
  }
  .layout.without-responses {
    grid-template-columns: 260px minmax(0, 1fr);
  }
}
</style>
