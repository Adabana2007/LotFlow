<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { RouterLink, useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Bot, CheckCircle2, FileSearch, ShieldAlert, Sparkles, UserRound, WalletCards } from 'lucide-vue-next'
import PageHeading from '@/components/PageHeading.vue'
import StatusPill from '@/components/StatusPill.vue'
import { apiRequest, errorMessage } from '@/api/client'
import type { AiTaskVO, DisputeDetailVO } from '@/types/api'
import { formatFullDate, formatMoney, statusText } from '@/utils/format'

type ResolutionOutcome = 'BUYER_WINS' | 'SELLER_WINS' | 'BUYER_WITHDRAWS'

const route = useRoute()
const disputeId = String(route.params.disputeId)
const detail = ref<DisputeDetailVO | null>(null)
const loading = ref(true)
const submitting = ref(false)
const summarizing = ref(false)
const loadError = ref('')
const actionError = ref('')
const summaryMessage = ref('AI 摘要只帮助阅读材料，不提供默认裁决结果。')
const summaryTask = ref<AiTaskVO | null>(null)
const confirmed = ref(false)
const form = reactive({ outcome: '' as ResolutionOutcome | '', resolution: '' })
const dispute = computed(() => detail.value?.dispute || null)
const isResolved = computed(() => dispute.value?.disputeStatus === 'RESOLVED')
const outcomes: Array<{ value: ResolutionOutcome; label: string; description: string }> = [
  { value: 'BUYER_WINS', label: '买家主张成立', description: '按既有资金规则退款，并处理相关保证金。' },
  { value: 'SELLER_WINS', label: '卖家主张成立', description: '解除争议并按既有结算规则处理货款。' },
  { value: 'BUYER_WITHDRAWS', label: '买家撤回争议', description: '记录撤回事实，恢复后续履约与结算流程。' },
]

async function load(): Promise<void> {
  loading.value = true
  loadError.value = ''
  try {
    detail.value = await apiRequest<DisputeDetailVO>(`/admin/disputes/${disputeId}`)
    form.outcome = ''
    form.resolution = ''
    confirmed.value = false
  } catch (error) {
    detail.value = null
    loadError.value = errorMessage(error)
  } finally {
    loading.value = false
  }
}

async function generateSummary(): Promise<void> {
  if (!dispute.value || summarizing.value) return
  summarizing.value = true
  try {
    const submitted = await apiRequest<{ taskId: string }>('/ai/dispute-summaries', {
      method: 'POST',
      body: JSON.stringify({ orderId: dispute.value.orderId }),
    })
    for (let attempt = 0; attempt < 10; attempt += 1) {
      await new Promise((resolve) => window.setTimeout(resolve, 1000))
      summaryTask.value = await apiRequest<AiTaskVO>(`/ai/tasks/${submitted.taskId}`)
      if (['SUCCEEDED', 'FAILED', 'FALLBACK'].includes(summaryTask.value.status)) break
    }
    summaryMessage.value = summaryTask.value?.outputSummary
      || (summaryTask.value?.status === 'FAILED' ? '摘要暂不可用，证据和裁决流程不受影响。' : '摘要任务仍在处理中。')
  } catch (error) {
    summaryMessage.value = errorMessage(error)
  } finally {
    summarizing.value = false
  }
}

