<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'

const props = withDefaults(
  defineProps<{
    deadline?: string | null
    prefix?: string
    large?: boolean
  }>(),
  { deadline: null, prefix: '', large: false },
)

const now = ref(Date.now())
let timer: number | undefined

const milliseconds = computed(() => {
  if (!props.deadline) return 0
  return Math.max(0, new Date(props.deadline).getTime() - now.value)
})

const display = computed(() => {
  const totalSeconds = Math.floor(milliseconds.value / 1000)
  const hours = Math.floor(totalSeconds / 3600)
  const minutes = Math.floor((totalSeconds % 3600) / 60)
  const seconds = totalSeconds % 60
  const pad = (value: number) => String(value).padStart(2, '0')
  if (hours >= 24) {
    const days = Math.floor(hours / 24)
    return `${days} 天 ${pad(hours % 24)}:${pad(minutes)}:${pad(seconds)}`
  }
  return `${pad(hours)}:${pad(minutes)}:${pad(seconds)}`
})

onMounted(() => {
  timer = window.setInterval(() => {
    now.value = Date.now()
  }, 1000)
})

onBeforeUnmount(() => {
  if (timer) window.clearInterval(timer)
})
</script>

<template>
  <span class="countdown" :class="{ 'countdown--large': large }">
    <span v-if="prefix" class="countdown__prefix">{{ prefix }}</span>
    {{ display }}
  </span>
</template>
