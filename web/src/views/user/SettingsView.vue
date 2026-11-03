<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  Bell,
  Camera,
  KeyRound,
  Laptop,
  LogOut,
  ShieldCheck,
  Trash2,
  UserRound,
} from 'lucide-vue-next'
import PageHeading from '@/components/PageHeading.vue'
import StatusPill from '@/components/StatusPill.vue'
import { apiRequest, errorMessage } from '@/api/client'
import { readSession } from '@/api/session'
import { useAuthStore } from '@/stores/auth'
import type { UserSessionVO } from '@/types/api'
import { formatFullDate } from '@/utils/format'
import { uploadAvatar } from '@/utils/upload'

const auth = useAuthStore()
const router = useRouter()
const saving = ref(false)
const uploadingAvatar = ref(false)
const sessionsLoading = ref(false)
const activeTab = ref('资料与隐私')
const tabs = ['资料与隐私', '安全与会话', '通知偏好']
const displayName = ref('')
const chatEnabled = ref(false)
const avatarVersion = ref(Date.now())
const fileInput = ref<HTMLInputElement | null>(null)
const sessions = ref<UserSessionVO[]>([])
const passwordVisible = ref(false)
const passwordForm = reactive({ currentPassword: '', newPassword: '', confirmPassword: '' })
const notifications = reactive({ auctionEnding: true, orderPayment: true, messages: true })
const display = computed(() => auth.user)
const avatarUrl = computed(() => display.value?.avatarUrl ? `${display.value.avatarUrl}?v=${avatarVersion.value}` : null)
const currentSessionId = computed(() => readSession()?.sessionId || '')

function syncUser(): void {
  displayName.value = auth.user?.displayName || ''
  chatEnabled.value = auth.user?.chatEnabled || false
  notifications.auctionEnding = auth.user?.notifyAuctionEnding ?? true
  notifications.orderPayment = auth.user?.notifyOrderPayment ?? true
  notifications.messages = auth.user?.notifyMessages ?? true
}

async function saveProfile(): Promise<void> {
  if (saving.value) return
  const name = displayName.value.trim()
  if (!name) {
    ElMessage.warning('显示名称不能为空')
    return
  }
  saving.value = true
  try {
    await apiRequest<void>('/users/me', {
      method: 'PATCH',
      body: JSON.stringify({ displayName: name }),
    })
    await auth.refreshUser()
    syncUser()
    ElMessage.success('公开资料已更新')
  } catch (error) {
    ElMessage.error(errorMessage(error))
  } finally {
    saving.value = false
  }
}

function chooseAvatar(): void {
  fileInput.value?.click()
}

async function handleAvatar(event: Event): Promise<void> {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  if (!file || uploadingAvatar.value) return
  uploadingAvatar.value = true
  try {
    await uploadAvatar(file)
    await auth.refreshUser()
    avatarVersion.value = Date.now()
    ElMessage.success('头像已更新')
  } catch (error) {
    ElMessage.error(errorMessage(error))
  } finally {
    uploadingAvatar.value = false
  }
}

async function toggleChat(value: boolean): Promise<void> {
  saving.value = true
  try {
    await apiRequest<void>(`/users/me/chat-setting?enabled=${value}`, { method: 'PATCH' })
    await auth.refreshUser()
    ElMessage.success(value ? '已允许从商品发起新会话' : '已阻止新会话；已有订单沟通不受影响')
  } catch (error) {
    chatEnabled.value = !value
    ElMessage.error(errorMessage(error))
  } finally {
    saving.value = false
  }
}

async function saveNotifications(): Promise<void> {
  if (saving.value) return
  saving.value = true
  try {
    await apiRequest<void>('/users/me/notifications', {
      method: 'PUT',
      body: JSON.stringify(notifications),
    })
    await auth.refreshUser()
    syncUser()
    ElMessage.success('通知偏好已保存')
  } catch (error) {
    ElMessage.error(errorMessage(error))
  } finally {
    saving.value = false
  }
}

async function loadSessions(): Promise<void> {
  sessionsLoading.value = true
  try {
    sessions.value = await apiRequest<UserSessionVO[]>(`/auth/sessions?currentSessionId=${encodeURIComponent(currentSessionId.value)}`)
  } catch (error) {
    sessions.value = []
    ElMessage.error(errorMessage(error))
  } finally {
    sessionsLoading.value = false
  }
}

