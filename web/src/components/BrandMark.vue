<script setup lang="ts">
import { RouterLink } from 'vue-router'

withDefaults(
  defineProps<{
    light?: boolean
    compact?: boolean
  }>(),
  { light: false, compact: false },
)
</script>

<template>
  <RouterLink class="brand-mark" :class="{ 'brand-mark--light': light, 'brand-mark--compact': compact }" to="/">
    <span class="brand-mark__symbol">L</span>
    <span v-if="!compact" class="brand-mark__word">LOTFLOW</span>
  </RouterLink>
</template>
