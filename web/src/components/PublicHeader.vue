<script setup lang="ts">
import { computed } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import { LogIn, Search, ShieldCheck, Sparkles } from 'lucide-vue-next'
import BrandMark from './BrandMark.vue'
import { useAuthStore } from '@/stores/auth'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

const navItems = [
  { label: '拍场', to: '/auctions' },
  { label: '秒杀', to: '/seckill' },
  { label: '一口价商城', to: '/shop' },
  { label: 'AI 找物', to: '/search' },
]

const userLabel = computed(() => auth.displayName)
const showAdmin = computed(() => auth.isAdmin)

function isActive(path: string): boolean {
  return route.path === path || (path !== '/' && route.path.startsWith(path))
}

function openWorkspace(): void {
  if (!auth.isAuthenticated) {
    router.push('/auth')
    return
  }
  // 管理员没有用户中心，头像入口固定回运营后台。
  router.push(auth.isAdmin ? '/admin' : '/workspace')
}
</script>

<template>
  <header class="public-header">
    <div class="public-header__inner">
      <BrandMark />
      <nav class="primary-nav" aria-label="主导航">
        <RouterLink
          v-for="item in navItems"
          :key="item.to"
          :to="item.to"
          class="primary-nav__link"
          :class="{ active: isActive(item.to) }"
        >
          {{ item.label }}
        </RouterLink>
      </nav>
      <div class="public-header__actions">
        <RouterLink class="icon-button" to="/search" aria-label="搜索">
          <Search :size="17" />
        </RouterLink>
        <RouterLink class="button button--ghost button--with-icon" to="/assistant">
          <Sparkles :size="15" />
          AI 助手
        </RouterLink>
        <RouterLink v-if="showAdmin" class="button button--ghost button--with-icon" to="/admin">
          <ShieldCheck :size="15" />
          运营后台
        </RouterLink>
        <button v-if="auth.isAuthenticated" class="header-user" type="button" @click="openWorkspace">
          <span class="avatar avatar--small">{{ userLabel.slice(0, 1) }}</span>
          <span>{{ userLabel }}</span>
        </button>
        <RouterLink v-else class="button button--outline button--with-icon" to="/auth">
          <LogIn :size="15" />
          登录 / 注册
        </RouterLink>
      </div>
    </div>
  </header>
</template>
