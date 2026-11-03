<script setup lang="ts">
import { computed } from 'vue'

const props = withDefaults(
  defineProps<{
    text: string
    tone?: 'neutral' | 'auction' | 'seckill' | 'shop' | 'success' | 'warning' | 'danger' | 'dark'
  }>(),
  { tone: 'neutral' },
)

const toneClass = computed(() => `status-pill--${props.tone}`)
</script>

<template>
  <span class="status-pill" :class="toneClass">{{ text }}</span>
</template>
