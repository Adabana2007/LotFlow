<script setup lang="ts">
import { Inbox } from 'lucide-vue-next'

withDefaults(
  defineProps<{
    title: string
    description: string
    actionText?: string
  }>(),
  { actionText: '' },
)

defineEmits<{ action: [] }>()
</script>

<template>
  <div class="empty-state">
    <span class="empty-state__icon"><Inbox :size="22" /></span>
    <b>{{ title }}</b>
    <p>{{ description }}</p>
    <button v-if="actionText" class="button button--outline" type="button" @click="$emit('action')">
      {{ actionText }}
    </button>
  </div>
</template>
