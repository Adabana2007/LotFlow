<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import {
  ArrowRight,
  Bot,
  Clock3,
  ListChecks,
  PackageOpen,
  Sparkles,
  WalletCards,
} from 'lucide-vue-next'
import PageHeading from '@/components/PageHeading.vue'
import LotCard from '@/components/LotCard.vue'
import EmptyState from '@/components/EmptyState.vue'
import StatusPill from '@/components/StatusPill.vue'
import { apiRequest, errorMessage } from '@/api/client'
import { useAuthStore } from '@/stores/auth'
import type {
  AuctionVO,
  CursorPage,
  ListingVO,
  OrderSummaryVO,
  TodoItemVO,
  WalletVO,
} from '@/types/api'
import type { MarketLot } from '@/types/catalog'
import { listingToMarketLot } from '@/utils/catalog'
import { formatDateTime, formatMoney } from '@/utils/format'

const auth = useAuthStore()
const wallet = ref<WalletVO | null>(null)
const todos = ref<TodoItemVO[]>([])
const orders = ref<OrderSummaryVO[]>([])
const listings = ref<ListingVO[]>([])
const activeLots = ref<MarketLot[]>([])
const loading = ref(true)
const error = ref('')

const todoTypeLabels: Record<string, string> = {
  ORDER_PAYMENT: '待付款订单',
  SELLER_DECISION: '流拍处置',
  SELLER_SHIPMENT: '待发货',
  LISTING_CHANGES: '商品需修改',
}

const pendingOrderCount = computed(() => orders.value.filter((order) =>
  order.role === 'BUYER' && ['PENDING_PAYMENT', 'SECOND_CHANCE'].includes(order.status)).length)
const metrics = computed(() => [
  {
    label: '可用余额',
    value: wallet.value ? formatMoney(wallet.value.availableBalance) : '—',
    note: `冻结 ${wallet.value ? formatMoney(wallet.value.frozenBalance) : '—'}`,
  },
  { label: '待处理事项', value: String(todos.value.length), note: '按真实截止时间排序' },
  {
    label: '我的商品',
    value: String(listings.value.length),
    note: `${listings.value.filter((item) => ['AUCTION_LIVE', 'SECKILL', 'SHOP'].includes(item.status)).length} 件正在流转`,
  },
  { label: '待付款订单', value: String(pendingOrderCount.value), note: '包含拍卖和第二顺位订单' },
])