async function revokeSession(session: UserSessionVO): Promise<void> {
  if (session.current || saving.value) return
  saving.value = true
  try {
    await apiRequest<void>(`/auth/sessions/${encodeURIComponent(session.id)}`, { method: 'DELETE' })
    ElMessage.success(`已退出 ${session.deviceName}`)
    await loadSessions()
  } catch (error) {
    ElMessage.error(errorMessage(error))
  } finally {
    saving.value = false
  }
}

function openPasswordDialog(): void {
  passwordForm.currentPassword = ''
  passwordForm.newPassword = ''
  passwordForm.confirmPassword = ''
  passwordVisible.value = true
}

async function changePassword(): Promise<void> {
  if (saving.value) return
  if (passwordForm.newPassword.length < 8) {
    ElMessage.warning('新密码至少 8 位')
    return
  }
  if (passwordForm.newPassword !== passwordForm.confirmPassword) {
    ElMessage.warning('两次输入的新密码不一致')
    return
  }
  saving.value = true
  try {
    await apiRequest<void>('/auth/password', {
      method: 'PUT',
      body: JSON.stringify({
        currentPassword: passwordForm.currentPassword,
        newPassword: passwordForm.newPassword,
      }),
    })
    passwordVisible.value = false
    ElMessage.success('密码已修改，请使用新密码重新登录')
    await auth.logout()
    await router.push('/auth')
  } catch (error) {
    ElMessage.error(errorMessage(error))
  } finally {
    saving.value = false
  }
}

async function logout(): Promise<void> {
  await auth.logout()
  await router.push('/auth')
}

watch(activeTab, (tab) => {
  if (tab === '安全与会话') void loadSessions()
})

watch(() => auth.user, syncUser, { immediate: true })
onMounted(loadSessions)
</script>

