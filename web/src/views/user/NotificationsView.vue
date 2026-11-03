<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Bell, Gavel, MessageSquareText, PackageOpen, RefreshCw } from 'lucide-vue-next'
import PageHeading from '@/components/PageHeading.vue'
import StatusPill from '@/components/StatusPill.vue'
import EmptyState from '@/components/EmptyState.vue'
import { apiRequest, errorMessage } from '@/api/client'
import type { CursorPage, NotificationVO } from '@/types/api'
import { formatDateTime } from '@/utils/format'

const router = useRouter()
const loading = ref(true)
const loadingMore = ref(false)
const error = ref('')
const rows = ref<NotificationVO[]>([])
const nextCursor = ref<string | null>(null)
const unread = ref(0)

const unreadInList = computed(() => rows.value.filter((row) => !row.read).length)

function iconOf(type: string) {
  if (type === 'AUCTION') return Gavel
  if (type === 'ORDER') return PackageOpen
  return MessageSquareText
}

function toneOf(type: string): 'auction' | 'success' | 'neutral' {
  if (type === 'AUCTION') return 'auction'
  if (type === 'ORDER') return 'success'
  return 'neutral'
}

function labelOf(type: string): string {
  if (type === 'AUCTION') return '拍卖'
  if (type === 'ORDER') return '订单'
  return '消息'
}

// 通知只保存关联资源的聚合 ID，拍卖场次没有独立前端页面，统一回到拍卖大厅避免拼错路由。
function targetOf(row: NotificationVO): string | null {
  if (row.resourceType === 'ORDER') return `/orders/${row.resourceId}`
  if (row.resourceType === 'CONVERSATION') return '/messages'
  if (row.resourceType === 'AUCTION') return '/auctions'
  return null
}

async function loadUnread(): Promise<void> {
  try {
    const result = await apiRequest<{ unread: number }>('/notifications/unread-count')
    unread.value = Number(result.unread || 0)
    publishUnread()
  } catch {
    unread.value = 0
  }
}

function publishUnread(): void {
  window.dispatchEvent(new CustomEvent('lotflow:notification-unread-changed', { detail: unread.value }))
}

async function load(reset = true): Promise<void> {
  if (reset) {
    loading.value = true
    rows.value = []
    nextCursor.value = null
  } else {
    loadingMore.value = true
  }
  error.value = ''
  try {
    const cursor = !reset && nextCursor.value
      ? `&cursor=${encodeURIComponent(nextCursor.value)}`
      : ''
    const page = await apiRequest<CursorPage<NotificationVO>>(`/notifications?limit=20${cursor}`)
    rows.value = reset ? page.items : [...rows.value, ...page.items]
    nextCursor.value = page.nextCursor
  } catch (cause) {
    error.value = errorMessage(cause)
  } finally {
    loading.value = false
    loadingMore.value = false
  }
}

async function openNotification(row: NotificationVO): Promise<void> {
  if (!row.read) {
    // 已读只影响未读统计，写失败也要允许跳转，不能让一次标记失败挡住用户看订单。
    try {
      await apiRequest<void>(`/notifications/${row.id}/reads`, { method: 'POST' })
      row.read = true
      unread.value = Math.max(0, unread.value - 1)
      publishUnread()
    } catch (cause) {
      ElMessage.warning(errorMessage(cause))
    }
  }
  const target = targetOf(row)
  if (target) void router.push(target)
}

onMounted(async () => {
  await Promise.all([load(true), loadUnread()])
})
</script>

<template>
  <div class="workspace-page" v-loading="loading">
    <PageHeading eyebrow="通知" title="拍卖、订单和消息的变化都汇总在这里" description="通知只记录业务事实和跳转入口，不在通知正文里重复展示资金与聊天细节。">
      <template #actions>
        <button class="button button--outline button--with-icon" type="button" @click="load(true)">
          <RefreshCw :size="15" /> 刷新
        </button>
      </template>
    </PageHeading>

    <div class="summary-row">
      <div><span>未读</span><b>{{ unread }}</b></div>
      <div><span>本页通知</span><b>{{ rows.length }}</b></div>
      <div><span>本页未读</span><b>{{ unreadInList }}</b></div>
    </div>

    <div v-if="rows.length" class="notification-list">
      <button
        v-for="row in rows"
        :key="row.id"
        class="panel notification-row"
        :class="{ 'notification-row--unread': !row.read }"
        type="button"
        @click="openNotification(row)"
      >
        <span class="notification-row__icon"><component :is="iconOf(row.type)" :size="18" /></span>
        <span class="notification-row__main">
          <span class="notification-row__tags">
            <StatusPill :text="labelOf(row.type)" :tone="toneOf(row.type)" />
            <StatusPill v-if="!row.read" text="未读" tone="warning" />
          </span>
          <b>{{ row.title }}</b>
          <span class="notification-row__content">{{ row.content }}</span>
        </span>
        <span class="notification-row__time">{{ formatDateTime(row.occurredAt) }}</span>
      </button>
    </div>
    <EmptyState
      v-else
      :title="error ? '通知暂时无法读取' : '当前没有通知'"
      :description="error || '拍卖结束、订单待支付或收到新消息时，这里会出现提醒。'"
      :action-text="error ? '重新加载' : ''"
      @action="load(true)"
    />

    <div v-if="nextCursor" class="load-more-wrap">
      <button class="button button--outline" type="button" :disabled="loadingMore" @click="load(false)">
        {{ loadingMore ? '正在加载…' : '加载更早通知' }}
      </button>
    </div>
  </div>
</template>

<style scoped>
.notification-list { display: grid; gap: 10px; margin-top: 16px; }
.notification-row { display: grid; width: 100%; grid-template-columns: auto minmax(0, 1fr) auto; gap: 14px; align-items: center; padding: 16px 18px; text-align: left; cursor: pointer; border: 1px solid var(--line); background: var(--surface); }
.notification-row--unread { border-color: var(--accent-soft, var(--line)); box-shadow: inset 3px 0 0 var(--accent, #b4532a); }
.notification-row__icon { display: grid; width: 38px; height: 38px; place-items: center; border-radius: 4px; background: var(--surface-muted); }
.notification-row__main { display: grid; min-width: 0; gap: 4px; }
.notification-row__tags { display: flex; gap: 6px; }
.notification-row__main b { font-size: 14px; }
.notification-row__content { overflow: hidden; color: var(--muted); font-size: 12px; text-overflow: ellipsis; white-space: nowrap; }
.notification-row__time { color: var(--muted); font-size: 12px; white-space: nowrap; }
.load-more-wrap { display: flex; justify-content: center; margin-top: 20px; }
@media (max-width: 760px) { .notification-row { grid-template-columns: auto minmax(0, 1fr); } .notification-row__time { grid-column: 2; } }
</style>
