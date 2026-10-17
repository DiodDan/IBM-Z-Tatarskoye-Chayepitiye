<script setup lang="ts">
import Asleep from '@carbon/icons-vue/es/asleep/16'
import Light from '@carbon/icons-vue/es/light/16'
import type { Theme } from '../services/theme.service'

defineProps<{ theme: Theme }>()
const emit = defineEmits<{ change: [theme: Theme] }>()
</script>

<template>
  <div class="theme-switch" role="group" aria-label="Color theme">
    <button type="button" :aria-pressed="theme === 'dark'" @click="emit('change', 'dark')">
      <Asleep />
      Dark
    </button>
    <button type="button" :aria-pressed="theme === 'light'" @click="emit('change', 'light')">
      <Light />
      Light
    </button>
  </div>
</template>

<style scoped>
.theme-switch {
  display: inline-flex;
  gap: 2px;
  padding: 3px;
  border: 1px solid var(--border);
  border-radius: 7px;
  background: var(--background);
}
button {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  height: 26px;
  padding: 0 9px;
  border-radius: 5px;
  font-size: 13px;
  color: var(--text-muted);
}
button:hover {
  color: var(--text);
}
button[aria-pressed='true'] {
  background: var(--accent);
  color: var(--accent-text);
}
</style>
