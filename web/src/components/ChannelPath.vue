<script setup lang="ts">
import { ArrowRight } from 'lucide-vue-next'

const flowStages = [
  { channel: 'AUCTION', caption: '拍卖', index: '01', name: '拍卖', detail: '先由市场验证价值', note: '起拍价是唯一公开起点' },
  { channel: 'SECKILL', caption: '秒杀', index: '02', name: '24h 秒杀', detail: '流拍后快速再尝试', note: '单件库存，无买家保证金' },
  { channel: 'SHOP', caption: '商城', index: '03', name: '30d 商城', detail: '一口价持续展示', note: '仍未售出则退回库存' },
] as const
</script>

<template>
  <div class="channel-path">
    <template v-for="(stage, index) in flowStages" :key="stage.channel">
      <div class="channel-node" :class="`channel-node--${stage.channel.toLowerCase()}`">
        <span>{{ stage.index }} · {{ stage.caption }}</span>
        <h3>{{ stage.name }}</h3>
        <p>{{ stage.detail }}<br />{{ stage.note }}</p>
      </div>
      <ArrowRight v-if="index < flowStages.length - 1" class="channel-path__arrow" :size="18" />
    </template>
  </div>
</template>
