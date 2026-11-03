<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import { ArrowRight, Clock3, PackageOpen, Plus, RefreshCw } from 'lucide-vue-next'
import PageHeading from '@/components/PageHeading.vue'
import ProductArtwork from '@/components/ProductArtwork.vue'
import StatusPill from '@/components/StatusPill.vue'
import EmptyState from '@/components/EmptyState.vue'
import { apiRequest, errorMessage } from '@/api/client'
import type { CursorPage, ListingVO } from '@/types/api'
import { listingToMarketLot } from '@/utils/catalog'
import { categoryText, conditionText, formatDateTime, formatMoney, statusText } from '@/utils/format'

type FilterValue = 'ALL' | 'DRAFT' | 'REVIEW' | 'READY' | 'LIVE' | 'DECISION' | 'DOWNGRADE' | 'ENDED'

const loading = ref(true)
const loadingMore = ref(false)
const error = ref('')
const listings = ref<ListingVO[]>([])
const nextCursor = ref<string | null>(null)
const active = ref<FilterValue>('ALL')

const filters: { value: FilterValue; label: string }[] = [
  { value: 'ALL', label: '全部记录' },
  { value: 'DRAFT', label: '草稿' },
  { value: 'REVIEW', label: '审核中' },
  { value: 'READY', label: '待排期' },
  { value: 'LIVE', label: '拍卖中' },
  { value: 'DECISION', label: '待处置' },
  { value: 'DOWNGRADE', label: '秒杀 / 商城' },
  { value: 'ENDED', label: '已结束' },
]

const liveStatuses = new Set(['SCHEDULED', 'AUCTION_LIVE'])
const endedStatuses = new Set(['COMPLETED', 'DISPUTED', 'RETURNED', 'CANCELLED', 'SOLD_PENDING_PAYMENT'])

function matchesFilter(listing: ListingVO): boolean {
  if (active.value === 'ALL') return true
  if (active.value === 'DRAFT') return listing.status === 'DRAFT'
  if (active.value === 'REVIEW') return ['PENDING_REVIEW', 'CHANGES_REQUIRED', 'REJECTED'].includes(listing.status)
  if (active.value === 'READY') return listing.status === 'APPROVED'
  if (active.value === 'LIVE') return liveStatuses.has(listing.status)
  if (active.value === 'DECISION') return listing.status === 'WAITING_SELLER_DECISION'
  if (active.value === 'DOWNGRADE') return ['SECKILL', 'SHOP'].includes(listing.status)
  return endedStatuses.has(listing.status)
}

const visible = computed(() => listings.value.filter(matchesFilter))
const summary = computed(() => ({
  total: listings.value.length,
  active: listings.value.filter((item) => ['SCHEDULED', 'AUCTION_LIVE', 'SECKILL', 'SHOP'].includes(item.status)).length,
  review: listings.value.filter((item) => ['PENDING_REVIEW', 'CHANGES_REQUIRED'].includes(item.status)).length,
  waiting: listings.value.filter((item) => item.status === 'WAITING_SELLER_DECISION').length,
}))

function currentChannel(listing: ListingVO): 'auction' | 'seckill' | 'shop' {
  if (listing.status === 'SECKILL') return 'seckill'
  if (listing.status === 'SHOP') return 'shop'
  return 'auction'
}

async function load(reset = true): Promise<void> {
  if (reset) {
    loading.value = true
    listings.value = []
    nextCursor.value = null
  } else {
    loadingMore.value = true
  }
  error.value = ''
  try {
    const cursor = !reset && nextCursor.value ? `&cursor=${encodeURIComponent(nextCursor.value)}` : ''
    const page = await apiRequest<CursorPage<ListingVO>>(`/listings/mine?limit=30${cursor}`)
    listings.value = reset ? page.items : [...listings.value, ...page.items]
    nextCursor.value = page.nextCursor
  } catch (cause) {
    error.value = errorMessage(cause)
  } finally {
    loading.value = false
    loadingMore.value = false
  }
}

onMounted(() => load(true))
</script>

