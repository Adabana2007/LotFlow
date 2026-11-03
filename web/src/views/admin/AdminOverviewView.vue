<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import {
  ArrowRight,
  ClipboardCheck,
  MessageSquareWarning,
  ShieldAlert,
  Store,
  TrendingUp,
  UsersRound,
  WalletCards,
  Zap,
} from 'lucide-vue-next'
import PageHeading from '@/components/PageHeading.vue'
import StatusPill from '@/components/StatusPill.vue'
import { apiRequest, errorMessage } from '@/api/client'
import type {
  AdminReportsVO,
  AdminUserVO,
  CursorPage,
  OrderVO,
  ReviewTaskVO,
  RiskEventVO,
} from '@/types/api'
import { formatMoney, statusText } from '@/utils/format'

const loading = ref(true)
const loadError = ref('')
const reports = ref<AdminReportsVO | null>(null)
const reviews = ref<ReviewTaskVO[]>([])
const orders = ref<OrderVO[]>([])
const risks = ref<RiskEventVO[]>([])
const users = ref<AdminUserVO[]>([])

function cents(value: string): bigint {
  if (!/^-?\d+(\.\d{1,2})?$/.test(value || '0')) return 0n
  const negative = value.startsWith('-')
  const normalized = negative ? value.slice(1) : value
  const [whole = '0', fraction = ''] = normalized.split('.')
  const amount = BigInt(whole) * 100n + BigInt((fraction + '00').slice(0, 2))
  return negative ? -amount : amount
}

const pendingReviews = computed(() => reviews.value.filter((task) => task.status !== 'COMPLETED').length)
const pendingOrders = computed(() => orders.value.filter((order) => ['PENDING_PAYMENT', 'SECOND_CHANCE'].includes(order.status)).length)
const riskReviews = computed(() => risks.value.filter((event) => event.requiresHumanReview).length)
const metrics = computed(() => {
  const summary = reports.value?.summary
  return [
    { label: '已完成成交额', value: formatMoney(summary?.completedGmv || '0.00'), note: '已完成订单的真实汇总', icon: TrendingUp, tone: 'green' },
    { label: '平台担保余额', value: formatMoney(summary?.escrowBalance || '0.00'), note: '等待履约或争议结束的货款', icon: WalletCards, tone: '' },
    { label: '待付款订单', value: String(summary?.pendingPaymentOrders ?? pendingOrders.value), note: '含第二高顺位付款窗口', icon: ArrowRight, tone: 'amber' },
    { label: '争议订单', value: String(summary?.disputedOrders ?? orders.value.filter((order) => order.status === 'DISPUTED').length), note: '需人工核对证据与资金', icon: ShieldAlert, tone: 'red' },
  ]
})
const trendRows = computed(() => (reports.value?.trend || []).slice(-14))
const chartPoints = computed(() => {
  const rows = trendRows.value
  if (!rows.length) return ''
  const values = rows.map((row) => cents(row.completedAmount))
  const max = values.reduce((current, value) => value > current ? value : current, 0n)
  return values.map((value, index) => {
    const x = rows.length === 1 ? 50 : (index / (rows.length - 1)) * 100
    const height = max === 0n ? 0 : Number(value * 82n / max)
    return `${x.toFixed(2)},${(92 - height).toFixed(2)}`
  }).join(' ')
})
const totalTrendAmount = computed(() => trendRows.value.reduce((sum, row) => sum + cents(row.completedAmount), 0n))
const totalTrendOrders = computed(() => trendRows.value.reduce((sum, row) => sum + row.completedOrders, 0))
const channelMax = computed(() => Math.max(1, ...(reports.value?.channels || []).map((channel) => channel.activeCount + channel.soldCount + channel.scheduledCount)))

function channelLabel(channel: string): string {
  return ({ AUCTION: '拍卖', SECKILL: '秒杀', SHOP: '商城' } as Record<string, string>)[channel] || channel
}

function barWidth(value: number): string {
  return `${Math.max(3, Math.round((value / channelMax.value) * 100))}%`
}

