<script setup lang="ts">
import { computed } from 'vue'
import { renderMarkdown } from '../services/markdown'
const props = defineProps<{ text: string }>()
const rendered = computed(() => renderMarkdown(props.text))
</script>

<template>
  <!-- Only sanitized, locally rendered Markdown reaches v-html. -->
  <div class="markdown-body" v-html="rendered" />
</template>

<style scoped>
.markdown-body { white-space: normal; overflow-wrap: anywhere; line-height: 1.6; min-width: 0; }
.markdown-body :deep(> :first-child) { margin-top: 0; }
.markdown-body :deep(> :last-child) { margin-bottom: 0; }
.markdown-body :deep(p) { margin: 0 0 .8em; }
.markdown-body :deep(h1), .markdown-body :deep(h2), .markdown-body :deep(h3),
.markdown-body :deep(h4), .markdown-body :deep(h5), .markdown-body :deep(h6) { margin: 1.1em 0 .5em; font-weight: 700; line-height: 1.3; }
.markdown-body :deep(h1) { font-size: 1.65em; }
.markdown-body :deep(h2) { font-size: 1.4em; }
.markdown-body :deep(h3) { font-size: 1.2em; }
.markdown-body :deep(h4), .markdown-body :deep(h5), .markdown-body :deep(h6) { font-size: 1em; }
.markdown-body :deep(ul), .markdown-body :deep(ol) { margin: .5em 0 .9em; padding-left: 1.6em; }
.markdown-body :deep(li) { margin: .25em 0; }
.markdown-body :deep(li > p) { margin: .3em 0; }
.markdown-body :deep(blockquote) { margin: .8em 0; padding: .2em 1em; border-left: 3px solid var(--chip-text); color: var(--text-muted); }
.markdown-body :deep(pre) { max-width: 100%; overflow-x: auto; padding: 12px; border: 1px solid var(--border); border-radius: 8px; background: var(--background); white-space: pre; font-size: .85em; }
.markdown-body :deep(code) { font-family: ui-monospace, SFMono-Regular, Consolas, monospace; font-size: .88em; padding: .1em .3em; border-radius: 4px; background: var(--surface-hover); }
.markdown-body :deep(pre code) { padding: 0; background: transparent; font-size: inherit; }
.markdown-body :deep(a) { color: var(--chip-text); text-decoration: underline; text-underline-offset: 2px; }
.markdown-body :deep(table) { display: block; overflow-x: auto; max-width: 100%; border-collapse: collapse; margin: .8em 0; }
.markdown-body :deep(th), .markdown-body :deep(td) { padding: .4em .7em; border: 1px solid var(--border); text-align: left; }
.markdown-body :deep(th) { background: var(--surface-hover); font-weight: 600; }
.markdown-body :deep(hr) { border: 0; border-top: 1px solid var(--border); margin: 1em 0; }
.markdown-body :deep(.markdown-image) { color: var(--text-muted); }
</style>
