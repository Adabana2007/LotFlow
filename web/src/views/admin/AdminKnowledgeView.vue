<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { BookOpenCheck, DatabaseZap, RefreshCw, ShieldCheck } from 'lucide-vue-next'
import PageHeading from '@/components/PageHeading.vue'
import StatusPill from '@/components/StatusPill.vue'
import { apiRequest, errorMessage } from '@/api/client'
import type { KnowledgeIndexVO } from '@/types/api'

const loading = ref(true)
const rebuilding = ref(false)
const loadError = ref('')
const status = ref<KnowledgeIndexVO | null>(null)

async function load(): Promise<void> {
  loading.value = true
  loadError.value = ''
  try {
    status.value = await apiRequest<KnowledgeIndexVO>('/admin/ai/knowledge-index')
  } catch (error) {
    status.value = null
    loadError.value = errorMessage(error)
  } finally {
    loading.value = false
  }
}

async function rebuild(): Promise<void> {
  if (rebuilding.value) return
  try {
    await ElMessageBox.confirm(
      '重建会重新读取版本化平台规则并更新向量索引。重建期间平台交易和订单流程不受影响。',
      '确认重建知识库索引',
      { confirmButtonText: '开始重建', cancelButtonText: '取消', type: 'warning' },
    )
  } catch {
    return
  }
  rebuilding.value = true
  try {
    await apiRequest<void>('/admin/ai/knowledge-index/rebuilds', { method: 'POST' })
    for (let attempt = 0; attempt < 10; attempt += 1) {
      await new Promise((resolve) => window.setTimeout(resolve, 1000))
      await load()
      if (status.value?.ready && !status.value.lastError) break
    }
    ElMessage.success(status.value?.ready ? '知识库索引已就绪' : '重建任务已提交，可稍后刷新状态')
  } catch (error) {
    ElMessage.error(errorMessage(error))
  } finally {
    rebuilding.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="admin-page" v-loading="loading">
    <PageHeading eyebrow="KNOWLEDGE INDEX" title="平台规则知识库" description="规则文档与索引版本由平台维护；AI 助手只在已就绪的知识库上检索和引用，不直接读取数据库或交易命令。">
      <template #actions><StatusPill :text="status?.ready ? '索引已就绪' : '索引不可用'" :tone="status?.ready ? 'success' : 'warning'" /></template>
    </PageHeading>
    <div class="knowledge-status-grid">
      <section class="panel knowledge-main-card"><div class="knowledge-icon"><BookOpenCheck :size="25" /></div><div><span>当前索引版本</span><strong>{{ status?.activeVersion || '尚未激活' }}</strong><p>知识库文档更新后由管理员显式重建，避免请求期间随意切换规则版本。</p><div class="knowledge-meta"><StatusPill :text="status?.ready ? '可检索' : '不可检索'" :tone="status?.ready ? 'success' : 'danger'" /><span v-if="status?.lastError" class="red-text">{{ status.lastError }}</span></div></div></section>
      <aside class="panel knowledge-action-card"><DatabaseZap :size="22" /><h3>重建索引</h3><p>重新读取版本化规则文件和向量模型，成功后原子切换活动版本。</p><button class="button button--dark button--wide button--with-icon" type="button" :disabled="rebuilding" @click="rebuild"><RefreshCw :size="15" :class="{ spinning: rebuilding }" />{{ rebuilding ? '正在重建并核对…' : '重建知识库索引' }}</button><button class="button button--outline button--wide" type="button" :disabled="loading" @click="load">刷新状态</button></aside>
    </div>
    <section class="panel"><div class="panel-head"><div><h3>索引边界</h3><p>规则知识库只提供检索上下文与引用。</p></div><ShieldCheck :size="18" /></div><div class="rule-line"><b>可做</b><span>检索平台规则、解释交易流程、给出引用</span></div><div class="rule-line"><b>不可做</b><span>决定成交、改动价格、扣款或执行账号处罚</span></div><div class="rule-line"><b>失败降级</b><span>索引不可用时助手明确说明，不编造规则答案</span></div></section>
    <div v-if="loadError" class="warm-note"><ShieldCheck :size="16" /><p>{{ loadError }}</p></div>
  </div>
</template>

<style scoped>
.knowledge-status-grid { display: grid; grid-template-columns: minmax(0, 1.55fr) minmax(280px, .55fr); gap: 12px; margin-bottom: 12px; }
.knowledge-main-card { display: flex; min-height: 220px; align-items: center; gap: 18px; padding: 24px; }
.knowledge-icon { display: grid; width: 58px; height: 58px; flex: 0 0 auto; place-items: center; border-radius: 50%; background: var(--jade-soft); color: var(--jade); }
.knowledge-main-card span { color: var(--muted); font-size: 12px; }
.knowledge-main-card strong { display: block; margin: 5px 0 9px; font-family: var(--font-display); font-size: 32px; font-weight: 500; }
.knowledge-main-card p { max-width: 680px; color: var(--muted); font-size: 14px; line-height: 1.7; }
.knowledge-meta { display: flex; align-items: center; gap: 10px; margin-top: 12px; }
.knowledge-action-card { align-self: start; padding: 18px; }
.knowledge-action-card > svg { color: var(--jade); }
.knowledge-action-card h3 { margin: 10px 0 6px; font-size: 20px; }
.knowledge-action-card p { margin-bottom: 14px; color: var(--muted); font-size: 14px; line-height: 1.65; }
.knowledge-action-card .button + .button { margin-top: 8px; }
.spinning { animation: spin .9s linear infinite; }
@keyframes spin { to { transform: rotate(360deg); } }
</style>
