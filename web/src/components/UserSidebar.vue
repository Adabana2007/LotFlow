<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { RouterLink, useRoute } from 'vue-router'
import {
  ArrowLeft,
  Bell,
  Bot,
  Gavel,
  LayoutDashboard,
  MessageSquareText,
  PackageOpen,
  Settings2,
  WalletCards,
} from 'lucide-vue-next'
import { useAuthStore } from '@/stores/auth'
import { apiRequest } from '@/api/client'
import type { ConversationSummaryVO, CursorPage } from '@/types/api'

const route = useRoute()
const auth = useAuthStore()
const unreadCount = ref(0)
const notificationUnread = ref(0)

const items = [
  { label: '工作台', to: '/workspace', icon: LayoutDashboard },
  { label: '我的拍品', to: '/workspace/listings', icon: PackageOpen },
  { label: '我的出价', to: '/workspace/bids', icon: Gavel },
  { label: '订单', to: '/orders', icon: PackageOpen },
  { label: '钱包', to: '/wallet', icon: WalletCards },
  { label: '消息', to: '/messages', icon: MessageSquareText },
  { label: '通知', to: '/notifications', icon: Bell },
  { label: 'AI 助手', to: '/assistant', icon: Bot },
  { label: '账号设置', to: '/settings', icon: Settings2 },
]

const name = computed(() => auth.displayName)
const credit = computed(() => auth.user?.creditScore ?? '—')

function active(path: string): boolean {
  return route.path === path || (path !== '/workspace' && route.path.startsWith(path))
}

function badgeOf(path: string): number {
  if (path === '/messages') return unreadCount.value
  if (path === '/notifications') return notificationUnread.value
  return 0
}

function onUnreadChanged(event: Event): void {
  unreadCount.value = Number((event as CustomEvent<number>).detail || 0)
}

function onNotificationUnreadChanged(event: Event): void {
  notificationUnread.value = Number((event as CustomEvent<number>).detail || 0)
}

onMounted(async () => {
  window.addEventListener('lotflow:unread-changed', onUnreadChanged)
  window.addEventListener('lotflow:notification-unread-changed', onNotificationUnreadChanged)
  try {
    const page = await apiRequest<CursorPage<ConversationSummaryVO>>('/conversations?limit=100')
    unreadCount.value = page.items.reduce((sum, item) => sum + item.unreadCount, 0)
  } catch {
    unreadCount.value = 0
  }
  try {
    const result = await apiRequest<{ unread: number }>('/notifications/unread-count')
    notificationUnread.value = Number(result.unread || 0)
  } catch {
    notificationUnread.value = 0
  }
})

onBeforeUnmount(() => {
  window.removeEventListener('lotflow:unread-changed', onUnreadChanged)
  window.removeEventListener('lotflow:notification-unread-changed', onNotificationUnreadChanged)
})
</script>

<template>
  <aside class="user-sidebar panel">
    <div class="sidebar-user">
      <span class="avatar">{{ name.slice(0, 1) }}</span>
      <div>
        <b>{{ name }}</b>
        <span>信用 {{ credit }} · 普通卖家</span>
      </div>
    </div>
    <nav class="sidebar-nav" aria-label="用户中心导航">
      <RouterLink
        v-for="item in items"
        :key="item.to"
        :to="item.to"
        class="sidebar-nav__item"
        :class="{ active: active(item.to) }"
      >
        <component :is="item.icon" :size="16" />
        <span>{{ item.label }}</span>
        <b v-if="badgeOf(item.to)" class="sidebar-nav__badge">{{ badgeOf(item.to) > 99 ? '99+' : badgeOf(item.to) }}</b>
      </RouterLink>
    </nav>
    <div class="sidebar-divider" />
    <RouterLink class="sidebar-back" to="/">
      <ArrowLeft :size="14" /> 返回公开站点
    </RouterLink>
  </aside>
</template>