async function load(): Promise<void> {
  loading.value = true
  loadError.value = ''
  const results = await Promise.allSettled([
    apiRequest<AdminReportsVO>('/admin/reports?days=30'),
    apiRequest<CursorPage<ReviewTaskVO>>('/admin/review-tasks?limit=100'),
    apiRequest<CursorPage<OrderVO>>('/admin/orders?limit=100'),
    apiRequest<CursorPage<RiskEventVO>>('/admin/risk-events?limit=100'),
    apiRequest<CursorPage<AdminUserVO>>('/admin/users?limit=100'),
  ])
  reports.value = results[0].status === 'fulfilled' ? results[0].value : null
  reviews.value = results[1].status === 'fulfilled' ? results[1].value.items : []
  orders.value = results[2].status === 'fulfilled' ? results[2].value.items : []
  risks.value = results[3].status === 'fulfilled' ? results[3].value.items : []
  users.value = results[4].status === 'fulfilled' ? results[4].value.items : []
  const failure = results.find((result) => result.status === 'rejected')
  if (failure?.status === 'rejected') loadError.value = errorMessage(failure.reason)
  loading.value = false
}

onMounted(load)
</script>

<template>
  <div class="admin-page" v-loading="loading">
    <PageHeading eyebrow="OPERATIONS" title="运营总览" description="从真实订单、渠道和治理队列汇总平台运行状态；AI 指标仅用于辅助，不进入成交与资金决定。">
      <template #actions><StatusPill :text="loadError ? '部分数据不可用' : '真实数据汇总'" :tone="loadError ? 'warning' : 'success'" /></template>
    </PageHeading>
    <section class="admin-metric-grid"><div v-for="metric in metrics" :key="metric.label" class="panel admin-metric"><div><span>{{ metric.label }}</span><component :is="metric.icon" :size="16" /></div><strong :class="{ 'green-text': metric.tone === 'green', 'red-text': metric.tone === 'red' }">{{ metric.value }}</strong><p>{{ metric.note }}</p></div></section>
    <div class="admin-overview-grid">
      <section class="panel trend-panel">
        <div class="panel-head"><div><h3>近期成交趋势</h3><p>最近 {{ trendRows.length }} 个自然日 · {{ totalTrendOrders }} 笔 · {{ formatMoney((Number(totalTrendAmount) / 100).toFixed(2)) }}</p></div><TrendingUp :size="18" /></div>
        <div v-if="trendRows.length" class="trend-chart overview-trend-chart"><div class="chart-grid-lines"><i /><i /><i /><i /></div><svg viewBox="0 0 100 100" preserveAspectRatio="none" aria-label="近期成交金额趋势"><polyline :points="chartPoints" fill="none" stroke="var(--jade)" stroke-width="1.5" vector-effect="non-scaling-stroke" /></svg><div class="chart-axis"><span v-for="row in [trendRows[0], trendRows.at(-1)]" :key="row?.day">{{ row?.day?.slice(5) }}</span></div></div>
        <div v-else class="empty-state"><TrendingUp :size="23" /><b>暂无已完成订单</b><p>完成订单后，这里会显示真实成交趋势。</p></div>
      </section>
      <aside class="panel attention-panel"><div class="panel-head"><h3>需要关注</h3><StatusPill :text="String(pendingReviews + riskReviews + (reports?.summary.disputedOrders || 0))" tone="danger" /></div><div class="attention-item"><ClipboardCheck :size="17" /><div><b>待人工审核</b><span>{{ pendingReviews }} 个任务需要处理</span></div><RouterLink to="/admin/reviews">处理</RouterLink></div><div class="attention-item"><MessageSquareWarning :size="17" /><div><b>聊天风险待复核</b><span>{{ riskReviews }} 个事件需要人工判断</span></div><RouterLink to="/admin/risk">查看</RouterLink></div><div class="attention-item"><ShieldAlert :size="17" /><div><b>争议待处理</b><span>{{ reports?.summary.disputedOrders || 0 }} 笔订单暂停结算</span></div><RouterLink to="/admin/disputes">查看</RouterLink></div><div class="attention-item"><WalletCards :size="17" /><div><b>库存超时诊断</b><span>检查应释放但尚未释放的库存</span></div><RouterLink to="/admin/inventory">诊断</RouterLink></div></aside>
    </div>
    <div class="admin-overview-grid admin-overview-grid--lower">
      <section class="panel channel-status-panel"><div class="panel-head"><div><h3>渠道库存与降级</h3><p>拍卖流拍后进入秒杀，再进入商城的真实渠道记录。</p></div><StatusPill :text="`降级率 ${reports?.summary.downgradeRate || '0'}%`" tone="warning" /></div><div class="channel-overview-grid"><div v-for="channel in reports?.channels" :key="channel.channel" class="channel-overview-card"><div><component :is="channel.channel === 'AUCTION' ? TrendingUp : channel.channel === 'SECKILL' ? Zap : Store" :size="16" /><b>{{ channelLabel(channel.channel) }}</b></div><strong>{{ channel.activeCount }}</strong><span>在售 / 待开始 {{ channel.scheduledCount }}</span><div class="channel-bar"><i :style="{ width: barWidth(channel.activeCount + channel.soldCount + channel.scheduledCount) }" /></div><p>成交 {{ channel.soldCount }} · 流拍 {{ channel.unsoldCount }}</p></div></div><div class="funnel-strip"><div><span>拍卖中</span><b>{{ reports?.summary.activeAuctions || 0 }}</b></div><i>→</i><div><span>秒杀中</span><b>{{ reports?.summary.activeSeckills || 0 }}</b></div><i>→</i><div><span>商城中</span><b>{{ reports?.summary.activeShops || 0 }}</b></div><i>→</i><div><span>流拍待处置</span><b>{{ reports?.summary.unsoldAuctions || 0 }}</b></div></div></section>
      <aside class="panel human-ops-panel"><div class="panel-head"><div><h3>平台运行事实</h3><p>只展示可核对的数量和状态。</p></div><UsersRound :size="18" /></div><div class="rule-line"><b>订单总量</b><span>{{ reports?.summary.totalOrders || 0 }} 笔</span></div><div class="rule-line"><b>当前风险事件</b><span>{{ risks.length }} 条</span></div><div class="rule-line"><b>当前用户样本</b><span>{{ users.length }} 个</span></div><div class="rule-line"><b>待付款状态</b><span>{{ orders.filter((order) => statusText(order.status) === '待付款').length }} 笔</span></div><div v-if="loadError" class="warm-note"><ShieldAlert :size="16" /><p>{{ loadError }}</p></div></aside>
    </div>
  </div>
