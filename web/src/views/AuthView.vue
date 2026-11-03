<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { ArrowRight, Eye } from 'lucide-vue-next'
import BrandMark from '@/components/BrandMark.vue'
import { errorMessage } from '@/api/client'
import { useAuthStore } from '@/stores/auth'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const mode = ref<'login' | 'register'>('login')
const showPassword = ref(false)
const submitting = ref(false)
const form = reactive({ phone: '', password: '', nickname: '' })
const formTitle = computed(() => (mode.value === 'login' ? '欢迎回来' : '从一件旧物开始'))

async function submit(): Promise<void> {
  if (submitting.value) return
  if (!/^1[3-9]\d{9}$/.test(form.phone)) {
    ElMessage.warning('请输入 11 位中国大陆手机号')
    return
  }
  if (form.password.length < 8) {
    ElMessage.warning('密码至少 8 位')
    return
  }
  submitting.value = true
  try {
    if (mode.value === 'login') await auth.login(form.phone, form.password)
    else await auth.register(form.phone, form.password, form.nickname.trim() || `用户${form.phone.slice(-4)}`)
    ElMessage.success(mode.value === 'login' ? '登录成功' : '注册并登录成功')
    // 管理员登录后默认进运营后台，普通用户仍回工作台；已带 redirect 时优先尊重原目标。
    const redirect = String(route.query.redirect || (auth.isAdmin ? '/admin' : '/workspace'))
    await router.push(redirect)
  } catch (error) {
    ElMessage.error(errorMessage(error))
  } finally {
    submitting.value = false
  }
}

</script>

<template>
  <div class="auth-page">
    <section class="auth-form-panel">
      <div class="auth-form-card">
        <div class="auth-form-card__top"><BrandMark /><RouterLink to="/">返回首页</RouterLink></div>
        <h2>{{ formTitle }}</h2>
        <p>{{ mode === 'login' ? '登录后继续管理你的拍卖、订单和钱包。' : '注册后，商品仍必须先通过拍卖渠道发布。' }}</p>
        <div class="segmented-control"><button :class="{ active: mode === 'login' }" type="button" @click="mode = 'login'">登录</button><button :class="{ active: mode === 'register' }" type="button" @click="mode = 'register'">注册</button></div>
        <label class="form-field"><span>手机号</span><input v-model="form.phone" autocomplete="tel" inputmode="numeric" placeholder="11 位手机号" /></label>
        <label v-if="mode === 'register'" class="form-field"><span>昵称</span><input v-model="form.nickname" autocomplete="nickname" maxlength="80" placeholder="交易中展示的昵称" /></label>
        <label class="form-field"><span>密码</span><div class="password-input"><input v-model="form.password" :type="showPassword ? 'text' : 'password'" autocomplete="current-password" placeholder="至少 8 位" /><button type="button" title="显示密码" @click="showPassword = !showPassword"><Eye :size="16" /></button></div></label>
        <button class="button button--dark button--wide auth-submit" type="button" :disabled="submitting" @click="submit">{{ submitting ? '正在提交…' : mode === 'login' ? '登录' : '注册并登录' }}<ArrowRight :size="15" /></button>
      </div>
    </section>
  </div>
</template>
