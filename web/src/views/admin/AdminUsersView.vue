<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { CircleUserRound, Search, ShieldCheck } from 'lucide-vue-next'
import PageHeading from '@/components/PageHeading.vue'
import StatusPill from '@/components/StatusPill.vue'
import { apiRequest, errorMessage } from '@/api/client'
import type { AdminUserVO, CursorPage } from '@/types/api'
import { statusText } from '@/utils/format'

const loading = ref(true)
const loadError = ref('')
const users = ref<AdminUserVO[]>([])
const search = ref('')
const statusFilter = ref('全部状态')
const adjustmentVisible = ref(false)
const selectedUser = ref<AdminUserVO | null>(null)
const submitting = ref(false)
const adjustment = reactive({ changeValue: -5, reason: '' })
const visible = computed(() => users.value.filter((user) => `${user.id}${user.phone}${user.displayName}`.includes(search.value.trim()) && (statusFilter.value === '全部状态' || statusText(user.status) === statusFilter.value)))
const restrictedCount = computed(() => users.value.filter((user) => user.status !== 'ACTIVE').length)
const lowCreditCount = computed(() => users.value.filter((user) => user.creditScore < 80).length)
const completedReviewCount = computed(() => users.value.reduce((total, user) => total + user.completedReviewCount, 0))

async function load(): Promise<void> {
  loading.value = true
  loadError.value = ''
  try {
    const page = await apiRequest<CursorPage<AdminUserVO>>('/admin/users?limit=50')
    users.value = page.items
  } catch (error) {
    users.value = []
    loadError.value = errorMessage(error)
  } finally {
    loading.value = false
  }
}

function openAdjustment(user: AdminUserVO): void {
  selectedUser.value = user
  adjustment.changeValue = -5
  adjustment.reason = ''
  adjustmentVisible.value = true
}

async function adjustCredit(): Promise<void> {
  if (!selectedUser.value || !adjustment.reason.trim() || submitting.value) {
    ElMessage.warning('请填写信用调整理由')
    return
  }
  submitting.value = true
  try {
    await apiRequest<void>(`/admin/users/${selectedUser.value.id}/credit-adjustments`, {
      method: 'POST',
      body: JSON.stringify({ changeValue: adjustment.changeValue, reason: adjustment.reason.trim() }),
    })
    ElMessage.success('信用调整已提交并写入审计')
    adjustmentVisible.value = false
    await load()
  } catch (error) {
    ElMessage.error(errorMessage(error))
  } finally {
    submitting.value = false
  }
}

async function changeStatus(user: AdminUserVO, status: 'ACTIVE' | 'RESTRICTED' | 'DISABLED'): Promise<void> {
  try {
    await apiRequest<void>(`/admin/users/${user.id}/status`, {
      method: 'POST',
      body: JSON.stringify({ status, reason: `管理员人工调整：${statusText(user.status)} → ${statusText(status)}` }),
    })
    ElMessage.success('账号状态已更新并记录管理员操作')
    await load()
  } catch (error) {
    ElMessage.error(errorMessage(error))
  }
}

onMounted(load)
</script>

<template>
  <div class="admin-page" v-loading="loading">
    <PageHeading eyebrow="USERS & CREDIT" title="用户与信用" description="信用与账号处置只能由管理员人工提交，必须保留原因与操作人。">
      <template #actions><StatusPill text="人工处置" tone="warning" /></template>
    </PageHeading>
    <div class="admin-metric-grid admin-metric-grid--small"><div class="panel admin-metric"><span>用户总数</span><strong>{{ users.length }}</strong><p>当前返回结果</p></div><div class="panel admin-metric"><span>受限账号</span><strong>{{ restrictedCount }}</strong><p>非正常状态</p></div><div class="panel admin-metric"><span>低信用用户</span><strong>{{ lowCreditCount }}</strong><p>信用分低于 80</p></div><div class="panel admin-metric"><span>累计审核</span><strong>{{ completedReviewCount }}</strong><p>当前返回用户的已完成审核</p></div></div>
    <div class="toolbar panel"><div class="filter-chips"><button v-for="item in ['全部状态', '正常', '已限制', '已停用']" :key="item" class="filter-chip" :class="{ active: statusFilter === item }" type="button" @click="statusFilter = item">{{ item }}</button></div><label class="inline-search"><Search :size="14" /><input v-model="search" placeholder="搜索用户编号、手机号或昵称" /></label></div>
    <div class="table-panel">
      <table class="data-table admin-table"><thead><tr><th>用户</th><th>状态</th><th>信用分</th><th>已完成审核</th><th>风险提示</th><th>操作</th></tr></thead><tbody><tr v-for="user in visible" :key="user.id"><td><div class="user-cell"><span class="avatar avatar--small">{{ user.displayName.slice(0, 1) }}</span><div><b>{{ user.displayName }}</b><span>{{ user.phone }} · 编号 {{ user.id }}</span></div></div></td><td><StatusPill :text="statusText(user.status)" :tone="user.status === 'ACTIVE' ? 'success' : user.status === 'RESTRICTED' ? 'warning' : 'danger'" /></td><td><span :class="{ 'red-text': user.creditScore < 80 }">{{ user.creditScore }}</span></td><td>{{ user.completedReviewCount }}</td><td><span class="muted-note">{{ user.creditScore < 80 ? '低信用 · 需人工确认' : '无高风险标签' }}</span></td><td><div class="row-actions"><button class="button button--small button--outline" type="button" @click="openAdjustment(user)">调整信用</button><button v-if="user.status === 'ACTIVE'" class="button button--small button--ghost" type="button" @click="changeStatus(user, 'RESTRICTED')">限制</button><button v-else class="button button--small button--ghost" type="button" @click="changeStatus(user, 'ACTIVE')">恢复</button></div></td></tr></tbody></table>
      <div v-if="!visible.length" class="empty-state"><Search :size="23" /><b>{{ loadError ? '用户列表读取失败' : '没有匹配用户' }}</b><p>{{ loadError || '调整状态或搜索条件后再试。' }}</p><button v-if="loadError" class="button button--outline" type="button" @click="load">重新加载</button></div>
    </div>
    <ElDialog v-model="adjustmentVisible" title="人工调整信用" width="540px"><div class="dialog-form"><div class="dialog-user"><CircleUserRound :size="20" /><div><b>{{ selectedUser?.displayName }}</b><span>当前信用 {{ selectedUser?.creditScore }}</span></div></div><label class="form-field"><span>变化值</span><input v-model.number="adjustment.changeValue" type="number" min="-100" max="100" /></label><label class="form-field"><span>调整理由</span><textarea v-model="adjustment.reason" rows="4" maxlength="500" placeholder="调整理由会进入信用审计，AI 不会自动修改信用" /></label><div class="warm-note"><ShieldCheck :size="16" /><p>信用调整可能影响发布和出价权限，必须由人工二次确认。</p></div></div><template #footer><button class="button button--outline" type="button" @click="adjustmentVisible = false">取消</button><button class="button button--danger" type="button" :disabled="submitting" @click="adjustCredit">确认调整</button></template></ElDialog>
  </div>
</template>
