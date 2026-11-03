<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { AlertTriangle, PackageCheck, RefreshCw, Search, TimerReset } from 'lucide-vue-next'
import PageHeading from '@/components/PageHeading.vue'
import StatusPill from '@/components/StatusPill.vue'
import { apiRequest, errorMessage } from '@/api/client'
import type { InventoryDiagnosticVO } from '@/types/api'
import { formatFullDate, sourceText, statusText } from '@/utils/format'

const loading = ref(true)
const releasingId = ref('')
const loadError = ref('')
const rows = ref<InventoryDiagnosticVO[]>([])
const search = ref('')
const visible = computed(() => rows.value.filter((row) => `${row.orderNo}${row.listingTitle}${row.listingId}`.toLowerCase().includes(search.value.trim().toLowerCase())))
const maxOverdue = computed(() => rows.value.reduce((max, row) => Math.max(max, row.overdueSeconds), 0))

function overdueText(seconds: number): string {
  if (seconds < 60) return `${seconds} 秒`
  if (seconds < 3600) return `${Math.floor(seconds / 60)} 分钟`
  if (seconds < 86400) return `${Math.floor(seconds / 3600)} 小时 ${Math.floor((seconds % 3600) / 60)} 分钟`
  return `${Math.floor(seconds / 86400)} 天 ${Math.floor((seconds % 86400) / 3600)} 小时`
}

async function load(): Promise<void> {
  loading.value = true
  loadError.value = ''
  try {
    rows.value = await apiRequest<InventoryDiagnosticVO[]>('/admin/inventory-diagnostics?limit=100')
  } catch (error) {
    rows.value = []
    loadError.value = errorMessage(error)
  } finally {
    loading.value = false
  }
}

async function release(row: InventoryDiagnosticVO): Promise<void> {
  if (releasingId.value) return
  try {
    await ElMessageBox.confirm(
      `将对订单 ${row.orderNo} 重放统一的超时付款处理。库存、渠道和资金状态仍由订单状态机校验。`,
      '确认执行库存释放',
      { confirmButtonText: '执行补偿', cancelButtonText: '取消', type: 'warning' },
    )
  } catch {
    return
  }
  releasingId.value = row.orderId
  try {
    await apiRequest<void>(`/admin/inventory-diagnostics/${row.orderId}/releases`, { method: 'POST' })
    ElMessage.success('库存释放补偿已执行')
    await load()
  } catch (error) {
    ElMessage.error(errorMessage(error))
  } finally {
    releasingId.value = ''
  }
}

onMounted(load)
</script>

<template>
  <div class="admin-page" v-loading="loading">
    <PageHeading eyebrow="INVENTORY DIAGNOSTICS" title="库存释放诊断" description="只列出已经超过付款时限但仍占用库存的订单；补偿操作复用统一订单服务，不直接修改库存表。">
      <template #actions><button class="button button--outline button--with-icon" type="button" :disabled="loading" @click="load"><RefreshCw :size="14" />刷新诊断</button></template>
    </PageHeading>
    <div class="admin-metric-grid admin-metric-grid--small"><div class="panel admin-metric"><span>待释放订单</span><strong :class="{ 'red-text': rows.length }">{{ rows.length }}</strong><p>超过付款截止且仍占用库存</p></div><div class="panel admin-metric"><span>最长超时</span><strong>{{ overdueText(maxOverdue) }}</strong><p>当前加载结果</p></div><div class="panel admin-metric"><span>涉及秒杀</span><strong>{{ rows.filter((row) => row.sourceType === 'SECKILL').length }}</strong><p>Redis 预占与订单状态联合核对</p></div><div class="panel admin-metric"><span>人工新增库存</span><strong class="green-text">0</strong><p>释放只恢复既有库存单位</p></div></div>
    <div class="toolbar panel"><label class="inline-search"><Search :size="14" /><input v-model="search" placeholder="搜索订单号、商品或订单编号" /></label></div>
    <div class="table-panel"><table class="data-table admin-table"><thead><tr><th>订单</th><th>商品</th><th>渠道</th><th>订单状态</th><th>预占状态</th><th>截止时间</th><th>已超时</th><th>操作</th></tr></thead><tbody><tr v-for="row in visible" :key="row.orderId"><td><b>{{ row.orderNo }}</b><span class="cell-note">订单 {{ row.orderId }}</span></td><td><b>{{ row.listingTitle }}</b><span class="cell-note">商品 {{ row.listingId }}</span></td><td><StatusPill :text="sourceText(row.sourceType)" :tone="row.sourceType === 'SECKILL' ? 'seckill' : 'auction'" /></td><td>{{ statusText(row.orderStatus) }}</td><td>{{ row.reservationStatus ? statusText(row.reservationStatus) : '付款窗口' }}</td><td>{{ formatFullDate(row.deadline) }}</td><td><span class="overdue-cell"><AlertTriangle :size="13" />{{ overdueText(row.overdueSeconds) }}</span></td><td><button class="button button--small button--outline" type="button" :disabled="Boolean(releasingId)" @click="release(row)">{{ releasingId === row.orderId ? '正在执行…' : '重试释放' }}</button></td></tr></tbody></table><div v-if="!visible.length" class="empty-state"><PackageCheck :size="24" /><b>{{ loadError ? '库存诊断读取失败' : '没有待释放库存' }}</b><p>{{ loadError || '所有已加载订单都处于允许的库存状态。' }}</p><button v-if="loadError" class="button button--outline" type="button" @click="load">重新加载</button></div></div>
    <div class="warm-note"><TimerReset :size="16" /><p>补偿按钮只会重放既有超时命令。若订单状态、渠道或资金状态已变化，统一订单状态机会拒绝重复处理。</p></div>
  </div>
</template>

<style scoped>
.overdue-cell { display: inline-flex; align-items: center; gap: 5px; color: var(--accent-dark); font-size: 12px; }
</style>
