<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import { Radio, RefreshCw, Search } from 'lucide-vue-next'
import PageHeading from '@/components/PageHeading.vue'
import LotCard from '@/components/LotCard.vue'
import EmptyState from '@/components/EmptyState.vue'
import { apiRequest, errorMessage } from '@/api/client'
import type { AuctionVO, CursorPage, ListingVO } from '@/types/api'
import type { MarketLot } from '@/types/catalog'
import { listingToMarketLot } from '@/utils/catalog'
import { formatMoney, statusText } from '@/utils/format'

const loading = ref(true)
const error = ref('')
const lots = ref<MarketLot[]>([])
const searchText = ref('')
const activeFilter = ref('全部')
const filters = ['全部', '即将结束', '新上拍', '低起拍价']

const visibleLots = computed(() => {
  const term = searchText.value.trim().toLowerCase()
  const filtered = term
    ? lots.value.filter((lot) => `${lot.title}${lot.lotNo}`.toLowerCase().includes(term))
    : [...lots.value]
  if (activeFilter.value === '即将结束') {
    return filtered.sort((left, right) => new Date(left.endsAt || 0).getTime() - new Date(right.endsAt || 0).getTime())
  }
  if (activeFilter.value === '低起拍价') {
    return filtered.sort((left, right) => Number(left.startPrice) - Number(right.startPrice))
  }
  return filtered
})

const stats = computed(() => {
  const prices = lots.value.map((lot) => Number(lot.price)).filter(Number.isFinite)
  const endingSoon = lots.value.filter((lot) => {
    if (!lot.endsAt) return false
    const remaining = new Date(lot.endsAt).getTime() - Date.now()
    return remaining > 0 && remaining <= 60 * 60_000
  }).length
  return [
    { label: '当前拍卖', value: String(lots.value.length) },
    { label: '一小时内结束', value: String(endingSoon) },
    { label: '最低当前价', value: prices.length ? formatMoney(String(Math.min(...prices))) : '—' },
    { label: '最高当前价', value: prices.length ? formatMoney(String(Math.max(...prices))) : '—' },
  ]
})

async function loadAuctions(): Promise<void> {
  loading.value = true
  error.value = ''
  try {
    const page = await apiRequest<CursorPage<AuctionVO>>('/auctions?limit=12&activeOnly=true')
    const hydrated = await Promise.all(
      page.items.map(async (auction, index) => {
        try {
          const listing = await apiRequest<ListingVO>(`/listings/${auction.listingId}`)
          return listingToMarketLot(listing, auction, index, 'auction')
        } catch {
          return null
        }
      }),
    )
    lots.value = hydrated.filter((lot): lot is MarketLot => lot !== null)
  } catch (cause) {
    error.value = errorMessage(cause)
  } finally {
    loading.value = false
  }
}

onMounted(loadAuctions)
</script>

<template>
  <div class="page-container" v-loading="loading">
    <PageHeading eyebrow="AUCTION ROOM" title="正在发生的拍卖" description="按剩余时间、价格和类目筛选。出价成功即冻结对应保证金，遇到更高出价时会显示最新价格。">
      <template #actions>
        <span class="status-pill status-pill--auction"><Radio :size="12" />{{ lots.length }} 场进行中</span>
      </template>
    </PageHeading>

    <div class="toolbar panel">
      <div class="filter-chips">
        <button v-for="filter in filters" :key="filter" class="filter-chip" :class="{ active: activeFilter === filter }" type="button" @click="activeFilter = filter">{{ filter }}</button>
      </div>
      <div class="toolbar__right">
        <label class="inline-search"><Search :size="15" /><input v-model="searchText" placeholder="搜索标题或拍品编号" /></label>
      </div>
    </div>

    <section class="metric-strip metric-strip--compact">
      <div v-for="stat in stats" :key="stat.label" class="metric-strip__item"><span>{{ stat.label }}</span><strong>{{ stat.value }}</strong></div>
    </section>

    <div class="section-heading">
      <div><h2>值得看一眼</h2><p>结束前 2 分钟内的新出价由平台规则判断是否顺延。</p></div>
      <button class="text-button" type="button" @click="loadAuctions"><RefreshCw :size="13" /> 刷新状态</button>
    </div>
    <div v-if="visibleLots.length" class="lot-grid lot-grid--four">
      <div v-for="lot in visibleLots" :key="lot.id" class="lot-card-button">
        <LotCard :lot="lot" compact />
      </div>
    </div>
    <EmptyState v-else :title="error ? '暂时无法读取拍卖' : '当前没有符合条件的拍卖'" :description="error || '有新的拍卖开始后会显示在这里。'" />

    <div class="section-heading section-heading--table">
      <div><h2>直播式出价记录</h2><p>页面只展示平台确认的当前价和保证金，不在本地自行抬高价格。</p></div>
      <span class="status-pill status-pill--success"><Radio :size="12" />平台时间同步</span>
    </div>
    <div class="table-panel">
      <table class="data-table">
        <thead><tr><th>拍品</th><th>当前价</th><th>保证金</th><th>剩余时间</th><th>状态</th><th>操作</th></tr></thead>
        <tbody>
          <tr v-for="lot in visibleLots.slice(0, 4)" :key="`row-${lot.id}`">
            <td><div class="table-lot"><span class="table-lot__thumb">{{ lot.lotNo }}</span><div><b>{{ lot.title }}</b><span>{{ lot.category }} · LOT {{ lot.lotNo }}</span></div></div></td>
            <td class="money-cell">{{ formatMoney(lot.price) }}</td>
            <td class="money-cell">出价页确认</td>
            <td class="mono">{{ lot.endsAt ? new Date(lot.endsAt).toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' }) : '待定' }}</td>
            <td><span class="status-pill status-pill--auction">{{ statusText(lot.status) }}</span></td>
            <td><RouterLink class="button button--small" :to="`/listings/${lot.id}?channel=auction${lot.auctionId ? `&auctionId=${lot.auctionId}` : ''}`">去出价</RouterLink></td>
          </tr>
        </tbody>
      </table>
    </div>
  </div>
</template>
