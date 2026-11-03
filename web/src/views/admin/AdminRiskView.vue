<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  Ban,
  Bot,
  CheckCircle2,
  MessageSquareWarning,
  Search,
  ShieldAlert,
  TriangleAlert,
  UserRoundX,
} from 'lucide-vue-next'
import PageHeading from '@/components/PageHeading.vue'
import StatusPill from '@/components/StatusPill.vue'
import { apiRequest, errorMessage } from '@/api/client'
import type { CursorPage, RiskEventVO } from '@/types/api'
import { formatDateTime, statusText } from '@/utils/format'

type DispositionAction = 'MARK_FALSE_POSITIVE' | 'RECORD_REMINDER' | 'RESTRICT_ACCOUNT' | 'DISABLE_ACCOUNT'

const loading = ref(true)
const loadError = ref('')
const events = ref<RiskEventVO[]>([])
const selectedId = ref('')
const search = ref('')
const riskFilter = ref('全部风险')
const dispositionVisible = ref(false)
const submitting = ref(false)
const actionError = ref('')
const disposition = reactive({ action: '' as DispositionAction | '', reason: '', confirmed: false })
const riskFilters = ['全部风险', '高风险', '中风险']
const actions: Array<{ value: DispositionAction; label: string; description: string; icon: typeof Ban; tone: 'neutral' | 'warning' | 'danger' }> = [
  { value: 'MARK_FALSE_POSITIVE', label: '标记误报', description: '确认该事件不构成交易风险，并关闭复核任务。', icon: CheckCircle2, tone: 'neutral' },
  { value: 'RECORD_REMINDER', label: '记录人工提醒', description: '记录管理员已经人工提醒该用户，保留处置依据。', icon: MessageSquareWarning, tone: 'warning' },
  { value: 'RESTRICT_ACCOUNT', label: '限制账号', description: '将消息发送者设为受限状态，限制其重新登录和交易操作。', icon: TriangleAlert, tone: 'danger' },
  { value: 'DISABLE_ACCOUNT', label: '停用账号', description: '将消息发送者设为停用状态，只用于证据明确的严重风险。', icon: UserRoundX, tone: 'danger' },
]
const selected = computed(() => events.value.find((event) => event.id === selectedId.value) || events.value[0] || null)
const visible = computed(() => events.value.filter((event) => {
  const textMatch = `${event.id}${event.messageId}${event.categories}${event.evidenceSummary}`.toLowerCase().includes(search.value.trim().toLowerCase())
  const riskMatch = riskFilter.value === '全部风险' || statusText(event.riskLevel) === riskFilter.value
  return textMatch && riskMatch
}))
const highRiskCount = computed(() => events.value.filter((event) => event.riskLevel === 'HIGH').length)
const humanReviewCount = computed(() => events.value.filter((event) => event.requiresHumanReview).length)
const selectedAction = computed(() => actions.find((item) => item.value === disposition.action) || null)

async function load(): Promise<void> {
  loading.value = true
  loadError.value = ''
  try {
    const page = await apiRequest<CursorPage<RiskEventVO>>('/admin/risk-events?limit=100')
    events.value = page.items
    selectedId.value = page.items[0]?.id || ''
  } catch (error) {
    events.value = []
    selectedId.value = ''
    loadError.value = errorMessage(error)
  } finally {
    loading.value = false
  }
}

function openDisposition(action: DispositionAction): void {
  disposition.action = action
  disposition.reason = ''
  disposition.confirmed = false
  actionError.value = ''
  dispositionVisible.value = true
}