<template>
  <div class="workspace-page">
    <PageHeading eyebrow="账号设置" title="把资料、安全和沟通边界放在一起" description="公开资料会用于交易沟通；设备会话和通知偏好都由你自行管理。" />
    <div class="settings-layout">
      <aside class="panel settings-nav"><button v-for="tab in tabs" :key="tab" :class="{ active: activeTab === tab }" type="button" @click="activeTab = tab"><UserRound v-if="tab === '资料与隐私'" :size="15" /><KeyRound v-else-if="tab === '安全与会话'" :size="15" /><Bell v-else :size="15" />{{ tab }}</button><div class="line-divider" /><button class="danger-text" type="button" @click="logout"><LogOut :size="14" /> 退出登录</button></aside>
      <section class="settings-main">
        <template v-if="activeTab === '资料与隐私'">
          <section v-if="display" class="panel settings-section">
            <div class="panel-head"><div><h3>公开资料</h3><p>显示名称会出现在买卖家沟通场景中。</p></div><UserRound :size="18" /></div>
            <div class="settings-avatar-row">
              <img v-if="avatarUrl" class="settings-avatar-image" :src="avatarUrl" alt="当前头像" />
              <span v-else class="avatar avatar--large">{{ display.displayName.slice(0, 1) }}</span>
              <div><b>{{ display.displayName }}</b><span>用户编号 {{ display.id }} · 信用 {{ display.creditScore }}</span></div>
              <button class="button button--outline button--with-icon" type="button" :disabled="uploadingAvatar" @click="chooseAvatar"><Camera :size="14" />{{ uploadingAvatar ? '正在上传…' : '更换头像' }}</button>
              <input ref="fileInput" class="hidden-file-input" type="file" accept="image/jpeg,image/png,image/webp" @change="handleAvatar" />
            </div>
            <div class="form-grid-two settings-form-grid"><label class="form-field"><span>显示名称</span><input v-model="displayName" maxlength="80" /></label><label class="form-field"><span>手机号</span><input :value="display.phone" disabled /></label></div>
            <button class="button button--dark" type="button" :disabled="saving" @click="saveProfile">{{ saving ? '正在保存…' : '保存公开资料' }}</button>
          </section>
          <section class="panel settings-section"><div class="panel-head"><div><h3>沟通与隐私</h3><p>开关只影响创建新会话，已有订单履约沟通仍可继续。</p></div><ShieldCheck :size="18" /></div><div class="setting-switch-row"><div><b>允许买家从商品发起会话</b><p>存在高风险或履约争议时，平台仍可保留沟通入口。</p></div><el-switch v-model="chatEnabled" :disabled="saving" @change="toggleChat" /></div></section>
        </template>

        <template v-else-if="activeTab === '安全与会话'">
          <section class="panel settings-section"><div class="panel-head"><div><h3>登录密码</h3><p>修改后当前设备和其他设备的刷新会话都会失去继续续期的能力。</p></div><KeyRound :size="18" /></div><div class="setting-switch-row"><div><b>定期更换高强度密码</b><p>密码至少 8 位，不应与其他网站重复。</p></div><button class="button button--outline" type="button" @click="openPasswordDialog">修改密码</button></div></section>
          <section v-loading="sessionsLoading" class="panel settings-section"><div class="panel-head"><div><h3>设备会话</h3><p>每条会话只能通过 Refresh Token 续期；退出设备后需要重新登录。</p></div><Laptop :size="18" /></div><div v-for="session in sessions" :key="session.id" class="device-session-row"><Laptop :size="17" /><div><b>{{ session.deviceName }}</b><span>创建 {{ formatFullDate(session.createdAt) }} · 最近使用 {{ formatFullDate(session.lastUsedAt) }}</span></div><StatusPill v-if="session.current" text="当前设备" tone="success" /><button v-else class="icon-button" type="button" title="退出该设备" :disabled="saving" @click="revokeSession(session)"><Trash2 :size="15" /></button></div><div v-if="!sessions.length && !sessionsLoading" class="empty-state"><Laptop :size="22" /><b>暂无其他设备</b><p>当前设备会保留在这里。</p></div></section>
        </template>

        <section v-else class="panel settings-section"><div class="panel-head"><div><h3>通知偏好</h3><p>这些选择会影响后续提醒，不会改变付款截止、库存释放或资金状态。</p></div><Bell :size="18" /></div><div class="setting-switch-row"><div><b>拍卖即将结束提醒</b><p>仅对正在参与或关注的拍品发送。</p></div><el-switch v-model="notifications.auctionEnding" /></div><div class="setting-switch-row"><div><b>订单付款提醒</b><p>按订单的真实付款截止时间发送。</p></div><el-switch v-model="notifications.orderPayment" /></div><div class="setting-switch-row"><div><b>新消息提醒</b><p>会话有新消息时提示；已读状态仍由你自己推进。</p></div><el-switch v-model="notifications.messages" /></div><button class="button button--dark settings-save-button" type="button" :disabled="saving" @click="saveNotifications">{{ saving ? '正在保存…' : '保存通知偏好' }}</button></section>
      </section>
    </div>

    <ElDialog v-model="passwordVisible" title="修改登录密码" width="520px">
      <div class="dialog-form"><label class="form-field"><span>当前密码</span><input v-model="passwordForm.currentPassword" type="password" autocomplete="current-password" /></label><label class="form-field"><span>新密码</span><input v-model="passwordForm.newPassword" type="password" autocomplete="new-password" /></label><label class="form-field"><span>确认新密码</span><input v-model="passwordForm.confirmPassword" type="password" autocomplete="new-password" /></label></div>
      <template #footer><button class="button button--outline" type="button" @click="passwordVisible = false">取消</button><button class="button button--dark" type="button" :disabled="saving" @click="changePassword">确认修改</button></template>
    </ElDialog>

  </div>
</template>

<style scoped>
.settings-avatar-image { width: 52px; height: 52px; flex: 0 0 auto; border-radius: 50%; object-fit: cover; border: 1px solid var(--line); }
.hidden-file-input { display: none; }
.settings-form-grid { margin-bottom: 4px; }
.settings-section > .button:not(.settings-save-button) { margin: 12px 0 0 15px; }
.settings-save-button { margin: 13px 15px 0; }
.device-session-row { display: flex; min-height: 64px; align-items: center; gap: 11px; padding: 10px 15px; border-bottom: 1px solid #e5e7e0; }
.device-session-row:last-child { border-bottom: 0; }
.device-session-row > div { min-width: 0; flex: 1; }
.device-session-row b, .device-session-row span { display: block; }
.device-session-row b { font-size: 14px; }
.device-session-row span { margin-top: 3px; color: var(--muted); font-size: 12px; }
</style>
