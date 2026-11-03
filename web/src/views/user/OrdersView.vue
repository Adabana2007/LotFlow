<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import { ArrowRight, Box, Clock3, FileSearch, ShoppingBag } from 'lucide-vue-next'
import PageHeading from '@/components/PageHeading.vue'
import StatusPill from '@/components/StatusPill.vue'
import CountdownText from '@/components/CountdownText.vue'
import EmptyState from '@/components/EmptyState.vue'
import { apiRequest, errorMessage } from '@/api/client'
import type { CursorPage, OrderSummaryVO } from '@/types/api'
import { formatDateTime, formatMoney, sourceText, statusText } from '@/utils/format'

type RoleFilter = 'ALL' | 'BUYER' | 'SELLER'

const loading = ref(true)
const loadingMore = ref(false)
const error = ref('')
const rows = ref<OrderSummaryVO[]>([])
const nextCursor = ref<string | null>(null)
const role = ref<RoleFilter>('ALL')
const status = ref('')

const statusChoices = [
  { value: '', label: '全部状态' },
  { value: 'PENDING_PAYMENT', label: '待付款' },
  { value: 'SECOND_CHANCE', label: '顺延待付款' },
  { value: 'PAID', label: '待发货' },
  { value: 'SHIPPED', label: '待收货' },
  { value: 'DISPUTED', label: '争议中' },
  { value: 'COMPLETED', label: '已完成' },
]
const roleChoices: { value: RoleFilter; label: string }[] = [
  { value: 'ALL', label: '全部身份' },
  { value: 'BUYER', label: '我买到的' },
  { value: 'SELLER', label: '我卖出的' },
]

const pendingCount = computed(() => rows.value.filter((row) =>
  row.role === 'BUYER' && ['PENDING_PAYMENT', 'SECOND_CHANCE'].includes(row.status)).length)
const shipmentCount = computed(() => rows.value.filter((row) =>
  row.role === 'SELLER' && row.status === 'PAID').length)
const disputeCount = computed(() => rows.value.filter((row) => row.status === 'DISPUTED').length)

function deadlineFor(order: OrderSummaryVO): string | null {
  if (['PENDING_PAYMENT', 'SECOND_CHANCE'].includes(order.status)) {
    return order.paymentDeadline || order.reservationExpiresAt
  }
  if (order.status === 'PAID' && order.role === 'SELLER') return order.shipDeadline
  return null
}

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
    const params = new URLSearchParams({ role: role.value, limit: '20' })
    if (status.value) params.set('status', status.value)
    if (!reset && nextCursor.value) params.set('cursor', nextCursor.value)
    const page = await apiRequest<CursorPage<OrderSummaryVO>>(`/orders?${params}`)
    rows.value = reset ? page.items : [...rows.value, ...page.items]
    nextCursor.value = page.nextCursor
  } catch (cause) {
    error.value = errorMessage(cause)
  } finally {
    loading.value = false
    loadingMore.value = false
  }
}

function applyFilters(): void {
  void load(true)
}

function chooseRole(value: RoleFilter): void {
  role.value = value
  applyFilters()
}

onMounted(() => load(true))
</script>

