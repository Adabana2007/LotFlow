<script setup lang="ts">
import { RouterLink } from 'vue-router'
import { ArrowLeft } from 'lucide-vue-next'
import BrandMark from '@/components/BrandMark.vue'
</script>

<template>
  <div class="not-found-page">
    <BrandMark />
    <span class="eyebrow">404 / NOT FOUND</span>
    <h1>这页没有被拍卖出去</h1>
    <p>路由不存在，但首页、拍场、秒杀、商城和 AI 找物仍然可以使用。</p>
    <RouterLink class="button button--dark button--with-icon" to="/"><ArrowLeft :size="15" />返回首页</RouterLink>
  </div>
</template>
