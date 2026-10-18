<script setup lang="ts">
import { computed } from 'vue'
import type { ModelAssessment } from '../types/model'
import { categoryLabels, formatScore } from '../types/model'
const props = defineProps<{ assessment?: ModelAssessment | null; expanded?: boolean }>()
const analysis = computed(() => props.assessment?.analysis)
const trajectory = computed(() => analysis.value?.trajectory)
const scores = computed(() => analysis.value?.atomic)
const categories = computed(() => Object.entries(scores.value?.categories ?? {}).sort((a, b) => b[1] - a[1]))
const status = computed(() => analysis.value?.status ?? 'pending')
</script>

<template>
  <section class="model-score" aria-label="Assistant model scores">
    <header><strong>Response model</strong><span class="badge">{{ status }}</span></header>
    <p v-if="status !== 'completed'" role="status">{{ analysis?.message ?? 'This response has not been analysed for the current model.' }}</p>
    <template v-else-if="scores">
      <div class="metric"><span>Response score</span><strong>{{ formatScore(scores.overall) }} / 1</strong></div>
      <meter min="0" max="1" :value="scores.overall" aria-label="Response model score" />
      <p class="note">{{ scores.calibrationApplied ? 'Calibrated model score' : 'Uncalibrated sigmoid score — not a validated probability' }}</p>
      <div v-if="trajectory" class="trajectory">
        <div class="metric"><span>Latest trajectory score</span><strong>{{ formatScore(trajectory.scores?.overall) }}</strong></div>
        <p v-if="!trajectory.scores" class="note">Single-response trajectory; no combined score yet.</p>
        <p class="note">Member {{ assessment?.membership?.ordinal ?? '—' }} of {{ trajectory.responseCount }} responses · {{ assessment?.membership?.status ?? '—' }}</p>
        <div class="metric"><span>Association score</span><span>{{ formatScore(trajectory.associationScore) }}</span></div>
        <p v-if="trajectory.associationScore == null" class="note">First member; no association decision.</p>
        <p v-else class="note">Link threshold {{ formatScore(assessment?.membership?.linkThreshold) }} · score for this member's join decision</p>
      </div>
      <details :open="expanded || undefined">
        <summary>Categories &amp; trajectory details</summary>
        <table>
          <caption>Response and latest trajectory scores</caption>
          <thead><tr><th scope="col">Category</th><th scope="col">Response</th><th scope="col">Trajectory</th></tr></thead>
          <tbody><tr v-for="[key, value] in categories" :key="key">
            <th scope="row">{{ categoryLabels[key] ?? key }}</th><td>{{ formatScore(value) }}</td><td>{{ formatScore(trajectory?.scores?.categories[key]) }}</td>
          </tr></tbody>
        </table>
        <p v-if="trajectory" class="note">Trajectory scores are the current state, including later responses; they are not historical scores at this member.</p>
        <dl>
          <dt>Trajectory</dt><dd>{{ trajectory?.id ?? '—' }}</dd>
          <dt>Last position</dt><dd>{{ trajectory?.lastPosition ?? '—' }}</dd>
          <dt>Calibration</dt><dd>{{ scores.calibrationId }}</dd>
          <dt>Model</dt><dd>{{ analysis?.modelFingerprint ?? '—' }}</dd>
          <template v-if="assessment?.membership">
            <dt>Updated</dt><dd>{{ new Date(assessment.membership.updatedAt).toLocaleString() }}</dd>
            <dt>Expires</dt><dd>{{ new Date(assessment.membership.expiresAt).toLocaleString() }}</dd>
            <dt>Association logit</dt><dd>{{ formatScore(assessment.membership.associationLogit) }}</dd>
          </template>
          <template v-if="analysis?.coverage">
            <dt>Coverage</dt><dd>{{ analysis.coverage.processedTokens }}/{{ analysis.coverage.totalTokens }} tokens · {{ analysis.coverage.windowCount }} windows · {{ analysis.coverage.status }}</dd>
          </template>
        </dl>
        <template v-if="analysis?.windowPeak">
          <h4>Window evidence</h4>
          <p class="note">Uncalibrated maximum per window; separate from the full response score. Token ranges use an exclusive end.</p>
          <ul><li v-for="(range, head) in analysis.windowPeak.peakTokenRanges" :key="head">
            {{ categoryLabels[head] ?? head }}: {{ formatScore(head === 'overall' ? analysis.windowPeak.scores.overall : analysis.windowPeak.scores.categories[head]) }} · tokens {{ range.start }}–{{ range.end }}
          </li></ul>
        </template>
      </details>
    </template>
    <p v-if="analysis?.errorCode && status === 'failed'" class="note">{{ analysis.errorCode }}</p>
  </section>
</template>

<style scoped>
.model-score { font-size: 12px; line-height: 1.5; overflow-wrap: anywhere; }
header,.metric { display: flex; justify-content: space-between; gap: 10px; align-items: baseline; }
header { margin-bottom: 6px; }
.badge { font-size: 10px; border: 1px solid var(--border); border-radius: 10px; padding: 1px 7px; text-transform: capitalize; }
meter { width: 100%; height: 8px; accent-color: var(--chip-text); }
meter::-webkit-meter-optimum-value { background: var(--chip-text); }
.note { color: var(--text-muted); font-size: 11px; margin: 4px 0; }
.trajectory { margin: 8px 0; padding: 8px 0; border-block: 1px solid var(--border); }
summary { cursor: pointer; font-weight: 600; padding: 4px 0; }
table { width: 100%; border-collapse: collapse; font-size: 11px; margin-top: 8px; }
caption { text-align: left; color: var(--text-muted); }
th,td { padding: 4px 3px; border-bottom: 1px solid var(--border); text-align: right; font-variant-numeric: tabular-nums; }
th:first-child { text-align: left; font-weight: 500; }
dl { display: grid; grid-template-columns: 80px minmax(0,1fr); gap: 4px 8px; font-size: 11px; }
dt { color: var(--text-muted); } dd { margin: 0; } ul { padding-left: 16px; font-size: 11px; } h4 { margin: 8px 0 0; }
</style>