async function submitDisposition(): Promise<void> {
  if (!selected.value || !disposition.action || submitting.value) return
  if (!disposition.reason.trim()) {
    ElMessage.warning('请填写人工处置理由')
    return
  }
  if (!disposition.confirmed) {
    ElMessage.warning('请确认已查看风险上下文和证据摘要')
    return
  }
  try {
    await ElMessageBox.confirm(
      `${selectedAction.value?.label}会写入审计记录${['RESTRICT_ACCOUNT', 'DISABLE_ACCOUNT'].includes(disposition.action) ? '，并立即改变账号状态' : ''}。`,
      '确认人工处置',
      { confirmButtonText: '确认处置', cancelButtonText: '返回核对', type: 'warning' },
    )
  } catch {
    return
  }
  submitting.value = true
  actionError.value = ''
  try {
    await apiRequest<void>(`/admin/risk-events/${selected.value.messageId}/dispositions`, {
      method: 'POST',
      body: JSON.stringify({ action: disposition.action, reason: disposition.reason.trim() }),
    })
    dispositionVisible.value = false
    ElMessage.success('人工处置已记录')
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
    <PageHeading eyebrow="CHAT RISK" title="聊天风险处置" description="风险分类与证据摘要用于人工复核；系统不会根据 AI 结论自动封号或处罚。">
      <template #actions><StatusPill text="人工复核优先" tone="warning" /></template>
    </PageHeading>
    <div class="admin-metric-grid admin-metric-grid--small"><div class="panel admin-metric"><span>待处置事件</span><strong>{{ events.length }}</strong><p>当前待复核结果</p></div><div class="panel admin-metric"><span>待人工</span><strong>{{ humanReviewCount }}</strong><p>需要查看证据上下文</p></div><div class="panel admin-metric"><span>高风险</span><strong>{{ highRiskCount }}</strong><p>优先人工核对</p></div><div class="panel admin-metric"><span>AI 自动处罚</span><strong class="green-text">0</strong><p>任何账号措施都由人工提交</p></div></div>
    <div class="risk-layout">
      <section class="table-panel risk-list-panel">
        <div class="toolbar toolbar--flush"><div class="filter-chips"><button v-for="item in riskFilters" :key="item" class="filter-chip" :class="{ active: riskFilter === item }" type="button" @click="riskFilter = item">{{ item }}</button></div><label class="inline-search"><Search :size="14" /><input v-model="search" placeholder="搜索消息、风险编号或分类" /></label></div>
        <table class="data-table admin-table"><thead><tr><th>风险事件</th><th>分类</th><th>证据摘要</th><th>时间</th><th>状态</th></tr></thead><tbody><tr v-for="event in visible" :key="event.id" :class="{ active: selected?.id === event.id }" @click="selectedId = event.id"><td class="mono">{{ event.id }}</td><td><StatusPill :text="event.categories" :tone="event.riskLevel === 'HIGH' ? 'danger' : 'warning'" /></td><td class="evidence-cell">{{ event.evidenceSummary }}</td><td>{{ formatDateTime(event.occurredAt) }}</td><td><StatusPill :text="event.requiresHumanReview ? '待人工' : '已标记'" :tone="event.requiresHumanReview ? 'warning' : 'neutral'" /></td></tr></tbody></table>
        <div v-if="!visible.length" class="empty-state"><Search :size="22" /><b>{{ loadError ? '风险事件读取失败' : '没有待处置事件' }}</b><p>{{ loadError || '已处置事件会保留审计记录，不再出现在待复核列表。' }}</p><button v-if="loadError" class="button button--outline" type="button" @click="load">重新加载</button></div>
      </section>
      <aside v-if="selected" class="panel risk-context-panel"><div class="panel-head"><h3>风险上下文</h3><StatusPill :text="statusText(selected.riskLevel)" :tone="selected.riskLevel === 'HIGH' ? 'danger' : 'warning'" /></div><div class="risk-context-message"><MessageSquareWarning :size="18" /><p>{{ selected.evidenceSummary }}</p><span>消息编号 {{ selected.messageId }} · 分类 {{ selected.categories }}</span></div><div class="risk-context-message seller"><Bot :size="18" /><p>AI 只生成风险标签和建议。处置必须由人工结合完整上下文判断，不能把摘要直接当作处罚结论。</p></div><div class="panel-divider" /><h4>人工处置</h4><button v-for="action in actions" :key="action.value" class="risk-action-button" :class="`risk-action-button--${action.tone}`" type="button" @click="openDisposition(action.value)"><component :is="action.icon" :size="16" /><span><b>{{ action.label }}</b><small>{{ action.description }}</small></span></button><div class="ai-boundary-mini"><ShieldAlert :size="13" />限制或停用账号会立即写入账号状态和审计记录，必须人工确认。</div></aside>
    </div>
    <ElDialog v-model="dispositionVisible" :title="selectedAction?.label || '人工处置'" width="560px">
      <div v-if="selected" class="dialog-form"><div class="dialog-user"><Bot :size="18" /><div><b>风险事件 {{ selected.id }}</b><span>消息 {{ selected.messageId }} · {{ selected.categories }}</span></div></div><p class="disposition-description">{{ selectedAction?.description }}</p><label class="form-field"><span>处置理由</span><textarea v-model="disposition.reason" rows="5" maxlength="1000" placeholder="说明证据采信、人工判断和措施依据；理由会进入审计记录" /></label><el-checkbox v-model="disposition.confirmed">我已查看风险上下文，确认由本人作出该处置</el-checkbox><div v-if="actionError" class="warning-block"><ShieldAlert :size="15" /><p>{{ actionError }}</p></div></div>
      <template #footer><button class="button button--outline" type="button" @click="dispositionVisible = false">取消</button><button class="button button--accent" type="button" :disabled="submitting || !disposition.reason.trim() || !disposition.confirmed" @click="submitDisposition">{{ submitting ? '正在提交…' : '提交人工处置' }}</button></template>
    </ElDialog>
  </div>
</template>

<style scoped>
.risk-action-button { display: flex; width: calc(100% - 30px); align-items: flex-start; gap: 9px; margin: 7px 15px; padding: 10px 11px; border: 1px solid var(--line); border-radius: 4px; background: #fff; color: var(--ink); text-align: left; }
.risk-action-button:hover { border-color: var(--line-strong); background: var(--surface-muted); }
.risk-action-button--danger { border-color: #e4c5be; color: var(--accent-dark); }
.risk-action-button span { display: grid; gap: 3px; }
.risk-action-button b { font-size: 14px; }
.risk-action-button small { color: var(--muted); font-size: 12px; line-height: 1.5; }
.disposition-description { color: var(--muted); font-size: 14px; line-height: 1.65; }
</style>
