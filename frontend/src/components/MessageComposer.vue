<script setup lang="ts">
import { ref } from 'vue'

const props = defineProps<{
  disabled: boolean
  error: string | null
  onSend: (content: string) => Promise<boolean>
}>()

const draft = ref('')

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
</script>

<template>
  <form class="composer" @submit.prevent="submit">
    <p v-if="error" class="error">{{ error }}</p>
    <div class="row">
      <textarea
        v-model="draft"
        rows="2"
        maxlength="20000"
        placeholder="Type a message… (Enter to send, Shift+Enter for new line)"
        aria-label="Message"
        @keydown="onKeydown"
      />
      <button class="primary" type="submit" :disabled="disabled || !draft.trim()">Send</button>
    </div>
  </form>
</template>

<style scoped>
.composer {
  padding: 12px 16px 16px;
  border-top: 1px solid var(--border);
}
.row {
  display: flex;
  gap: 8px;
  align-items: flex-end;
}
textarea {
  flex: 1;
  resize: none;
  font: inherit;
}
.error {
  color: var(--danger);
  margin: 0 0 8px;
  font-size: 14px;
}
</style>