async function load(): Promise<void> {
  loading.value = true
  error.value = ''
  try {
    const [walletResult, todoResult, orderPage, listingPage, auctionPage] = await Promise.all([
      apiRequest<WalletVO>('/wallet'),
      apiRequest<TodoItemVO[]>('/users/me/todos'),
      apiRequest<CursorPage<OrderSummaryVO>>('/orders?limit=50'),
      apiRequest<CursorPage<ListingVO>>('/listings/mine?limit=50'),
      apiRequest<CursorPage<AuctionVO>>('/auctions?limit=50'),
    ])
    wallet.value = walletResult
    todos.value = todoResult
    orders.value = orderPage.items
    listings.value = listingPage.items
    const listingById = new Map(listingPage.items.map((listing) => [listing.id, listing]))
    const auctionByListingId = new Map(auctionPage.items
      .filter((auction) => auction.sellerId === auth.user?.id)
      .map((auction) => [auction.listingId, auction]))
    activeLots.value = listingPage.items
      .filter((listing) => listing.status === 'AUCTION_LIVE')
      .flatMap((listing, index) => {
        const resolved = listingById.get(listing.id)
        return resolved ? [listingToMarketLot(resolved, auctionByListingId.get(listing.id), index)] : []
      })
      .slice(0, 3)
  } catch (cause) {
    error.value = errorMessage(cause)
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="workspace-page" v-loading="loading">
    <PageHeading
      eyebrow="我的工作台"
      :title="`${auth.displayName}，欢迎回来`"
      description="订单、商品、钱包和流拍处置都按当前账号的真实状态汇总。"
    >
      <template #actions><RouterLink class="button button--dark" to="/workspace/publish">发起拍卖</RouterLink></template>
    </PageHeading>

    <section class="metric-strip metric-strip--workspace">
      <div v-for="metric in metrics" :key="metric.label" class="metric-strip__item">
        <span>{{ metric.label }}</span><strong>{{ metric.value }}</strong><small>{{ metric.note }}</small>
      </div>
    </section>

    <div class="workspace-overview-grid">
      <section class="panel task-panel">
        <div class="panel-head">
          <div><h3>需要处理</h3><p>按截止时间排序，不会混入已结束事项</p></div>
          <StatusPill :text="todos.length ? `${todos.length} 项待办` : '暂无待办'" :tone="todos.length ? 'warning' : 'success'" />
        </div>
        <div v-if="todos.length" class="todo-list">
          <RouterLink v-for="todo in todos.slice(0, 6)" :key="`${todo.type}-${todo.resourceId}`" :to="todo.route" class="todo-row">
            <span class="todo-row__icon"><ListChecks :size="16" /></span>
            <div><b>{{ todo.title }}</b><span>{{ todo.description }}</span><small><Clock3 :size="12" />{{ todo.dueAt ? formatDateTime(todo.dueAt) : '无截止时间' }}</small></div>
            <StatusPill :text="todoTypeLabels[todo.type] || '待处理'" tone="warning" />
            <ArrowRight :size="15" />
          </RouterLink>
        </div>
        <EmptyState v-else title="当前没有需要处理的事项" description="新的付款、发货、流拍处置或商品修改要求会按截止时间出现在这里。" />
      </section>

      <section class="panel ai-workspace-card">
        <div class="panel-head"><h3><Bot :size="16" /> AI 经营辅助</h3><StatusPill text="只建议" tone="warning" /></div>
        <p>经营分析会读取商品、出价和流拍事实，给出可解释建议；发布、改价和成交仍由你与平台规则决定。</p>
        <RouterLink class="button button--accent" to="/workspace/listings">选择商品查看建议</RouterLink>
        <RouterLink class="text-link" to="/assistant">询问平台规则 <ArrowRight :size="14" /></RouterLink>
        <div class="ai-boundary-mini"><Sparkles :size="13" /> AI 不执行发布、出价、付款、退款或最终审核。</div>
      </section>
    </div>

    <div class="section-heading">
      <div><h2>正在拍卖</h2><p>价格和结束时间来自当前拍卖状态。</p></div>
      <RouterLink class="text-link" to="/workspace/listings">查看全部 <ArrowRight :size="14" /></RouterLink>
    </div>
    <div v-if="activeLots.length" class="lot-grid lot-grid--three">
      <LotCard v-for="lot in activeLots" :key="lot.id" :lot="lot" compact />
    </div>
    <EmptyState
      v-else
      :title="error ? '暂时无法读取工作台数据' : '当前没有你的在售拍卖'"
      :description="error || '商品进入拍卖后，会在这里展示实时价格与结束时间。'"
      :action-text="error ? '重新加载' : ''"
      @action="load"
    />

    <div class="workspace-foot-cards">
      <RouterLink class="panel quick-link" to="/wallet"><WalletCards :size="19" /><div><b>钱包与流水</b><span>查看可用、冻结和每笔资金变化</span></div><ArrowRight :size="16" /></RouterLink>
      <RouterLink class="panel quick-link" to="/workspace/listings"><PackageOpen :size="19" /><div><b>完整拍品生命周期</b><span>草稿、审核、渠道与结束记录集中查看</span></div><ArrowRight :size="16" /></RouterLink>
    </div>
  </div>
</template>

<style scoped>
.todo-list { display: grid; }
.todo-row { display: grid; grid-template-columns: auto minmax(0, 1fr) auto auto; gap: 12px; align-items: center; padding: 14px 2px; border-top: 1px solid var(--line); }
.todo-row__icon { display: grid; width: 34px; height: 34px; place-items: center; border-radius: 4px; background: var(--amber-soft); color: var(--amber); }
.todo-row div { display: grid; min-width: 0; gap: 3px; }
.todo-row b, .todo-row span, .todo-row small { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.todo-row span { color: var(--muted); font-size: 13px; }
.todo-row small { display: inline-flex; gap: 5px; align-items: center; color: var(--muted); font-size: 12px; }
.ai-workspace-card .text-link { margin-top: 14px; }
@media (max-width: 760px) { .todo-row { grid-template-columns: auto minmax(0, 1fr) auto; } .todo-row .status-pill { display: none; } }
</style>
