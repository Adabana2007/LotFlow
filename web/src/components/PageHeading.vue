<script setup lang="ts">
withDefaults(
  defineProps<{
    eyebrow?: string
    title: string
    description?: string
    compact?: boolean
  }>(),
  { eyebrow: '', description: '', compact: false },
)
</script>

<template>
  <div class="page-heading" :class="{ 'page-heading--compact': compact }">
    <div>
      <div v-if="eyebrow" class="eyebrow">{{ eyebrow }}</div>
      <h1>{{ title }}</h1>
      <p v-if="description">{{ description }}</p>
    </div>
    <div v-if="$slots.actions" class="page-heading__actions">
      <slot name="actions" />
    </div>
  </div>
</template>
