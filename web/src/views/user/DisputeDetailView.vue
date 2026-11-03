<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { RouterLink, useRoute } from 'vue-router'
import { Bot, FileSearch, ShieldAlert, Sparkles, UserRound, WalletCards } from 'lucide-vue-next'
import PageHeading from '@/components/PageHeading.vue'
import StatusPill from '@/components/StatusPill.vue'
import { apiRequest, errorMessage } from '@/api/client'
import type { AiTaskVO, DisputeDetailVO } from '@/types/api'
import { formatFullDate, formatMoney, statusText } from '@/utils/format'

const route = useRoute()
const orderId = String(route.params.id || route.query.orderId || '')
const detail = ref<DisputeDetailVO | null>(null)
const loading = ref(true)
const loadError = ref('')
const summaryTask = ref<AiTaskVO | null>(null)
const summaryMessage = ref('AI 只整理已经存在的订单事实、争议说明和时间线，不判断责任。')
const summarizing = ref(false)

const dispute = computed(() => detail.value?.dispute || null)
const isResolved = computed(() => dispute.value?.disputeStatus === 'RESOLVED')
const outcomeText = computed(() => {
  const resolution = dispute.value?.resolution || ''
  if (resolution.includes('BUYER_WINS')) return '买家主张成立'
  if (resolution.includes('SELLER_WINS')) return '卖家主张成立'
  if (resolution.includes('BUYER_WITHDRAWS')) return '买家撤回争议'
  return isResolved.value ? '人工已完成处理' : '等待人工处理'
})

async function load(): Promise<void> {
  loading.value = true
  loadError.value = ''
  try {
    detail.value = await apiRequest<DisputeDetailVO>(`/disputes/${orderId}`)
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
      || (summaryTask.value?.status === 'FAILED' ? '摘要暂不可用，争议材料不受影响。' : '摘要任务仍在处理中，请稍后重试。')
  } catch (error) {
    summaryMessage.value = errorMessage(error)
  } finally {
    summarizing.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="workspace-page" v-loading="loading">
    <template v-if="dispute">
      <PageHeading eyebrow="争议详情" :title="dispute.listingTitle" :description="`订单 ${dispute.orderNo} · ${formatFullDate(dispute.disputeOpenedAt)} 发起`">
        <template #actions><StatusPill :text="isResolved ? '人工已处理' : '等待人工处理'" :tone="isResolved ? 'success' : 'warning'" /></template>
      </PageHeading>
      <div class="dispute-summary-band"><div><ShieldAlert :size="20" /><div><b>{{ outcomeText }}</b><p>争议期间平台暂停向卖家结算，处理结果必须由人工提交并保留理由。</p></div></div><div><span>争议金额</span><b>{{ formatMoney(dispute.amount) }}</b></div></div>
      <div class="dispute-layout">
        <div class="dispute-main">
          <section class="panel dispute-order-card">
            <div class="panel-head"><div><h3>交易与当事人</h3><p>订单、买卖家和争议状态来自同一条交易事实。</p></div><FileSearch :size="18" /></div>
            <dl class="detail-list detail-list--wide"><div><dt>订单编号</dt><dd>{{ dispute.orderNo }}</dd></div><div><dt>订单状态</dt><dd>{{ statusText(dispute.orderStatus) }}</dd></div><div><dt>买家</dt><dd>{{ dispute.buyerName }} · {{ dispute.buyerId }}</dd></div><div><dt>卖家</dt><dd>{{ dispute.sellerName }} · {{ dispute.sellerId }}</dd></div><div><dt>商品编号</dt><dd>{{ dispute.listingId }}</dd></div><div><dt>争议编号</dt><dd>{{ dispute.id }}</dd></div></dl>
          </section>
          <section class="panel evidence-panel-block"><div class="panel-head"><div><h3>争议理由与证据说明</h3><p>材料由买卖家提交，AI 不会补写不存在的证据。</p></div><UserRound :size="18" /></div><div class="statement"><b>争议理由</b><p>{{ dispute.reason }}</p></div><div class="statement"><b>证据说明</b><p>{{ dispute.evidence || '买家未补充图片或文字证据说明。' }}</p></div></section>
          <section class="panel"><div class="panel-head"><div><h3>交易时间线</h3><p>仅展示已经写入订单事实的时间节点。</p></div><FileSearch :size="18" /></div><div class="status-timeline"><div v-for="item in detail?.timeline" :key="`${item.type}-${item.occurredAt}`" class="done"><i /><div><b>{{ item.title }}</b><p>{{ item.detail }}</p></div><span>{{ formatFullDate(item.occurredAt) }}</span></div></div></section>
        </div>
        <aside class="dispute-side">
          <section class="panel"><div class="panel-head"><h3><WalletCards :size="16" /> 资金状态</h3><StatusPill :text="isResolved ? '已处理' : '暂停结算'" :tone="isResolved ? 'success' : 'warning'" /></div><dl class="detail-list"><div><dt>订单金额</dt><dd>{{ formatMoney(dispute.amount) }}</dd></div><div><dt>已应用保证金</dt><dd>{{ formatMoney(dispute.depositApplied) }}</dd></div><div><dt>平台担保金额</dt><dd>{{ formatMoney(dispute.escrowAmount) }}</dd></div><div><dt>人工结果</dt><dd>{{ dispute.resolution || '尚未提交' }}</dd></div><div v-if="dispute.resolvedBy"><dt>处理人</dt><dd>{{ dispute.resolvedBy }}</dd></div><div v-if="dispute.resolvedAt"><dt>处理时间</dt><dd>{{ formatFullDate(dispute.resolvedAt) }}</dd></div></dl><div class="fund-preflight"><span>资金边界</span><b>余额不会由页面直接改写</b><p>人工结果只触发既有退款、释放或结算命令，并写入资金流水。</p></div></section>
          <section class="panel ai-summary-panel"><div class="panel-head"><h3><Bot :size="16" /> AI 争议摘要</h3><Sparkles :size="16" /></div><p>{{ summaryMessage }}</p><button class="button button--accent button--wide" type="button" :disabled="summarizing" @click="generateSummary">{{ summarizing ? '正在整理…' : '生成只读摘要' }}</button><div class="ai-boundary-mini">AI 不决定责任、不退款、不扣信用；最终裁决只由人工提交。</div></section>
          <RouterLink class="button button--outline button--wide" :to="`/orders/${dispute.orderId}`">返回订单详情</RouterLink>
        </aside>
      </div>
    </template>
    <div v-else-if="!loading" class="panel empty-state"><FileSearch :size="26" /><b>争议详情暂时无法读取</b><p>{{ loadError || '请确认该订单属于当前账号。' }}</p><RouterLink class="button button--outline" to="/orders">返回订单列表</RouterLink></div>
  </div>
</template>

<style scoped>
.statement b { color: var(--ink); font-size: 14px; }
.statement p { margin-top: 6px; color: var(--muted); font-size: 14px; line-height: 1.7; white-space: pre-wrap; }
</style>
