<script setup lang="ts">
import ConversationView from './views/ConversationView.vue'
import AdminView from './views/AdminView.vue'

// Two pages, no router needed: /admin (or /#/admin, which works on any static host) is the admin page.
const isAdmin = location.pathname.replace(/\/+$/, '') === '/admin' || location.hash.startsWith('#/admin')
</script>

<template>
  <AdminView v-if="isAdmin" />
  <ConversationView v-else />
</template>
