<script setup lang="ts">
import { nextTick, ref, watch } from 'vue'
import Attachment from '@carbon/icons-vue/es/attachment/16'
import Microphone from '@carbon/icons-vue/es/microphone/16'
import Send from '@carbon/icons-vue/es/send--alt/16'

const props = defineProps<{
  disabled: boolean
  error: string | null
  notice: string | null
  onSend: (content: string) => Promise<boolean>
}>()

const draft = ref('')
const textarea = ref<HTMLTextAreaElement | null>(null)

// Grow with the text, up to MAX_HEIGHT, then scroll.
const MAX_HEIGHT = 140
watch(draft, async () => {
  await nextTick()
  const el = textarea.value
  if (!el) return
  el.style.height = '0px'
  el.style.height = `${Math.min(el.scrollHeight, MAX_HEIGHT)}px`
})

async function submit() {
  if (props.disabled || !draft.value.trim()) return
  const sent = await props.onSend(draft.value)
  if (sent) draft.value = ''
}

function onKeydown(event: KeyboardEvent) {
  if (event.key === 'Enter' && !event.shiftKey && !event.isComposing) {
    event.preventDefault()
    submit()
  }
}

defineExpose({ focus: () => textarea.value?.focus() })
</script>

<template>
  <div class="area">
    <div class="column">
      <p v-if="error" class="message error" role="alert">{{ error }}</p>
      <p v-else-if="notice" class="message" role="status">{{ notice }}</p>

      <form class="composer" @submit.prevent="submit">
        <!-- Not available yet: visible but disabled. -->
        <button type="button" class="tool" disabled aria-label="Attach file (coming soon)" title="Coming soon">
          <Attachment />
        </button>
        <textarea
          ref="textarea"
          v-model="draft"
          rows="1"
          maxlength="20000"
          placeholder="Type a message… · Enter to send · Shift+Enter for new line"
          aria-label="Message"
          @keydown="onKeydown"
        />
        <button type="button" class="tool" disabled aria-label="Voice input (coming soon)" title="Coming soon">
          <Microphone />
        </button>
        <button class="send" type="submit" :disabled="disabled || !draft.trim()" aria-label="Send message">
          <Send />
        </button>
      </form>
    </div>
  </div>
</template>

<style scoped>
.area {
  flex-shrink: 0;
  padding: 12px var(--gutter) 14px;
  background: var(--panel);
  border-top: 1px solid var(--border);
}
.column {
  max-width: 900px;
  margin: 0 auto;
}
.message {
  margin: 0 0 8px;
  font-size: 13px;
  color: var(--text-muted);
}
.error {
  color: var(--danger);
}
.composer {
  display: flex;
  align-items: flex-end;
  gap: 8px;
  min-height: 40px;
  padding: 5px 5px 5px 12px;
  border: 1px solid var(--border);
  border-radius: 10px;
  background: var(--input);
}
.composer:focus-within {
  border-color: var(--accent-border);
}
textarea {
  flex: 1;
  min-width: 0;
  height: 28px;
  max-height: 140px;
  padding: 4px 0;
  border: none;
  background: transparent;
  resize: none;
  font-size: 14px;
  line-height: 20px;
  outline: none;
}
.tool {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  border-radius: 6px;
  color: var(--text-muted);
}
.send {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 30px;
  height: 28px;
  border-radius: 7px;
  background: var(--accent);
  color: var(--accent-text);
}
.send:hover:not(:disabled) {
  background: var(--accent-hover);
}
</style>
