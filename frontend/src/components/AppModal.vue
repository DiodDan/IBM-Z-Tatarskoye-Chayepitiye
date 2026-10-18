<script setup lang="ts">
import { nextTick, onMounted, onUnmounted, ref } from 'vue'
import Close from '@carbon/icons-vue/es/close/20'

defineProps<{ title: string }>()
const emit = defineEmits<{ close: [] }>()

const dialog = ref<HTMLElement | null>(null)
let previousFocus: HTMLElement | null = null

function onKeydown(event: KeyboardEvent) {
  if (event.key === 'Escape') emit('close')
}

onMounted(async () => {
  previousFocus = document.activeElement as HTMLElement | null
  window.addEventListener('keydown', onKeydown)
  await nextTick()
  dialog.value?.focus()
})

onUnmounted(() => {
  window.removeEventListener('keydown', onKeydown)
  previousFocus?.focus?.()
})
</script>

<template>
  <div class="backdrop" @click.self="emit('close')">
    <section ref="dialog" class="modal" role="dialog" aria-modal="true" aria-labelledby="modal-title" tabindex="-1">
      <header class="head">
        <h2 id="modal-title">{{ title }}</h2>
        <button class="btn icon-only" type="button" aria-label="Close" @click="emit('close')"><Close /></button>
      </header>
      <slot />
    </section>
  </div>
</template>

<style scoped>
.backdrop {
  position: fixed;
  inset: 0;
  z-index: 20;
  display: grid;
  place-items: center;
  padding: 24px;
  background: rgb(0 0 0 / 0.45);
  backdrop-filter: blur(4px);
}
.modal {
  width: min(100%, 620px);
  max-height: calc(100dvh - 48px);
  overflow-y: auto;
  padding: 22px 24px 24px;
  border: 1px solid var(--border);
  border-radius: 14px;
  background: var(--surface);
  box-shadow: 0 20px 70px rgb(0 0 0 / 0.3);
  outline: none;
}
.head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 16px;
}
h2 {
  margin: 0;
  font-size: 20px;
  font-weight: 600;
}
</style>