</template>

<style scoped>
.overview-trend-chart svg polyline { stroke-linecap: round; stroke-linejoin: round; }
.channel-overview-grid { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 9px; padding: 14px; }
.channel-overview-card { padding: 12px; border: 1px solid var(--line); border-radius: 4px; background: #f7f8f3; }
.channel-overview-card > div:first-child { display: flex; align-items: center; gap: 7px; color: var(--jade); }
.channel-overview-card b { color: var(--ink); font-size: 14px; }
.channel-overview-card strong { display: block; margin-top: 9px; font-family: var(--font-display); font-size: 26px; font-weight: 500; }
.channel-overview-card span, .channel-overview-card p { color: var(--muted); font-size: 12px; }
.channel-bar { height: 6px; margin: 8px 0 5px; overflow: hidden; border-radius: 2px; background: #e4e7df; }
.channel-bar i { display: block; height: 100%; border-radius: 2px; background: var(--jade); }
.funnel-strip { display: grid; grid-template-columns: repeat(7, auto); align-items: center; gap: 8px; padding: 10px 16px 16px; }
.funnel-strip > div { flex: 1; min-width: 0; padding: 9px 10px; border: 1px solid var(--line); border-radius: 3px; background: #fff; }
.funnel-strip span { display: block; color: var(--muted); font-size: 12px; }
.funnel-strip b { display: block; margin-top: 3px; font-size: 18px; }
.funnel-strip > i { color: var(--muted-light); font-style: normal; }
@media (max-width: 900px) { .channel-overview-grid { grid-template-columns: 1fr; } .funnel-strip { grid-template-columns: 1fr; } .funnel-strip > i { display: none; } }
</style>
