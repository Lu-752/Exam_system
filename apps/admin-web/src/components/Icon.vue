<template>
  <svg class="ic" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"
       stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
    <path v-for="(d, i) in paths" :key="i" :d="d" />
  </svg>
</template>

<script setup lang="ts">
import { computed } from 'vue'

const props = defineProps<{ name: string }>()

const MAP: Record<string, string[]> = {
  home: ['M3.5 10.6 12 3.6l8.5 7', 'M5.6 9.4V20.4h12.8V9.4', 'M10 20.4v-5.6h4v5.6'],
  users: ['M9 4.6a3.4 3.4 0 1 1 0 6.8 3.4 3.4 0 0 1 0-6.8', 'M3.2 20c0-3.4 2.6-5.6 5.8-5.6s5.8 2.2 5.8 5.6',
          'M17.2 5.9a2.7 2.7 0 1 1 0 5.4 2.7 2.7 0 0 1 0-5.4', 'M16.4 14.6c2.7.3 4.6 2.4 4.6 5'],
  doc: ['M5 5h14v16H5z', 'M9 5V3.4h6V5', 'M9 11.5h6', 'M9 15.5h4'],
  chart: ['M4 20V9', 'M10 20V4', 'M16 20v-7', 'M20 20H3'],
  setting: ['M12 15.1a3.1 3.1 0 1 0 0-6.2 3.1 3.1 0 0 0 0 6.2',
            'M12 2.8v2.6M12 18.6v2.6M2.8 12h2.6M18.6 12h2.6M5.6 5.6l1.9 1.9M16.5 16.5l1.9 1.9M18.4 5.6l-1.9 1.9M7.5 16.5l-1.9 1.9'],
  bell: ['M6.2 9.4a5.8 5.8 0 0 1 11.6 0c0 4.8 2 6.4 2 6.4H4.2s2-1.6 2-6.4z', 'M10.1 19a2.2 2.2 0 0 0 3.8 0'],
  chevron: ['M6 9.5l6 6 6-6'],
  search: ['M11 4.2a6.8 6.8 0 1 1 0 13.6 6.8 6.8 0 0 1 0-13.6', 'M16.2 16.2 21 21'],
  back: ['M14.5 6l-6 6 6 6'],
  monitor: ['M3 4.2h18v12.4H3z', 'M9 20.4h6M12 16.6v3.8']
}

const paths = computed(() => MAP[props.name] ?? MAP.doc)
</script>

<style scoped>
.ic { width: 15px; height: 15px; flex: 0 0 auto; }
</style>
