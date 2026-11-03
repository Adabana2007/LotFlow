<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import { AlertTriangle, PackageSearch, Search } from 'lucide-vue-next'
import PageHeading from '@/components/PageHeading.vue'
import StatusPill from '@/components/StatusPill.vue'
import CountdownText from '@/components/CountdownText.vue'
import { apiRequest, errorMessage } from '@/api/client'
import type { CursorPage, OrderVO } from '@/types/api'
import { formatMoney, sourceText, statusText } from '@/utils/format'

const loading = ref(true)
const loadError = ref('')
const rows = ref<OrderVO[]>([])
const search = ref('')
const stateFilter = ref('全部状态')
const states = ['全部状态', '待付款', '已付款', '已发货', '争议中', '已完成']
const visible = computed(() => rows.value.filter((row) => `${row.orderNo}${row.listingId}${row.id}`.toLowerCase().includes(search.value.trim().toLowerCase()) && (stateFilter.value === '全部状态' || statusText(row.status) === stateFilter.value)))
const pendingCount = computed(() => rows.value.filter((row) => ['PENDING_PAYMENT', 'SECOND_CHANCE'].includes(row.status)).length)
const disputedCount = computed(() => rows.value.filter((row) => row.status === 'DISPUTED').length)
const completedCount = computed(() => rows.value.filter((row) => row.status === 'COMPLETED').length)

async function load(): Promise<void> {
  loading.value = true
  loadError.value = ''
  try {
    const page = await apiRequest<CursorPage<OrderVO>>('/admin/orders?limit=50')
    rows.value = page.items
  } catch (error) {
    rows.value = []
    loadError.value = errorMessage(error)
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="admin-page" v-loading="loading">
    <PageHeading eyebrow="ORDER OPERATIONS" title="订单运营" description="查看支付、履约和争议状态；超时库存进入单独的诊断与补偿页面。">
      <template #actions><RouterLink class="button button--outline button--with-icon" to="/admin/inventory"><AlertTriangle :size="14" />库存释放诊断</RouterLink></template>
    </PageHeading>
    <div class="admin-metric-grid admin-metric-grid--small"><div class="panel admin-metric"><span>订单总数</span><strong>{{ rows.length }}</strong><p>当前列表</p></div><div class="panel admin-metric"><span>待付款</span><strong>{{ pendingCount }}</strong><p>可能仍锁定库存</p></div><div class="panel admin-metric"><span>争议中</span><strong class="red-text">{{ disputedCount }}</strong><p>需人工核对</p></div><div class="panel admin-metric"><span>已完成</span><strong>{{ completedCount }}</strong><p>当前列表</p></div></div>
    <div class="toolbar panel"><div class="filter-chips"><button v-for="state in states" :key="state" class="filter-chip" :class="{ active: stateFilter === state }" type="button" @click="stateFilter = state">{{ state }}</button></div><label class="inline-search"><Search :size="14" /><input v-model="search" placeholder="搜索订单号、商品或订单编号" /></label></div>
    <div class="table-panel">
      <table class="data-table admin-table"><thead><tr><th>订单号</th><th>商品</th><th>渠道</th><th>金额</th><th>状态</th><th>付款截止</th><th>诊断</th></tr></thead><tbody>
        <tr v-for="row in visible" :key="row.id" :class="{ 'table-row--danger': row.status === 'DISPUTED' }"><td class="mono">{{ row.orderNo }}</td><td><b>商品编号 {{ row.listingId }}</b><span class="cell-note">订单编号 {{ row.id }}</span></td><td><StatusPill :text="sourceText(row.sourceType)" :tone="row.sourceType === 'SECKILL' ? 'seckill' : row.sourceType === 'SHOP' ? 'shop' : 'auction'" /></td><td class="money-cell">{{ formatMoney(row.amount) }}</td><td><StatusPill :text="statusText(row.status)" :tone="row.status === 'DISPUTED' ? 'danger' : ['PENDING_PAYMENT', 'SECOND_CHANCE'].includes(row.status) ? 'warning' : 'success'" /></td><td><CountdownText v-if="row.paymentDeadline" :deadline="row.paymentDeadline" /><span v-else class="muted-note">不适用</span></td><td><RouterLink class="diagnostic-note" to="/admin/inventory"><AlertTriangle :size="12" /> 查看库存诊断</RouterLink></td></tr>
      </tbody></table>
      <div v-if="!visible.length" class="empty-state"><PackageSearch :size="23" /><b>{{ loadError ? '订单读取失败' : '没有匹配订单' }}</b><p>{{ loadError || '调整状态或搜索条件后再试。' }}</p><button v-if="loadError" class="button button--outline" type="button" @click="load">重新加载</button></div>
    </div>
  </div>
</template>