<template>
  <div class="workspace-page" v-loading="loading">
    <PageHeading eyebrow="我的订单" title="每笔交易都有清楚的下一步" description="同时包含买家与卖家订单；付款、发货、收货和争议均按订单状态开放。">
      <template #actions><RouterLink class="button button--dark button--with-icon" to="/workspace/listings"><ShoppingBag :size="15" /> 查看我的拍品</RouterLink></template>
    </PageHeading>

    <div class="summary-row">
      <div><span>当前列表</span><b>{{ rows.length }}</b></div>
      <div><span>待我付款</span><b class="accent-text">{{ pendingCount }}</b></div>
      <div><span>待我发货</span><b>{{ shipmentCount }}</b></div>
      <div><span>争议中</span><b>{{ disputeCount }}</b></div>
    </div>

    <div class="toolbar panel orders-toolbar">
      <div class="filter-chips">
        <button v-for="item in roleChoices" :key="item.value" class="filter-chip" :class="{ active: role === item.value }" type="button" @click="chooseRole(item.value)">{{ item.label }}</button>
      </div>
      <label class="inline-search"><FileSearch :size="14" /><select v-model="status" @change="applyFilters"><option v-for="item in statusChoices" :key="item.value" :value="item.value">{{ item.label }}</option></select></label>
    </div>

    <div v-if="rows.length" class="order-list">
      <article v-for="order in rows" :key="order.id" class="panel order-list-card">
        <div class="order-list-card__main">
          <span class="order-list-card__icon"><Box :size="20" /></span>
          <div class="order-list-card__title">
            <div><StatusPill :text="order.role === 'BUYER' ? '我买到' : '我卖出'" tone="neutral" /><StatusPill :text="sourceText(order.sourceType)" :tone="order.sourceType === 'SECKILL' ? 'seckill' : order.sourceType === 'SHOP' ? 'shop' : 'auction'" /></div>
            <h3>{{ order.listingTitle }}</h3>
            <p>{{ order.orderNo }} · 对方 {{ order.counterpartyName }}</p>
          </div>
          <div class="order-list-card__amount"><span>订单金额</span><b>{{ formatMoney(order.amount) }}</b><small>{{ formatDateTime(order.createdAt) }}</small></div>
          <StatusPill :text="statusText(order.status)" :tone="order.status === 'DISPUTED' ? 'danger' : ['PENDING_PAYMENT', 'SECOND_CHANCE', 'PAID'].includes(order.status) ? 'warning' : 'success'" />
        </div>
        <div class="order-list-card__foot">
          <div v-if="deadlineFor(order)"><Clock3 :size="14" /><span>{{ ['PENDING_PAYMENT', 'SECOND_CHANCE'].includes(order.status) ? '付款剩余' : '发货剩余' }}</span><CountdownText :deadline="deadlineFor(order)" /></div>
          <div v-else><Clock3 :size="14" /><span>{{ order.status === 'SHIPPED' ? '确认收货后进入售后观察期' : '当前没有操作倒计时' }}</span></div>
          <RouterLink class="button button--small button--outline button--with-icon" :to="`/orders/${order.id}`">查看订单 <ArrowRight :size="14" /></RouterLink>
        </div>
      </article>
    </div>

    <EmptyState
      v-else
      :title="error ? '订单暂时无法读取' : '当前筛选下没有订单'"
      :description="error || '完成拍卖、秒杀或商城购买后，订单会出现在这里。'"
      :action-text="error ? '重新加载' : ''"
      @action="load(true)"
    />

    <div v-if="nextCursor" class="load-more-wrap"><button class="button button--outline" type="button" :disabled="loadingMore" @click="load(false)">{{ loadingMore ? '正在加载…' : '加载更多订单' }}</button></div>
  </div>
</template>

<style scoped>
.orders-toolbar { display: flex; align-items: center; justify-content: space-between; gap: 16px; }
.order-list { display: grid; gap: 12px; margin-top: 16px; }
.order-list-card { padding: 18px 20px; }
.order-list-card__main { display: grid; grid-template-columns: auto minmax(0, 1fr) auto auto; gap: 16px; align-items: center; }
.order-list-card__icon { display: grid; width: 42px; height: 42px; place-items: center; border-radius: 4px; background: var(--surface-muted); color: var(--ink-soft); }
.order-list-card__title { display: grid; min-width: 0; gap: 5px; }
.order-list-card__title > div { display: flex; gap: 6px; }
.order-list-card__title h3, .order-list-card__title p { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.order-list-card__title h3 { font-family: var(--font-body); font-size: 16px; font-weight: 700; }
.order-list-card__title p, .order-list-card__amount span, .order-list-card__amount small { color: var(--muted); font-size: 12px; }
.order-list-card__amount { display: grid; min-width: 120px; gap: 3px; text-align: right; }
.order-list-card__amount b { font-size: 18px; }
.order-list-card__foot { display: flex; align-items: center; justify-content: space-between; gap: 16px; margin-top: 14px; padding-top: 12px; border-top: 1px solid var(--line); }
.order-list-card__foot > div { display: flex; align-items: center; gap: 6px; color: var(--muted); font-size: 12px; }
.load-more-wrap { display: flex; justify-content: center; margin-top: 20px; }
@media (max-width: 760px) { .orders-toolbar { align-items: stretch; flex-direction: column; } .order-list-card__main { grid-template-columns: auto minmax(0, 1fr); } .order-list-card__amount { text-align: left; } .order-list-card__main > .status-pill { grid-column: 2; width: fit-content; } }
</style>
