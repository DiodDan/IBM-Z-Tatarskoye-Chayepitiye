<script setup lang="ts">
import { ref, watch } from 'vue'
import AddAlt from '@carbon/icons-vue/es/add--alt/16'
import Chat from '@carbon/icons-vue/es/chat/16'
import Chip from '@carbon/icons-vue/es/chip/20'
import Menu from '@carbon/icons-vue/es/menu/20'
import Settings from '@carbon/icons-vue/es/settings/16'
import Share from '@carbon/icons-vue/es/share/16'
import MessageList from './MessageList.vue'
import MessageComposer from './MessageComposer.vue'
import type { ConversationPreview, Message } from '../types/api'

const props = defineProps<{
  conversation: ConversationPreview | null
  messages: Message[]
  isLoadingHistory: boolean
  isSending: boolean
  error: string | null
  sendError: string | null
  onSend: (content: string) => Promise<boolean>
  /** Admin view: no New Chat, composer locked, suggestions not clickable. */
  readonly?: boolean
  /** Admin view: show the user-prompt risk under each reply. */
  showRisk?: boolean
  /** Admin view: extra header badge, e.g. the conversation owner's anonymous id. */
  ownerLabel?: string | null
}>()

const emit = defineEmits<{
  newConversation: []
  openSidebar: []
}>()

const notice = ref<string | null>(null)
const composer = ref<InstanceType<typeof MessageComposer> | null>(null)

watch(
  () => props.conversation?.id,
  () => (notice.value = null),
)

/** "Share" = copy the whole conversation as plain text. */
async function share() {
  if (props.messages.length === 0) {
    notice.value = 'Nothing to share yet.'
    return
  }
  const lines = props.messages.map((m) => `${m.role === 'user' ? 'You' : 'AI Assistant'}: ${m.content}`)
  const text = `${props.conversation?.title ?? 'Conversation'}\n\n${lines.join('\n\n')}`
  try {
    await navigator.clipboard.writeText(text)
    notice.value = 'Conversation copied to clipboard.'
  } catch {
    notice.value = 'Clipboard unavailable in this browser.'
  }
}

function focusComposer() {
  composer.value?.focus()
}

defineExpose({ focusComposer })
</script>

<template>
  <section class="panel">
    <header class="topbar">
      <div class="title-group">
        <button class="btn icon-only mobile-only" type="button" aria-label="Open conversations" @click="emit('openSidebar')">
          <Menu />
        </button>
        <span class="chat-tile"><Chat /></span>
        <h1>{{ conversation?.title ?? 'New conversation' }}</h1>
        <span class="badge">AI Assistant</span>
        <span v-if="ownerLabel" class="badge" :title="ownerLabel">{{ ownerLabel }}</span>
      </div>

      <div class="actions">
        <button class="btn share" type="button" aria-label="Share" @click="share">
          <Share />
          <span>Share</span>
        </button>
        <button v-if="!readonly" class="btn primary" type="button" @click="emit('newConversation')">
          <AddAlt />
          New Chat
        </button>
        <slot name="actions" />
        <!-- Not available yet: visible but disabled. -->
        <button class="btn icon-only" type="button" disabled aria-label="Settings (coming soon)" title="Coming soon">
          <Settings />
        </button>
      </div>
    </header>

    <p v-if="error" class="state error">{{ error }}</p>
    <p v-else-if="isLoadingHistory" class="state">Loading conversation…</p>
    <div v-else-if="messages.length === 0 && !isSending" class="state empty">
      <span class="cpu-tile big"><Chip /></span>
      <template v-if="readonly">
        <h2>Nothing to review yet</h2>
        <p>Select a conversation on the left. Users' chats appear here as soon as they send a message.</p>
      </template>
      <template v-else>
        <h2>How can I help you today?</h2>
        <p>Ask a question, explore an idea, or work through a problem.</p>
      </template>
    </div>
    <MessageList
      v-else
      :messages="messages"
      :is-waiting-for-reply="isSending"
      :readonly="readonly"
      :show-risk="showRisk"
      @suggest="onSend"
    />

    <MessageComposer
      ref="composer"
      :disabled="isSending || isLoadingHistory"
      :error="sendError"
      :notice="notice"
      :on-send="onSend"
      :readonly="readonly"
    />
  </section>
</template>

<style scoped>
.panel {
  --gutter: 48px;
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;
  min-width: 0;
  background: var(--background);
}
.topbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  height: 56px;
  flex-shrink: 0;
  padding: 0 20px;
  background: var(--panel);
  border-bottom: 1px solid var(--border);
}
.title-group,
.actions {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
}
.actions {
  flex-shrink: 0;
  gap: 8px;
}
.chat-tile {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 26px;
  height: 26px;
  flex-shrink: 0;
  border: 1px solid var(--border);
  border-radius: 7px;
}
h1 {
  margin: 0;
  font-size: 14px;
  font-weight: 700;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}
.badge {
  flex-shrink: 0;
  padding: 1px 8px;
  border: 1px solid var(--border);
  border-radius: 10px;
  background: var(--surface-hover);
  font-size: 10px;
  font-weight: 600;
  color: var(--text-muted);
}
.state {
  flex: 1;
  margin: 0;
  padding: 24px var(--gutter);
  color: var(--text-muted);
}
.error {
  color: var(--danger);
}
.empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  text-align: center;
}
.empty h2 {
  margin: 16px 0 4px;
  font-size: 24px;
  font-weight: 600;
  color: var(--text);
}
.empty p {
  margin: 0;
}
.big {
  width: 48px;
  height: 48px;
  border-radius: 12px;
}
.mobile-only {
  display: none;
}

@media (max-width: 1000px) {
  .panel {
    --gutter: 24px;
  }
  .badge {
    display: none;
  }
}
@media (max-width: 720px) {
  .panel {
    --gutter: 14px;
  }
  .topbar {
    padding: 0 10px;
    gap: 6px;
  }
  .mobile-only {
    display: inline-flex;
  }
  .chat-tile {
    display: none;
  }
  .share {
    width: 32px;
    padding: 0;
  }
  .share span {
    display: none;
  }
  .actions {
    gap: 6px;
  }
}
</style>
