<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import { ArrowRight, Gavel, History, RefreshCw } from 'lucide-vue-next'
import PageHeading from '@/components/PageHeading.vue'
import StatusPill from '@/components/StatusPill.vue'
import EmptyState from '@/components/EmptyState.vue'
import { apiRequest, errorMessage } from '@/api/client'
import type { BidHistoryVO, CursorPage } from '@/types/api'
import { formatDateTime, formatMoney, statusText } from '@/utils/format'

const loading = ref(true)
const loadingMore = ref(false)
const error = ref('')
const rows = ref<BidHistoryVO[]>([])
const nextCursor = ref<string | null>(null)
const filter = ref<'ALL' | 'LEADING' | 'ENDED'>('ALL')
const filterChoices: { value: 'ALL' | 'LEADING' | 'ENDED'; label: string }[] = [
  { value: 'ALL', label: '全部记录' },
  { value: 'LEADING', label: '进行中' },
  { value: 'ENDED', label: '已结束' },
]

const visible = computed(() => rows.value.filter((row) => {
  if (filter.value === 'ALL') return true
  if (filter.value === 'LEADING') return row.auctionStatus === 'LIVE' && row.highestBidderId !== null
  return row.auctionStatus !== 'LIVE'
}))
const highestBid = computed(() => rows.value.reduce((max, row) => Math.max(max, Number(row.amount)), 0))

async function load(reset = true): Promise<void> {
  if (reset) {
    loading.value = true
    rows.value = []
    nextCursor.value = null
  } else {
    loadingMore.value = true
  }
  error.value = ''
  try {
    const cursor = !reset && nextCursor.value ? `&cursor=${encodeURIComponent(nextCursor.value)}` : ''
    const page = await apiRequest<CursorPage<BidHistoryVO>>(`/auctions/mine/bids?limit=30${cursor}`)
    rows.value = reset ? page.items : [...rows.value, ...page.items]
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
    <PageHeading eyebrow="我的出价" title="每次出价都保留完整时间和保证金依据" description="这里展示长期出价历史，不以公开拍场当前第一页代替个人交易记录。">
      <template #actions><RouterLink class="button button--outline button--with-icon" to="/auctions"><Gavel :size="15" /> 去拍场</RouterLink></template>
    </PageHeading>
    <div class="summary-row"><div><span>出价记录</span><b>{{ rows.length }}</b></div><div><span>单次最高</span><b>{{ formatMoney(highestBid) }}</b></div><div><span>进行中拍场</span><b>{{ rows.filter((row) => row.auctionStatus === 'LIVE').length }}</b></div><div><span>已结束</span><b>{{ rows.filter((row) => row.auctionStatus !== 'LIVE').length }}</b></div></div>
    <div class="toolbar panel"><div class="filter-chips"><button v-for="item in filterChoices" :key="item.value" class="filter-chip" :class="{ active: filter === item.value }" type="button" @click="filter = item.value">{{ item.label }}</button></div><button class="button button--small button--outline button--with-icon" type="button" @click="load(true)"><RefreshCw :size="14" /> 刷新</button></div>
    <div v-if="visible.length" class="bid-history-list">
      <article v-for="row in visible" :key="row.id" class="panel bid-history-row">
        <span class="bid-history-row__icon"><History :size="18" /></span>
        <div class="bid-history-row__main"><div><StatusPill :text="statusText(row.auctionStatus)" :tone="row.auctionStatus === 'LIVE' ? 'auction' : 'neutral'" /><StatusPill :text="row.bidStatus === 'VALID' ? '有效出价' : '已取消'" :tone="row.bidStatus === 'VALID' ? 'success' : 'danger'" /></div><h3>{{ row.listingTitle }}</h3><p>拍场 #{{ row.auctionId }} · {{ formatDateTime(row.createdAt) }}</p></div>
        <dl><div><dt>我的出价</dt><dd>{{ formatMoney(row.amount) }}</dd></div><div><dt>当前价</dt><dd>{{ formatMoney(row.currentPrice) }}</dd></div><div><dt>保证金</dt><dd>{{ formatMoney(row.depositRequired) }}</dd></div></dl>
        <RouterLink class="button button--small button--outline button--with-icon" :to="`/listings/${row.listingId}`">查看商品 <ArrowRight :size="14" /></RouterLink>
      </article>
    </div>
    <EmptyState v-else :title="error ? '出价记录暂时无法读取' : '当前没有出价记录'" :description="error || '参与拍卖后，每次出价、保证金和最终状态会保存在这里。'" :action-text="error ? '重新加载' : ''" @action="load(true)" />
    <div v-if="nextCursor" class="load-more-wrap"><button class="button button--outline" type="button" :disabled="loadingMore" @click="load(false)">{{ loadingMore ? '正在加载…' : '加载更早记录' }}</button></div>
  </div>
</template>

<style scoped>
.toolbar { display: flex; align-items: center; justify-content: space-between; gap: 16px; }
.bid-history-list { display: grid; gap: 12px; margin-top: 16px; }
.bid-history-row { display: grid; grid-template-columns: auto minmax(0, 1fr) auto auto; gap: 18px; align-items: center; padding: 18px 20px; }
.bid-history-row__icon { display: grid; width: 40px; height: 40px; place-items: center; border-radius: 4px; background: var(--surface-muted); }
.bid-history-row__main { display: grid; min-width: 0; gap: 5px; }
.bid-history-row__main > div { display: flex; gap: 6px; }
.bid-history-row__main h3 { overflow: hidden; font-family: var(--font-body); font-size: 16px; font-weight: 700; text-overflow: ellipsis; white-space: nowrap; }
.bid-history-row__main p { color: var(--muted); font-size: 12px; }
.bid-history-row dl { display: flex; gap: 22px; }
.bid-history-row dl div { display: grid; gap: 3px; }
.bid-history-row dt { color: var(--muted); font-size: 12px; }
.bid-history-row dd { font-weight: 700; }
.load-more-wrap { display: flex; justify-content: center; margin-top: 20px; }
@media (max-width: 900px) { .bid-history-row { grid-template-columns: auto minmax(0, 1fr); } .bid-history-row dl { grid-column: 2; } .bid-history-row > a { grid-column: 2; width: fit-content; } }
@media (max-width: 760px) { .toolbar { align-items: stretch; flex-direction: column; } .bid-history-row dl { flex-wrap: wrap; } }
</style>
