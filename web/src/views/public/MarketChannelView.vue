<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { PackageCheck, ShieldCheck, TimerReset } from 'lucide-vue-next'
import PageHeading from '@/components/PageHeading.vue'
import LotCard from '@/components/LotCard.vue'
import EmptyState from '@/components/EmptyState.vue'
import { apiRequest, errorMessage } from '@/api/client'
import type { CursorPage, ListingVO } from '@/types/api'
import type { MarketLot } from '@/types/catalog'
import { listingToMarketLot } from '@/utils/catalog'

const props = defineProps<{ channel: 'seckill' | 'shop' }>()
const isSeckill = computed(() => props.channel === 'seckill')
const loading = ref(true)
const error = ref('')
const lots = ref<MarketLot[]>([])
const title = computed(() => (isSeckill.value ? '流拍后的第二站' : '秒杀仍未售出，再慢一点遇见'))
const eyebrow = computed(() => (isSeckill.value ? '24H FLASH' : '30D FIXED PRICE SHOP'))
const description = computed(() =>
  isSeckill.value
    ? '每件商品只有 1 个库存单元，不收买家保证金；下单后锁定库存 15 分钟。'
    : '同一件商品的一口价交易，不做购物车、SKU、数量选择或多件合并购买。',
)

async function load(): Promise<void> {
  loading.value = true
  error.value = ''
  try {
    const page = await apiRequest<CursorPage<ListingVO>>('/listings?limit=50')
    const targetStatus = props.channel === 'seckill' ? 'SECKILL' : 'SHOP'
    lots.value = page.items
      .filter((listing) => listing.status === targetStatus)
      .map((listing, index) => listingToMarketLot(listing, null, index, props.channel))
  } catch (cause) {
    error.value = errorMessage(cause)
  } finally {
    loading.value = false
  }
}

onMounted(load)
watch(() => props.channel, load)
</script>

<template>
  <div class="page-container">
    <PageHeading :eyebrow="eyebrow" :title="title" :description="description">
      <template #actions>
        <span class="status-pill status-pill--success"><PackageCheck :size="13" />单件交易</span>
        <span class="status-pill" :class="isSeckill ? 'status-pill--warning' : 'status-pill--shop'">{{ isSeckill ? '24 小时场次' : '30 天在售' }}</span>
      </template>
    </PageHeading>

    <section class="channel-banner" :class="{ 'channel-banner--shop': !isSeckill }">
      <div>
        <h3>{{ isSeckill ? '秒杀不是大促，是流拍商品的一次限期机会' : '这里没有凑单，只有一件物品和明确的一口价' }}</h3>
        <p>{{ isSeckill ? '库存只有 1 件；提交后锁定 15 分钟，成功售出后立即结束。' : '同一商品任意时刻只处于一个渠道；秒杀未售出且仍在价格链内时才会进入商城。' }}</p>
      </div>
      <span class="status-pill status-pill--dark"><TimerReset :size="13" />{{ isSeckill ? '库存仅 1 件' : '商城到期自动退回库存' }}</span>
      <span class="status-pill status-pill--success"><ShieldCheck :size="13" />{{ isSeckill ? '无买家保证金' : '虚拟钱包担保' }}</span>
    </section>

    <div class="section-heading">
      <div><h2>{{ isSeckill ? '正在秒杀' : '正在一口价出售' }}</h2><p>{{ isSeckill ? '倒计时以平台时间为准，售价不会高于上一渠道价格。' : '每件商品只有一个明确价格；点击购买即生成单件订单。' }}</p></div>
      <span class="muted-note">{{ loading ? '正在读取' : `${lots.length} 件在售` }}</span>
    </div>
    <div v-if="lots.length" class="lot-grid lot-grid--four" v-loading="loading">
      <LotCard v-for="lot in lots" :key="lot.id" :lot="lot" />
    </div>
    <EmptyState v-else :title="error ? '暂时无法读取渠道商品' : `当前没有${isSeckill ? '秒杀' : '商城'}商品`" :description="error || '流拍商品进入对应渠道后会显示在这里。'" />

    <section class="rule-grid">
      <div class="info-panel"><span>01</span><h3>单一有效渠道</h3><p>拍卖、秒杀和商城不能同时占用同一库存单元。</p></div>
      <div class="info-panel"><span>02</span><h3>{{ isSeckill ? '15 分钟库存锁定' : '单件一口价' }}</h3><p>{{ isSeckill ? '超时未付款时，系统会自动释放库存。' : '不提供购物车、SKU、数量和多件合并购买。' }}</p></div>
      <div class="info-panel"><span>03</span><h3>AI 不参与成交</h3><p>AI 可以提供描述和检索建议，但不决定下单、付款或最终成交。</p></div>
    </section>
  </div>
</template>
