<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { BadgeCheck, Plus, ShieldCheck, WalletCards } from 'lucide-vue-next'
import PageHeading from '@/components/PageHeading.vue'
import StatusPill from '@/components/StatusPill.vue'
import { apiRequest, createIdempotencyKey, errorMessage } from '@/api/client'
import type { WalletTransactionVO, WalletVO } from '@/types/api'
import { formatFullDate, formatMoney } from '@/utils/format'

const loading = ref(true)
const loadError = ref('')
const rechargeVisible = ref(false)
const recharging = ref(false)
const wallet = ref<WalletVO | null>(null)
const transactions = ref<WalletTransactionVO[]>([])
const rechargeForm = reactive({ amount: '' })
const filter = ref('全部流水')
const filters = ['全部流水', '充值', '冻结', '扣款', '退款', '结算']

function typeLabel(type: string): string {
  const labels: Record<string, string> = {
    DEPOSIT_FREEZE: '冻结',
    PAYMENT: '扣款',
    REFUND: '退款',
    RECHARGE: '充值',
    SETTLEMENT: '结算',
  }
  return labels[type] || type
}

function typeTone(type: string) {
  if (type === 'REFUND' || type.endsWith('_RECHARGE') || type === 'SETTLEMENT') return 'success' as const
  if (type === 'PAYMENT') return 'danger' as const
  return 'warning' as const
}

const visibleTransactions = computed(() => transactions.value.filter((item) => filter.value === '全部流水' || typeLabel(item.transactionType) === filter.value))
const balanceValue = computed(() => loading.value ? '读取中' : loadError.value ? '暂不可用' : formatMoney(wallet.value?.availableBalance))
const frozenValue = computed(() => loading.value ? '读取中' : loadError.value ? '暂不可用' : formatMoney(wallet.value?.frozenBalance))
const metrics = computed(() => [
  { label: '可用余额', value: balanceValue.value, note: '可立即用于订单和保证金' },
  { label: '冻结总额', value: frozenValue.value, note: '出价保证金与待结算金额' },
  { label: '流水笔数', value: String(transactions.value.length), note: '最近 50 条资金记录' },
  { label: '记录类型', value: String(new Set(transactions.value.map((item) => item.transactionType)).size), note: '当前可见的资金记录分类' },
])

async function load(): Promise<void> {
  loading.value = true
  loadError.value = ''
  try {
    const [balance, ledger] = await Promise.all([
      apiRequest<WalletVO>('/wallet'),
      apiRequest<WalletTransactionVO[]>('/wallet/transactions?limit=50'),
    ])
    wallet.value = balance
    transactions.value = ledger
  } catch (error) {
    wallet.value = null
    transactions.value = []
    loadError.value = errorMessage(error)
  } finally {
    loading.value = false
  }
}

async function recharge(): Promise<void> {
  if (recharging.value || !/^\d+(\.\d{1,2})?$/.test(rechargeForm.amount) || Number(rechargeForm.amount) <= 0) {
    ElMessage.warning('请输入大于 0 且最多两位小数的金额')
    return
  }
  recharging.value = true
  try {
    await apiRequest<WalletVO>('/wallet/recharges', {
      method: 'POST',
      headers: { 'Idempotency-Key': createIdempotencyKey('wallet-recharge') },
      body: JSON.stringify({ amount: rechargeForm.amount }),
    })
    rechargeVisible.value = false
    ElMessage.success('充值已到账')
    await load()
  } catch (error) {
    ElMessage.error(errorMessage(error))
  } finally {
    recharging.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="workspace-page" v-loading="loading">
    <PageHeading eyebrow="虚拟钱包" title="钱包" description="查看可用余额、冻结金额和每一笔资金变化；历史记录不可修改。">
      <template #actions><button class="button button--dark button--with-icon" type="button" @click="rechargeVisible = true"><Plus :size="15" /> 充值</button></template>
    </PageHeading>
    <template v-if="wallet">
      <section class="metric-strip metric-strip--workspace"><div v-for="metric in metrics" :key="metric.label" class="metric-strip__item"><span>{{ metric.label }}</span><strong>{{ metric.value }}</strong><small>{{ metric.note }}</small></div></section>
      <div class="wallet-highlight panel"><div class="wallet-highlight__icon"><WalletCards :size="22" /></div><div><span>账户编号</span><b>{{ wallet.accountId }}</b><p>平台虚拟钱包 · 不接入真实第三方支付</p></div><div class="wallet-highlight__state"><BadgeCheck :size="16" /><span>账实一致</span></div></div>
      <div class="toolbar panel"><div class="filter-chips"><button v-for="item in filters" :key="item" class="filter-chip" :class="{ active: filter === item }" type="button" @click="filter = item">{{ item }}</button></div><span class="status-pill status-pill--success"><ShieldCheck :size="12" />资金记录不可修改</span></div>
      <div class="table-panel">
        <table class="data-table wallet-table"><thead><tr><th>流水号</th><th>类型</th><th>业务编号</th><th>可用余额变化</th><th>冻结余额变化</th><th>变更后余额</th><th>时间</th></tr></thead><tbody>
          <tr v-for="item in visibleTransactions" :key="item.id"><td class="mono">{{ item.id }}</td><td><StatusPill :text="typeLabel(item.transactionType)" :tone="typeTone(item.transactionType)" /></td><td class="mono business-key">{{ item.businessKey }}</td><td class="money-cell" :class="{ 'red-text': item.availableChange.startsWith('-'), 'green-text': !item.availableChange.startsWith('-') }">{{ formatMoney(item.availableChange) }}</td><td class="money-cell">{{ formatMoney(item.frozenChange) }}</td><td><span>可用 {{ formatMoney(item.availableAfter) }}</span><span class="cell-note">冻结 {{ formatMoney(item.frozenAfter) }}</span></td><td>{{ formatFullDate(item.createdAt) }}</td></tr>
        </tbody></table>
        <div v-if="!visibleTransactions.length" class="empty-state"><WalletCards :size="22" /><b>暂无可展示流水</b><p>当前筛选条件下没有资金变化记录。</p></div>
      </div>
      <section class="rule-grid"><div class="info-panel"><h3>安全扣减</h3><p>余额变化由平台按资金规则完成，避免重复扣款或超扣。</p></div><div class="info-panel"><h3>资金记录</h3><p>冻结、扣款、退款与赔付都保留独立记录，不覆盖历史。</p></div><div class="info-panel"><h3>充值边界</h3><p>充值仅在平台允许的环境开放，不连接银行卡或第三方支付。</p></div></section>
    </template>
    <div v-else-if="!loading" class="panel empty-state"><WalletCards :size="24" /><b>钱包暂时无法读取</b><p>{{ loadError || '请稍后重试。' }}</p><button class="button button--outline" type="button" @click="load">重新加载</button></div>
    <ElDialog v-model="rechargeVisible" title="钱包充值" width="520px"><div class="dialog-form"><div class="warm-note"><ShieldCheck :size="16" /><p>充值仅在平台允许的环境开放，不连接真实银行卡或第三方支付。</p></div><label class="form-field"><span>充值金额</span><input v-model="rechargeForm.amount" inputmode="decimal" placeholder="请输入充值金额" /></label></div><template #footer><button class="button button--outline" type="button" @click="rechargeVisible = false">取消</button><button class="button button--accent" type="button" :disabled="recharging" @click="recharge">{{ recharging ? '正在入账…' : '确认充值' }}</button></template></ElDialog>
  </div>
</template>
