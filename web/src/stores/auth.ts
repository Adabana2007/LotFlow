import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { apiRequest, type TokenResponse } from '@/api/client'
import { clearSession, readSession, writeSession } from '@/api/session'
import type { UserVO } from '@/types/api'

export const useAuthStore = defineStore('auth', () => {
  const user = ref<UserVO | null>(null)
  const accessToken = ref<string | null>(null)
  const loading = ref(false)
  const initialized = ref(false)

  const isAuthenticated = computed(() => Boolean(accessToken.value))
  const isAdmin = computed(() => Boolean(user.value?.roles.includes('ADMIN')))
  const displayName = computed(() => user.value?.displayName || '访客')

  async function restore(): Promise<void> {
    if (initialized.value) return
    const session = readSession()
    accessToken.value = session?.accessToken || null
    if (accessToken.value) {
      try {
        user.value = await apiRequest<UserVO>('/users/me')
      } catch {
        clearSession()
        accessToken.value = null
      }
    }
    initialized.value = true
  }

  async function login(phone: string, password: string, deviceName = '浏览器会话'): Promise<void> {
    loading.value = true
    try {
      const tokens = await apiRequest<TokenResponse>('/auth/tokens', {
        method: 'POST',
        body: JSON.stringify({ phone, password, deviceName }),
      }, false)
      writeSession(tokens)
      accessToken.value = tokens.accessToken
      user.value = await apiRequest<UserVO>('/users/me')
    } finally {
      loading.value = false
    }
  }

  async function register(phone: string, password: string, nickname: string): Promise<void> {
    loading.value = true
    try {
      await apiRequest<void>('/users', {
        method: 'POST',
        body: JSON.stringify({ phone, password, nickname }),
      })
      await login(phone, password)
    } finally {
      loading.value = false
    }
  }

  async function refreshUser(): Promise<void> {
    if (!accessToken.value) return
    user.value = await apiRequest<UserVO>('/users/me')
  }

  async function logout(): Promise<void> {
    const session = readSession()
    if (session?.refreshToken) {
      try {
        await apiRequest<void>('/auth/tokens/current', {
          method: 'DELETE',
          body: JSON.stringify({ refreshToken: session.refreshToken }),
        })
      } catch {
        // Local logout still revokes the browser session when the token is already expired.
      }
    }
    clearSession()
    accessToken.value = null
    user.value = null
  }

  return {
    user,
    accessToken,
    loading,
    initialized,
    isAuthenticated,
    isAdmin,
    displayName,
    restore,
    login,
    register,
    refreshUser,
    logout,
  }
})
