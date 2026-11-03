<script setup lang="ts">
import { RouterLink } from 'vue-router'
import { ArrowRight } from 'lucide-vue-next'
import ProductArtwork from './ProductArtwork.vue'
import StatusPill from './StatusPill.vue'
import CountdownText from './CountdownText.vue'
import { formatMoney } from '@/utils/format'
import type { MarketLot } from '@/types/catalog'

const props = withDefaults(
  defineProps<{
    lot: MarketLot
    compact?: boolean
  }>(),
  { compact: false },
)

const channelLabels = { auction: '拍卖', seckill: '秒杀', shop: '商城' }
const actionLabels = { auction: '去出价', seckill: '立即抢购', shop: '立即购买' }
const channelTone = { auction: 'auction', seckill: 'seckill', shop: 'shop' } as const

function detailLink(): string {
  return `/listings/${props.lot.id}?channel=${props.lot.channel}`
}
</script>

<template>
  <article class="lot-card" :class="{ 'lot-card--compact': compact }">
    <RouterLink :to="detailLink()" class="lot-card__visual">
      <img v-if="lot.coverUrl" :src="lot.coverUrl" :alt="lot.title" class="lot-card__photo" loading="lazy" />
      <ProductArtwork v-else :kind="lot.artwork" :tone="lot.tone" :lot-no="lot.lotNo" />
    </RouterLink>
    <div class="lot-card__body">
      <div class="lot-card__meta">
        <StatusPill :text="channelLabels[lot.channel]" :tone="channelTone[lot.channel]" />
        <CountdownText v-if="lot.endsAt" :deadline="lot.endsAt" />
      </div>
      <RouterLink :to="detailLink()" class="lot-card__title">{{ lot.title }}</RouterLink>
      <p>{{ lot.subtitle }}</p>
      <div class="lot-card__footer">
        <div>
          <span class="muted-note">
            {{ lot.channel === 'auction' ? (lot.bidCount == null ? '出价数据以拍卖详情为准' : `${lot.bidCount} 次出价`) : lot.channel === 'seckill' ? '仅 1 件 · 无买家保证金' : '单件一口价 · 无购物车' }}
          </span>
          <strong class="price-text">{{ formatMoney(lot.price) }}</strong>
        </div>
        <RouterLink :to="detailLink()" class="button button--small">
          {{ actionLabels[lot.channel] }}
          <ArrowRight :size="13" />
        </RouterLink>
      </div>
    </div>
  </article>
</template>