<template>
  <div class="workspace-page" v-loading="loading">
    <PageHeading eyebrow="我的拍品" title="从草稿到成交，完整记录都在这里" description="商品先经过拍卖，再按授权进入秒杀和商城；审核意见、渠道价格与处置入口均可核对。">
      <template #actions><RouterLink class="button button--dark button--with-icon" to="/workspace/publish"><Plus :size="15" /> 发起拍卖</RouterLink></template>
    </PageHeading>

    <div class="summary-row">
      <div><span>全部商品</span><b>{{ summary.total }}</b></div>
      <div><span>正在流转</span><b>{{ summary.active }}</b></div>
      <div><span>待审核 / 修改</span><b>{{ summary.review }}</b></div>
      <div><span>待我处置</span><b class="accent-text">{{ summary.waiting }}</b></div>
    </div>

    <div class="toolbar panel"><div class="filter-chips"><button v-for="filter in filters" :key="filter.value" class="filter-chip" :class="{ active: active === filter.value }" type="button" @click="active = filter.value">{{ filter.label }}</button></div><button class="button button--small button--outline button--with-icon" type="button" :disabled="loading" @click="load(true)"><RefreshCw :size="14" /> 刷新</button></div>

    <div class="listing-table panel">
      <div class="listing-table__head"><span>拍品</span><span>当前渠道</span><span>价格链</span><span>状态与时间</span><span>操作</span></div>
      <article v-for="(listing, index) in visible" :key="listing.id" class="listing-row">
        <div class="listing-row__product"><ProductArtwork :kind="listingToMarketLot(listing, null, index).artwork" :tone="listingToMarketLot(listing, null, index).tone" /><div><b>{{ listing.title }}</b><span>#{{ listing.id }} · {{ categoryText(listing.categoryCode) }} · {{ conditionText(listing.conditionCode) }}</span></div></div>
        <div><StatusPill :text="currentChannel(listing) === 'auction' ? '拍卖' : currentChannel(listing) === 'seckill' ? '秒杀' : '商城'" :tone="currentChannel(listing)" /><span class="cell-note">{{ listing.autoDowngrade ? '已授权流拍降级' : '流拍后由你决定' }}</span></div>
        <div class="price-chain"><span>起拍 {{ formatMoney(listing.startPrice) }}</span><span>秒杀 {{ listing.seckillPrice ? formatMoney(listing.seckillPrice) : '未设置' }}</span><span>商城 {{ listing.shopPrice ? formatMoney(listing.shopPrice) : '未设置' }}</span></div>
        <div><StatusPill :text="statusText(listing.status)" :tone="listing.status === 'WAITING_SELLER_DECISION' || listing.status === 'CHANGES_REQUIRED' ? 'warning' : listing.status === 'REJECTED' ? 'danger' : 'success'" /><span class="cell-note"><Clock3 :size="12" />{{ formatDateTime(listing.updatedAt) }}</span><span v-if="listing.rejectionReason" class="cell-note rejection-note">{{ listing.rejectionReason }}</span></div>
        <div class="row-actions"><RouterLink class="button button--small button--outline" :to="`/workspace/listings/${listing.id}`">管理</RouterLink><RouterLink v-if="listing.status === 'WAITING_SELLER_DECISION'" class="button button--small" :to="`/workspace/listings/${listing.id}/unsold`">去处置</RouterLink><RouterLink v-else-if="listing.status === 'APPROVED'" class="button button--small" :to="`/workspace/listings/${listing.id}`">确认排期</RouterLink></div>
      </article>
      <EmptyState v-if="!visible.length" :title="error ? '暂时无法读取商品记录' : '当前筛选下没有商品'" :description="error || '创建草稿后，商品的生命周期会在这里持续更新。'" :action-text="error ? '重新加载' : ''" @action="load(true)" />
    </div>

    <div v-if="nextCursor" class="load-more-wrap"><button class="button button--outline" type="button" :disabled="loadingMore" @click="load(false)"><RefreshCw :size="14" />{{ loadingMore ? '正在加载…' : '加载更多商品' }}</button></div>
    <div class="warm-note"><PackageOpen :size="16" /><p>同一商品在任意时刻只会出现在拍卖、秒杀或商城中的一个渠道。价格必须满足商城价 ≤ 秒杀价 ≤ 起拍价。</p><ArrowRight :size="15" /></div>
  </div>
</template>

<style scoped>
.toolbar { display: flex; align-items: center; justify-content: space-between; gap: 16px; }
.rejection-note { max-width: 220px; color: var(--danger); white-space: normal; }
.load-more-wrap { display: flex; justify-content: center; margin-top: 20px; }
@media (max-width: 760px) { .toolbar { align-items: stretch; flex-direction: column; } }
</style>
