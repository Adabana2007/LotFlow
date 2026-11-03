<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import { ChevronRight, MessageSquareWarning, Search, ShieldAlert, WalletCards } from 'lucide-vue-next'
import PageHeading from '@/components/PageHeading.vue'
import StatusPill from '@/components/StatusPill.vue'
import { apiRequest, errorMessage } from '@/api/client'
import type { CursorPage, DisputeVO } from '@/types/api'
import { formatFullDate, formatMoney } from '@/utils/format'

const loading = ref(true)
const loadingMore = ref(false)
const loadError = ref('')
const rows = ref<DisputeVO[]>([])
const nextCursor = ref<string | null>(null)
const search = ref('')
const stateFilter = ref('全部争议')
const states = ['全部争议', '待人工处理', '已处理']

function cents(value: string): bigint {
  if (!/^-?\d+(\.\d{1,2})?$/.test(value || '0')) return 0n
  const negative = value.startsWith('-')
  const normalized = negative ? value.slice(1) : value
  const [whole = '0', fraction = ''] = normalized.split('.')
  const amount = BigInt(whole) * 100n + BigInt((fraction + '00').slice(0, 2))
  return negative ? -amount : amount
}

function formatCents(value: bigint): string {
  const negative = value < 0n
  const absolute = negative ? -value : value
  return `${negative ? '-' : ''}${absolute / 100n}.${String(absolute % 100n).padStart(2, '0')}`
}

const pending = computed(() => rows.value.filter((row) => row.disputeStatus === 'OPEN'))
const resolved = computed(() => rows.value.filter((row) => row.disputeStatus === 'RESOLVED'))
const frozenAmount = computed(() => formatCents(pending.value.reduce((sum, row) => sum + cents(row.escrowAmount), 0n)))
const visible = computed(() => rows.value.filter((row) => {
  const keyword = search.value.trim().toLowerCase()
  const matchesSearch = !keyword || `${row.orderNo}${row.listingTitle}${row.buyerName}${row.sellerName}${row.reason}`.toLowerCase().includes(keyword)
  const matchesState = stateFilter.value === '全部争议'
    || (stateFilter.value === '待人工处理' && row.disputeStatus === 'OPEN')
    || (stateFilter.value === '已处理' && row.disputeStatus === 'RESOLVED')
  return matchesSearch && matchesState
}))

async function load(reset = true): Promise<void> {
  if (reset) {
    loading.value = true
    nextCursor.value = null
    loadError.value = ''
  } else {
    if (!nextCursor.value || loadingMore.value) return
    loadingMore.value = true
  }
  try {
    const cursor = reset ? '' : `&cursor=${encodeURIComponent(nextCursor.value || '')}`
    const page = await apiRequest<CursorPage<DisputeVO>>(`/admin/disputes?limit=50${cursor}`)
    rows.value = reset ? page.items : [...rows.value, ...page.items]
    nextCursor.value = page.nextCursor
  } catch (error) {
    if (reset) rows.value = []
    loadError.value = errorMessage(error)
  } finally {
    loading.value = false
    loadingMore.value = false
  }
}

onMounted(() => load())
</script>

<template>
  <div class="admin-page" v-loading="loading">
    <PageHeading eyebrow="DISPUTE OPERATIONS" title="交易争议" description="集中查看争议证据、资金状态和交易时间线；最终资金处理必须由管理员明确提交。">
      <template #actions><StatusPill text="人工裁决" tone="warning" /></template>
    </PageHeading>
    <div class="admin-metric-grid admin-metric-grid--small"><div class="panel admin-metric"><span>待人工处理</span><strong>{{ pending.length }}</strong><p>当前已加载争议</p></div><div class="panel admin-metric"><span>担保中金额</span><strong>{{ formatMoney(frozenAmount) }}</strong><p>待处理争议关联货款</p></div><div class="panel admin-metric"><span>已处理</span><strong>{{ resolved.length }}</strong><p>已形成可追溯结果</p></div><div class="panel admin-metric"><span>AI 最终裁决</span><strong class="green-text">0</strong><p>AI 只做只读摘要</p></div></div>
    <div class="toolbar panel"><div class="filter-chips"><button v-for="state in states" :key="state" class="filter-chip" :class="{ active: stateFilter === state }" type="button" @click="stateFilter = state">{{ state }}</button></div><label class="inline-search"><Search :size="14" /><input v-model="search" placeholder="搜索订单、商品或当事人" /></label></div>
    <div class="dispute-admin-list">
      <article v-for="row in visible" :key="row.id" class="panel dispute-admin-row">
        <div class="dispute-admin-row__main"><span class="dispute-icon"><MessageSquareWarning :size="18" /></span><div><StatusPill :text="row.disputeStatus === 'OPEN' ? '待人工处理' : '人工已处理'" :tone="row.disputeStatus === 'OPEN' ? 'warning' : 'success'" /><h3>{{ row.listingTitle }}</h3><p>{{ row.reason }}</p><span>订单 {{ row.orderNo }} · 买家 {{ row.buyerName }} · 卖家 {{ row.sellerName }}</span></div></div>
        <div class="dispute-admin-row__sla"><WalletCards :size="17" /><span>争议金额</span><b>{{ formatMoney(row.escrowAmount || row.amount) }}</b><span>{{ formatFullDate(row.disputeOpenedAt) }}</span></div>
        <RouterLink class="button button--outline button--with-icon" :to="`/admin/disputes/${row.id}`">查看证据 <ChevronRight :size="14" /></RouterLink>
      </article>
    </div>
    <div v-if="visible.length && nextCursor" class="load-more-row"><button class="button button--outline" type="button" :disabled="loadingMore" @click="load(false)">{{ loadingMore ? '正在加载…' : '加载更多争议' }}</button></div>
    <div v-if="!visible.length" class="panel empty-state"><ShieldAlert :size="26" /><b>{{ loadError ? '争议列表读取失败' : '没有匹配争议' }}</b><p>{{ loadError || '调整筛选或搜索条件后再试。' }}</p><button v-if="loadError" class="button button--outline" type="button" @click="load()">重新加载</button></div>
    <div class="warm-note"><ShieldAlert :size="16" /><p>订单金额、保证金和担保金额在同一条争议事实中核对；任何资金变化都必须由人工裁决触发并保留流水。</p></div>
  </div>
</template>

<style scoped>
.load-more-row { display: flex; justify-content: center; margin-top: 12px; }
</style>
