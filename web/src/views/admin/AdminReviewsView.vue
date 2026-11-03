<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import { Clock3, Search, ShieldAlert } from 'lucide-vue-next'
import PageHeading from '@/components/PageHeading.vue'
import StatusPill from '@/components/StatusPill.vue'
import { apiRequest, errorMessage } from '@/api/client'
import type { CursorPage, ReviewTaskVO } from '@/types/api'
import { formatDateTime, statusText } from '@/utils/format'

const loading = ref(true)
const loadError = ref('')
const tasks = ref<ReviewTaskVO[]>([])
const risk = ref('全部风险')
const search = ref('')
const riskOptions = ['全部风险', '高风险', '中风险', '低风险']
const visible = computed(() => tasks.value.filter((task) => {
  const riskMatch = risk.value === '全部风险'
    || (risk.value === '高风险' && task.riskLevel === 'HIGH')
    || (risk.value === '中风险' && task.riskLevel === 'MEDIUM')
    || (risk.value === '低风险' && task.riskLevel === 'LOW')
  return riskMatch && `${task.id}${task.listingId}${task.sellerId}`.includes(search.value.trim())
}))
const pendingCount = computed(() => tasks.value.filter((task) => task.status !== 'COMPLETED').length)
const highRiskCount = computed(() => tasks.value.filter((task) => task.riskLevel === 'HIGH' || task.riskLevel === 'MEDIUM').length)
const longestWait = computed(() => {
  const oldest = tasks.value.reduce<string | null>((current, task) => {
    if (!current) return task.createdAt
    return new Date(task.createdAt).getTime() < new Date(current).getTime() ? task.createdAt : current
  }, null)
  if (!oldest) return '—'
  return `${Math.max(0, Math.round((Date.now() - new Date(oldest).getTime()) / 60_000))}m`
})

async function load(): Promise<void> {
  loading.value = true
  loadError.value = ''
  try {
    const page = await apiRequest<CursorPage<ReviewTaskVO>>('/admin/review-tasks?limit=50')
    tasks.value = page.items
  } catch (error) {
    tasks.value = []
    loadError.value = errorMessage(error)
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="admin-page" v-loading="loading">
    <PageHeading eyebrow="CONTENT REVIEW" title="商品审核队列" description="规则初筛和 AI 风险标签只帮助排序，中高风险与最终决定由人工完成。">
      <template #actions><StatusPill text="人工最终决定" tone="warning" /></template>
    </PageHeading>
    <div class="admin-metric-grid admin-metric-grid--small"><div class="panel admin-metric"><span>队列总数</span><strong>{{ tasks.length }}</strong><p>当前审核队列</p></div><div class="panel admin-metric"><span>待处理</span><strong>{{ pendingCount }}</strong><p>包含人工复核</p></div><div class="panel admin-metric"><span>中高风险</span><strong>{{ highRiskCount }}</strong><p>优先人工复核</p></div><div class="panel admin-metric"><span>最久等待</span><strong>{{ longestWait }}</strong><p>按任务创建时间计算</p></div></div>
    <div class="toolbar panel"><div class="filter-chips"><button v-for="item in riskOptions" :key="item" class="filter-chip" :class="{ active: risk === item }" type="button" @click="risk = item">{{ item }}</button></div><label class="inline-search"><Search :size="14" /><input v-model="search" placeholder="搜索任务、拍品或卖家编号" /></label></div>
    <div class="table-panel">
      <table class="data-table admin-table"><thead><tr><th>任务</th><th>拍品 / 卖家</th><th>风险等级</th><th>AI 证据摘要</th><th>创建时间</th><th>状态</th><th>操作</th></tr></thead><tbody>
        <tr v-for="task in visible" :key="task.id" :class="{ 'table-row--danger': task.riskLevel === 'HIGH' }"><td class="mono">R-{{ task.id }}</td><td><b>#{{ task.listingId }}</b><span class="cell-note">卖家 {{ task.sellerId }}</span></td><td><StatusPill :text="statusText(task.riskLevel)" :tone="task.riskLevel === 'HIGH' ? 'danger' : task.riskLevel === 'MEDIUM' ? 'warning' : 'success'" /></td><td class="evidence-cell">{{ task.evidenceSummary || '暂无摘要' }}</td><td><span class="time-warning"><Clock3 :size="12" />{{ formatDateTime(task.createdAt) }}</span></td><td><StatusPill :text="task.manualRequired ? '需人工' : statusText(task.status)" :tone="task.manualRequired ? 'warning' : 'neutral'" /></td><td><RouterLink v-if="task.status !== 'COMPLETED'" class="button button--small button--dark" :to="`/admin/reviews/${task.id}`">审核</RouterLink><span v-else class="muted-note">已完成</span></td></tr>
      </tbody></table>
      <div v-if="!visible.length" class="empty-state"><Search :size="22" /><b>{{ loadError ? '审核任务读取失败' : '没有匹配任务' }}</b><p>{{ loadError || '调整风险筛选或搜索条件后再试。' }}</p><button v-if="loadError" class="button button--outline" type="button" @click="load">重新加载</button></div>
    </div>
    <div class="warm-note"><ShieldAlert :size="16" /><p>AI 风险标签不构成最终审核结论，也不会自动驳回或发布商品。</p></div>
  </div>
</template>
