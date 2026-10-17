<script setup lang="ts">
import type { Theme } from '../services/theme.service'

defineProps<{ theme: Theme }>()
const emit = defineEmits<{ toggle: [] }>()
</script>

<template>
  <button
    class="theme-toggle"
    type="button"
    :aria-label="theme === 'dark' ? 'Switch to light theme' : 'Switch to dark theme'"
    @click="emit('toggle')"
  >
    {{ theme === 'dark' ? '☀︎ Light' : '☾ Dark' }}
  </button>
</template>

<style scoped>
.theme-toggle {
  width: 100%;
}
</style>


