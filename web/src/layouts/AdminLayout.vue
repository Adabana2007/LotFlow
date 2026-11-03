<script setup lang="ts">
import { computed, type Component } from 'vue'
import { RouterLink, RouterView, useRoute, useRouter } from 'vue-router'
import {
  Bot,
  BookOpenCheck,
  CircleUserRound,
  ClipboardCheck,
  LayoutDashboard,
  LogOut,
  MessageSquareWarning,
  PackageCheck,
  PackageSearch,
  ShieldAlert,
  WalletCards,
} from 'lucide-vue-next'
import BrandMark from '@/components/BrandMark.vue'
import { useAuthStore } from '@/stores/auth'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

interface AdminNavItem {
  label: string
  to: string
  icon: Component
  exact?: boolean
}

interface AdminNavGroup {
  label: string
  items: AdminNavItem[]
}

const groups: AdminNavGroup[] = [
  {
    label: '运营中心',
    items: [{ label: '运营总览', to: '/admin', icon: LayoutDashboard, exact: true }],
  },
  {
    label: '交易治理',
    items: [
      { label: '商品审核', to: '/admin/reviews', icon: ClipboardCheck },
      { label: '聊天风险', to: '/admin/risk', icon: ShieldAlert },
      { label: '交易争议', to: '/admin/disputes', icon: MessageSquareWarning },
    ],
  },
  {
    label: '业务数据',
    items: [
      { label: '订单运营', to: '/admin/orders', icon: PackageSearch },
      { label: '库存诊断', to: '/admin/inventory', icon: PackageCheck },
      { label: '资金审计', to: '/admin/finance', icon: WalletCards },
      { label: '用户与信用', to: '/admin/users', icon: CircleUserRound },
    ],
  },
  {
    label: '系统审计',
    items: [
      { label: 'AI 使用审计', to: '/admin/ai-logs', icon: Bot },
      { label: '知识库索引', to: '/admin/knowledge', icon: BookOpenCheck },
    ],
  },
]

const currentTitle = computed(() => String(route.meta.title || '运营后台'))

function isActive(path: string, exact = false): boolean {
  if (exact) return route.path === path
  return route.path === path || route.path.startsWith(`${path}/`)
}

async function logout(): Promise<void> {
  await auth.logout()
  await router.push('/auth')
}
</script>

<template>
  <div class="admin-frame">
    <aside class="admin-sidebar">
      <div class="admin-sidebar__brand"><BrandMark light /></div>
      <div class="admin-environment"><i /> 运营控制台</div>
      <nav class="admin-nav">
        <div v-for="group in groups" :key="group.label" class="admin-nav__group">
          <span class="admin-nav__label">{{ group.label }}</span>
          <RouterLink
            v-for="item in group.items"
            :key="item.to"
            :to="item.to"
            class="admin-nav__item"
            :class="{ active: isActive(item.to, item.exact) }"
          >
            <component :is="item.icon" :size="16" />
            <span>{{ item.label }}</span>
          </RouterLink>
        </div>
      </nav>
      <div class="admin-sidebar__footer">
        <span class="avatar avatar--dark">{{ auth.displayName.slice(0, 1) }}</span>
        <div><b>{{ auth.displayName }}</b><span>运营管理员 · 平台运营</span></div>
        <button class="icon-button icon-button--dark" type="button" title="退出登录" @click="logout">
          <LogOut :size="15" />
        </button>
      </div>
    </aside>
    <div class="admin-main">
      <header class="admin-header">
        <div><span class="admin-header__crumb">运营后台 /</span><b>{{ currentTitle }}</b></div>
        <div class="admin-header__right">
          <RouterLink class="button button--outline button--with-icon" to="/admin/assistant"><Bot :size="15" /> AI 治理助手</RouterLink>
        </div>
      </header>
      <main class="admin-content"><RouterView /></main>
    </div>
  </div>
</template>