async function submitDecision(): Promise<void> {
  if (!dispute.value || submitting.value) return
  if (!form.outcome) {
    ElMessage.warning('请选择人工处理结果')
    return
  }
  if (!form.resolution.trim()) {
    ElMessage.warning('请填写裁决理由')
    return
  }
  if (!confirmed.value) {
    ElMessage.warning('请确认已核对证据、订单和资金状态')
    return
  }
  const selected = outcomes.find((item) => item.value === form.outcome)
  try {
    await ElMessageBox.confirm(
      `${selected?.label}会立即触发资金和订单状态处理，提交后不能通过页面改写历史结果。`,
      '确认提交人工裁决',
      { confirmButtonText: '确认提交', cancelButtonText: '返回核对', type: 'warning' },
    )
  } catch {
    return
  }
  submitting.value = true
  actionError.value = ''
  try {
    await apiRequest<void>(`/admin/disputes/${dispute.value.id}/decisions`, {
      method: 'POST',
      body: JSON.stringify({ outcome: form.outcome, resolution: form.resolution.trim() }),
    })
    ElMessage.success('人工裁决已提交')
    await load()
  } catch (error) {
    actionError.value = errorMessage(error)
  } finally {
    submitting.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="admin-page" v-loading="loading">
    <template v-if="dispute">
      <PageHeading eyebrow="DISPUTE ADJUDICATION" :title="dispute.listingTitle" :description="`订单 ${dispute.orderNo} · 争议 ${dispute.id}`">
        <template #actions><RouterLink class="button button--outline" to="/admin/disputes">返回争议列表</RouterLink><StatusPill :text="isResolved ? '人工已处理' : '等待裁决'" :tone="isResolved ? 'success' : 'danger'" /></template>
      </PageHeading>
      <div class="admin-dispute-banner"><div><ShieldAlert :size="20" /><p><b>资金决定需要人工确认</b><br />AI 只能整理证据，不会替管理员选择结果，也不会直接退款或扣款。</p></div><div><span>当前订单状态</span><b>{{ statusText(dispute.orderStatus) }}</b></div><div><span>争议金额</span><b>{{ formatMoney(dispute.amount) }}</b></div></div>
      <div class="admin-dispute-layout">
        <div class="admin-dispute-main">
          <section class="panel"><div class="panel-head"><div><h3>当事人与交易</h3><p>商品、订单和当事人来自同一条交易事实。</p></div><UserRound :size="18" /></div><dl class="detail-list detail-list--wide"><div><dt>买家</dt><dd>{{ dispute.buyerName }} · {{ dispute.buyerId }}</dd></div><div><dt>卖家</dt><dd>{{ dispute.sellerName }} · {{ dispute.sellerId }}</dd></div><div><dt>商品编号</dt><dd>{{ dispute.listingId }}</dd></div><div><dt>争议发起时间</dt><dd>{{ formatFullDate(dispute.disputeOpenedAt) }}</dd></div></dl></section>
          <section class="panel evidence-panel-block"><div class="panel-head"><div><h3>证据材料</h3><p>材料不足时应要求补充，不根据缺失信息推断责任。</p></div><FileSearch :size="18" /></div><div class="statement"><b>买家争议理由</b><p>{{ dispute.reason }}</p></div><div class="statement"><b>买家证据说明</b><p>{{ dispute.evidence || '未提交文字证据说明' }}</p></div></section>
          <section class="panel"><div class="panel-head"><div><h3>可核对时间线</h3><p>只展示已经写入订单事实的节点。</p></div><FileSearch :size="18" /></div><div class="status-timeline"><div v-for="item in detail?.timeline" :key="`${item.type}-${item.occurredAt}`" class="done"><i /><div><b>{{ item.title }}</b><p>{{ item.detail }}</p></div><span>{{ formatFullDate(item.occurredAt) }}</span></div></div></section>
          <section v-if="!isResolved" class="panel review-decision"><div class="panel-head"><div><h3>人工裁决</h3><p>选择结果、填写依据并确认后才会触发资金和订单处理。</p></div><StatusPill text="必须人工提交" tone="warning" /></div><div class="decision-radio"><label v-for="item in outcomes" :key="item.value"><input v-model="form.outcome" type="radio" :value="item.value" /><span><b>{{ item.label }}</b><small>{{ item.description }}</small></span></label></div><label class="form-field"><span>裁决理由</span><textarea v-model="form.resolution" rows="5" maxlength="2000" placeholder="说明证据采信、责任判断和资金处理依据；理由会进入审计记录" /></label><el-checkbox v-model="confirmed">我已核对订单、证据、担保金额和保证金状态</el-checkbox><div v-if="actionError" class="warning-block"><ShieldAlert :size="15" /><p>{{ actionError }}</p></div><button class="button button--accent button--wide button--with-icon" type="button" :disabled="submitting || !form.outcome || !form.resolution.trim() || !confirmed" @click="submitDecision"><CheckCircle2 :size="15" />{{ submitting ? '正在提交…' : '提交人工裁决' }}</button></section>
          <section v-else class="panel"><div class="panel-head"><div><h3>已记录的人工结果</h3><p>资金和订单状态已按该结果处理。</p></div><StatusPill text="已完成" tone="success" /></div><div class="statement"><p>{{ dispute.resolution }}</p><span>处理人 {{ dispute.resolvedBy || '平台管理员' }} · {{ formatFullDate(dispute.resolvedAt) }}</span></div></section>
        </div>
        <aside class="admin-dispute-side">
          <section class="panel"><div class="panel-head"><h3><WalletCards :size="16" /> 资金预检</h3><StatusPill :text="isResolved ? '已处理' : '待处理'" :tone="isResolved ? 'success' : 'warning'" /></div><dl class="detail-list"><div><dt>订单金额</dt><dd>{{ formatMoney(dispute.amount) }}</dd></div><div><dt>保证金</dt><dd>{{ formatMoney(dispute.depositApplied) }}</dd></div><div><dt>担保金额</dt><dd>{{ formatMoney(dispute.escrowAmount) }}</dd></div><div><dt>付款时间</dt><dd>{{ formatFullDate(dispute.paidAt) }}</dd></div><div><dt>发货时间</dt><dd>{{ formatFullDate(dispute.shippedAt) }}</dd></div></dl><div class="fund-preflight"><span>不可绕过</span><b>页面没有余额编辑能力</b><p>提交裁决后由资金服务按幂等业务键生成不可变流水。</p></div></section>
          <section class="panel ai-summary-panel"><div class="panel-head"><h3><Bot :size="16" /> AI 争议摘要</h3><Sparkles :size="16" /></div><p>{{ summaryMessage }}</p><button class="button button--accent button--wide" type="button" :disabled="summarizing" @click="generateSummary">{{ summarizing ? '正在整理…' : '生成只读摘要' }}</button><div class="ai-boundary-mini">摘要不会预填裁决结果、不会提交表单，也不会决定退款或信用处罚。</div></section>
          <RouterLink class="button button--outline button--wide" :to="`/admin/orders`">查看订单运营</RouterLink>
        </aside>
      </div>
    </template>
    <div v-else-if="!loading" class="panel empty-state"><FileSearch :size="26" /><b>争议详情暂时无法读取</b><p>{{ loadError || '请确认争议编号和当前管理员权限。' }}</p><button class="button button--outline" type="button" @click="load">重新加载</button></div>
  </div>
</template>

<style scoped>
.decision-radio label { align-items: flex-start; }
.decision-radio label > span { display: grid; gap: 3px; }
.decision-radio small { color: var(--muted); font-size: 12px; line-height: 1.5; }
.statement b { color: var(--ink); font-size: 14px; }
.statement p { margin-top: 6px; color: var(--muted); font-size: 14px; line-height: 1.7; white-space: pre-wrap; }
.statement span { display: block; margin-top: 8px; color: var(--muted); font-size: 12px; }
.review-decision > .el-checkbox, .review-decision > .button, .review-decision > .warning-block { margin: 12px 15px 0; }
.review-decision > .button { width: calc(100% - 30px); }
</style>
