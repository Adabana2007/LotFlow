<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { Bot, Coins, Search, ShieldCheck, Sparkles, Timer } from 'lucide-vue-next'
import PageHeading from '@/components/PageHeading.vue'
import StatusPill from '@/components/StatusPill.vue'
import { apiRequest, errorMessage } from '@/api/client'
import type { AiTaskVO, AiUsageSummaryVO, CursorPage } from '@/types/api'
import { aiTaskTypeText, formatFullDate, statusText } from '@/utils/format'

const loading = ref(true)
const loadError = ref('')
const rows = ref<AiTaskVO[]>([])
const usage = ref<AiUsageSummaryVO | null>(null)
const selectedId = ref('')
const search = ref('')
const statusFilter = ref('全部状态')
const statuses = ['全部状态', '已完成', '失败', '降级完成']
const current = computed(() => rows.value.find((row) => row.id === selectedId.value) || rows.value[0] || null)
const visible = computed(() => rows.value.filter((row) => {
  const keyword = search.value.trim().toLowerCase()
  const matchesSearch = !keyword || `${row.id}${row.taskType}${row.modelName}${row.traceId}${row.errorCode}`.toLowerCase().includes(keyword)
  return matchesSearch && (statusFilter.value === '全部状态' || statusText(row.status) === statusFilter.value)
}))
const successRate = computed(() => {
  const totals = usage.value?.totals
  if (!totals?.totalCalls) return '—'
  return `${((totals.succeededCalls / totals.totalCalls) * 100).toFixed(1)}%`
})

function tokenText(value?: number | null): string {
  return value === null || value === undefined ? '—' : value.toLocaleString('zh-CN')
}

function costText(value?: string | null): string {
  if (!usage.value?.costConfigured) return '未配置'
  return value === null || value === undefined ? '未配置' : `¥${value}`
}

