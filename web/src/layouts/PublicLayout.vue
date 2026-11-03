<script setup lang="ts">
import { RouterView } from 'vue-router'
import PublicHeader from '@/components/PublicHeader.vue'
</script>

<template>
  <div class="app-frame">
    <PublicHeader />
    <main class="public-main">
      <RouterView />
    </main>
    <footer class="site-footer">
      <div class="site-footer__inner">
        <span>LotFlow · 拍卖不是终点，是物品重新被看见的方式。</span>
        <span>平台虚拟钱包 · 不连接银行卡或第三方支付</span>
      </div>
    </footer>
  </div>
</template>
