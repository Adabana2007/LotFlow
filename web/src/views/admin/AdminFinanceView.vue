<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { CheckCircle2, Search, ShieldCheck, TriangleAlert, WalletCards } from 'lucide-vue-next'
import PageHeading from '@/components/PageHeading.vue'
import StatusPill from '@/components/StatusPill.vue'
import { apiRequest, errorMessage } from '@/api/client'
import type { CursorPage, WalletReconciliationVO, WalletTransactionVO } from '@/types/api'
import { formatFullDate, formatMoney } from '@/utils/format'

const loading = ref(true)
const loadError = ref('')
const rows = ref<WalletTransactionVO[]>([])
const reconciliations = ref<WalletReconciliationVO[]>([])
const search = ref('')
const typeFilter = ref('全部类型')
const types = ['全部类型', '冻结', '扣款', '退款', '结算', '充值']

function typeLabel(type: string): string {
  if (type.endsWith('_RECHARGE')) return '充值'
  if (type.includes('FREEZE')) return '冻结'
  if (type.includes('REFUND')) return '退款'
  if (type.includes('SETTLE')) return '结算'
  if (type.includes('PAYMENT')) return '扣款'
  return type
}

const visible = computed(() => rows.value.filter((row) => {
  const matchesSearch = `${row.id}${row.businessKey}${row.transactionType}`.toLowerCase().includes(search.value.trim().toLowerCase())
  return matchesSearch && (typeFilter.value === '全部类型' || typeLabel(row.transactionType) === typeFilter.value)
}))
const inconsistent = computed(() => reconciliations.value.filter((row) => !row.consistent))
const debitCount = computed(() => rows.value.filter((row) => row.availableChange.startsWith('-') || row.frozenChange.startsWith('-')).length)
const refundCount = computed(() => rows.value.filter((row) => row.transactionType.includes('REFUND')).length)

async function load(): Promise<void> {
  loading.value = true
  loadError.value = ''
  try {
    const [transactions, accounts] = await Promise.all([
      apiRequest<CursorPage<WalletTransactionVO>>('/admin/wallet-transactions?limit=100'),
      apiRequest<WalletReconciliationVO[]>('/admin/finance/reconciliation?limit=100'),
    ])
    rows.value = transactions.items
    reconciliations.value = accounts
  } catch (error) {
    rows.value = []
    reconciliations.value = []
    loadError.value = errorMessage(error)
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="admin-page" v-loading="loading">
    <PageHeading eyebrow="FINANCIAL AUDIT" title="资金审计与对账" description="核对账户余额、冻结金额和最后一笔不可变流水；管理员只能查询，不能直接改写余额或历史流水。">
      <template #actions><StatusPill :text="inconsistent.length ? `${inconsistent.length} 个账户有差异` : '对账一致'" :tone="inconsistent.length ? 'danger' : 'success'" /></template>
    </PageHeading>
    <div class="admin-metric-grid admin-metric-grid--small"><div class="panel admin-metric"><span>资金流水</span><strong>{{ rows.length }}</strong><p>当前加载结果</p></div><div class="panel admin-metric"><span>扣减流水</span><strong>{{ debitCount }}</strong><p>可用或冻结余额减少</p></div><div class="panel admin-metric"><span>退款流水</span><strong>{{ refundCount }}</strong><p>当前加载结果</p></div><div class="panel admin-metric"><span>对账异常</span><strong :class="inconsistent.length ? 'red-text' : 'green-text'">{{ inconsistent.length }}</strong><p>{{ reconciliations.length }} 个账户参与核对</p></div></div>
    <section class="panel"><div class="panel-head"><div><h3>账户余额与流水快照对账</h3><p>逐账户比较当前余额与最后一笔流水的期末余额，任何差异都需要人工核查。</p></div><WalletCards :size="18" /></div><div class="table-panel reconciliation-table"><table class="data-table admin-table"><thead><tr><th>账户</th><th>归属</th><th>可用余额</th><th>流水可用余额</th><th>冻结余额</th><th>流水冻结余额</th><th>差异</th><th>结果</th></tr></thead><tbody><tr v-for="row in reconciliations" :key="row.accountId" :class="{ 'table-row--danger': !row.consistent }"><td class="mono">{{ row.accountCode }}</td><td><b>{{ row.ownerType }}</b><span class="cell-note">{{ row.ownerId || row.accountId }}</span></td><td class="money-cell">{{ formatMoney(row.availableBalance) }}</td><td class="money-cell">{{ formatMoney(row.ledgerAvailableBalance) }}</td><td class="money-cell">{{ formatMoney(row.frozenBalance) }}</td><td class="money-cell">{{ formatMoney(row.ledgerFrozenBalance) }}</td><td class="money-cell">{{ formatMoney(row.difference) }}</td><td><StatusPill :text="row.consistent ? '一致' : '需要核查'" :tone="row.consistent ? 'success' : 'danger'" /></td></tr></tbody></table><div v-if="!reconciliations.length" class="empty-state"><CheckCircle2 :size="22" /><b>{{ loadError ? '对账数据读取失败' : '暂无账户数据' }}</b><p>{{ loadError || '钱包账户创建后会出现在这里。' }}</p></div></div></section>
    <div class="toolbar panel"><div class="filter-chips"><button v-for="type in types" :key="type" class="filter-chip" :class="{ active: typeFilter === type }" type="button" @click="typeFilter = type">{{ type }}</button></div><label class="inline-search"><Search :size="14" /><input v-model="search" placeholder="搜索流水号或业务编号" /></label></div>
    <div class="table-panel">
      <table class="data-table admin-table finance-table"><thead><tr><th>流水号</th><th>类型</th><th>业务编号</th><th>可用变化</th><th>冻结变化</th><th>可用后</th><th>冻结后</th><th>时间</th></tr></thead><tbody><tr v-for="row in visible" :key="row.id"><td class="mono">{{ row.id }}</td><td><StatusPill :text="typeLabel(row.transactionType)" :tone="row.availableChange.startsWith('-') ? 'danger' : 'success'" /></td><td class="mono business-key">{{ row.businessKey }}</td><td class="money-cell" :class="{ 'red-text': row.availableChange.startsWith('-'), 'green-text': !row.availableChange.startsWith('-') }">{{ formatMoney(row.availableChange) }}</td><td class="money-cell">{{ formatMoney(row.frozenChange) }}</td><td class="money-cell">{{ formatMoney(row.availableAfter) }}</td><td class="money-cell">{{ formatMoney(row.frozenAfter) }}</td><td>{{ formatFullDate(row.createdAt) }}</td></tr></tbody></table>
      <div v-if="!visible.length" class="empty-state"><WalletCards :size="23" /><b>{{ loadError ? '资金流水读取失败' : '没有匹配流水' }}</b><p>{{ loadError || '调整类型或搜索条件后再试。' }}</p><button v-if="loadError" class="button button--outline" type="button" @click="load">重新加载</button></div>
    </div>
    <div class="finance-audit-strip"><ShieldCheck :size="18" /><div><b>审计原则</b><p>余额只能按资金规则变化；管理员可查、可核对，但不能直接编辑账户余额。</p></div><template v-if="inconsistent.length"><TriangleAlert :size="18" /><span>存在余额与流水不一致的账户，请逐笔核查。</span></template><template v-else><CheckCircle2 :size="18" /><span>当前加载账户的余额与流水快照一致。</span></template></div>
  </div>
</template>

<style scoped>
.reconciliation-table { border: 0; border-top: 1px solid var(--line); border-radius: 0; }
</style>