async function load(): Promise<void> {
  loading.value = true
  loadError.value = ''
  try {
    const [page, summary] = await Promise.all([
      apiRequest<CursorPage<AiTaskVO>>('/admin/ai-call-logs?limit=100'),
      apiRequest<AiUsageSummaryVO>('/admin/ai/usage'),
    ])
    rows.value = page.items
    usage.value = summary
    selectedId.value = page.items[0]?.id || ''
  } catch (error) {
    rows.value = []
    usage.value = null
    selectedId.value = ''
    loadError.value = errorMessage(error)
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="admin-page" v-loading="loading">
    <PageHeading eyebrow="AI GOVERNANCE" title="AI 使用与成本审计" description="记录模型、规则版本、Token、延迟和失败原因；不保存完整隐私正文，也不把 AI 记录当作资金或审核决定。">
      <template #actions><StatusPill text="可追踪" tone="success" /></template>
    </PageHeading>
    <div class="admin-metric-grid admin-metric-grid--small"><div class="panel admin-metric"><span>调用总量</span><strong>{{ usage?.totals.totalCalls || 0 }}</strong><p>已持久化审计记录</p></div><div class="panel admin-metric"><span>成功率</span><strong class="green-text">{{ successRate }}</strong><p>成功调用 / 全部调用</p></div><div class="panel admin-metric"><span>Token 总量</span><strong>{{ tokenText((usage?.totals.promptTokens || 0) + (usage?.totals.completionTokens || 0)) }}</strong><p>提示 {{ tokenText(usage?.totals.promptTokens) }} · 输出 {{ tokenText(usage?.totals.completionTokens) }}</p></div><div class="panel admin-metric"><span>估算成本</span><strong>{{ costText(usage?.estimatedCost) }}</strong><p>{{ usage?.costConfigured ? '按已配置单价估算' : '未配置单价，不显示为 0' }}</p></div></div>
    <section class="panel model-usage-panel"><div class="panel-head"><div><h3>模型用量</h3><p>按模型聚合调用次数、Token 和平均延迟。</p></div><Sparkles :size="18" /></div><div class="model-usage-grid"><div v-for="model in usage?.models" :key="model.modelName" class="model-usage-card"><div><Bot :size="16" /><b>{{ model.modelName || '未记录模型' }}</b></div><strong>{{ model.calls }} 次</strong><p>提示 {{ tokenText(model.promptTokens) }} · 输出 {{ tokenText(model.completionTokens) }}</p><span><Timer :size="13" /> {{ model.averageLatencyMs === null ? '延迟未记录' : `平均 ${model.averageLatencyMs} ms` }}</span><span><Coins :size="13" /> {{ costText(model.estimatedCost) }}</span></div><div v-if="!usage?.models.length" class="empty-state"><Bot :size="22" /><b>暂无模型调用记录</b><p>调用发生后会按模型汇总。</p></div></div></section>
    <div class="toolbar panel"><div class="filter-chips"><button v-for="item in statuses" :key="item" class="filter-chip" :class="{ active: statusFilter === item }" type="button" @click="statusFilter = item">{{ item }}</button></div><label class="inline-search"><Search :size="14" /><input v-model="search" placeholder="搜索任务、模型或追踪编号" /></label></div>
    <div class="ai-log-layout">
      <section class="table-panel"><table class="data-table admin-table"><thead><tr><th>任务</th><th>类型</th><th>模型</th><th>Token</th><th>延迟</th><th>状态</th><th>时间</th></tr></thead><tbody><tr v-for="row in visible" :key="row.id" :class="{ active: current?.id === row.id }" @click="selectedId = row.id"><td class="mono">{{ row.id }}</td><td>{{ aiTaskTypeText(row.taskType) }}</td><td>{{ row.modelName || '—' }}</td><td>{{ tokenText((row.promptTokens || 0) + (row.completionTokens || 0)) }}</td><td>{{ row.latencyMs ? `${row.latencyMs} ms` : '—' }}</td><td><StatusPill :text="statusText(row.status)" :tone="row.status === 'FAILED' ? 'danger' : row.status === 'FALLBACK' ? 'warning' : 'success'" /></td><td>{{ formatFullDate(row.createdAt) }}</td></tr></tbody></table><div v-if="!visible.length" class="empty-state"><Bot :size="22" /><b>{{ loadError ? 'AI 记录读取失败' : '没有匹配记录' }}</b><p>{{ loadError || '调整状态或搜索条件后再试。' }}</p><button v-if="loadError" class="button button--outline" type="button" @click="load">重新加载</button></div></section>
      <aside v-if="current" class="panel ai-log-detail"><div class="panel-head"><h3><Bot :size="16" /> 使用详情</h3><StatusPill :text="statusText(current.status)" :tone="current.status === 'FAILED' ? 'danger' : current.status === 'FALLBACK' ? 'warning' : 'success'" /></div><dl class="detail-list"><div><dt>任务编号</dt><dd>{{ current.id }}</dd></div><div><dt>任务类型</dt><dd>{{ aiTaskTypeText(current.taskType) }}</dd></div><div><dt>模型</dt><dd>{{ current.modelName || '—' }}</dd></div><div><dt>规则版本</dt><dd>{{ current.promptVersion || '—' }}</dd></div><div><dt>提示 Token</dt><dd>{{ tokenText(current.promptTokens) }}</dd></div><div><dt>输出 Token</dt><dd>{{ tokenText(current.completionTokens) }}</dd></div><div><dt>处理用时</dt><dd>{{ current.latencyMs ? `${current.latencyMs} ms` : '—' }}</dd></div><div><dt>追踪编号</dt><dd class="mono">{{ current.traceId || '—' }}</dd></div><div><dt>失败原因</dt><dd>{{ current.errorCode || '无' }}</dd></div></dl><div class="ai-boundary-mini"><ShieldCheck :size="13" />成本为可配置估算值；单价未配置时保持“未配置”。AI 记录没有最终资金、成交或审核权限。</div></aside>
    </div>
  </div>
</template>

<style scoped>
.model-usage-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(220px, 1fr)); gap: 9px; padding: 14px; }
.model-usage-card { padding: 12px; border: 1px solid var(--line); border-radius: 4px; background: #f7f8f3; }
.model-usage-card > div { display: flex; align-items: center; gap: 7px; color: var(--jade); }
.model-usage-card b { color: var(--ink); font-size: 14px; }
.model-usage-card strong { display: block; margin: 9px 0 4px; font-size: 20px; }
.model-usage-card p, .model-usage-card span { display: flex; align-items: center; gap: 5px; color: var(--muted); font-size: 12px; line-height: 1.6; }
.ai-log-layout { display: grid; grid-template-columns: minmax(0, 1.75fr) minmax(300px, .7fr); gap: 12px; }
</style>
