<script setup lang="ts">
import type { ArtworkKind } from '@/types/catalog'

withDefaults(
  defineProps<{
    kind: ArtworkKind
    tone?: string
    lotNo?: string
    large?: boolean
  }>(),
  { tone: 'tone-celadon', lotNo: '', large: false },
)
</script>

<template>
  <div class="product-artwork" :class="[tone, { 'product-artwork--large': large }]">
    <span v-if="lotNo" class="product-artwork__stamp">LOT {{ lotNo }}</span>
    <div v-if="kind === 'camera' || kind === 'film'" class="art-object camera-object">
      <i class="camera-top" /><i class="camera-lens" /><i class="camera-dial" /><i class="camera-flash" />
    </div>
    <div v-else-if="kind === 'console'" class="art-object console-object">
      <i class="console-screen" /><i class="console-stick" /><i class="console-button" />
    </div>
    <div v-else-if="kind === 'mug'" class="art-object mug-object"><i /></div>
    <div v-else-if="kind === 'guitar'" class="art-object guitar-object"><i class="guitar-hole" /></div>
    <div v-else-if="kind === 'cards'" class="art-object cards-object"><i /><b>01</b></div>
    <div v-else-if="kind === 'watch'" class="art-object watch-object"><i class="watch-face" /></div>
    <div v-else-if="kind === 'figurine'" class="art-object figurine-object"><i /></div>
    <div v-else-if="kind === 'speaker'" class="art-object speaker-object"><i /><b /></div>
    <div v-else class="art-object vase-object"><i /></div>
  </div>
</template>
