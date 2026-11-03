<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Archive, Gavel, TimerReset, Zap } from 'lucide-vue-next'
import PageHeading from '@/components/PageHeading.vue'
import CountdownText from '@/components/CountdownText.vue'
import StatusPill from '@/components/StatusPill.vue'
import { apiRequest, errorMessage } from '@/api/client'
import type { ListingVO, SaleChannelVO } from '@/types/api'
import { channelText, formatFullDate, formatMoney, statusText } from '@/utils/format'

type Decision = 'REAUCTION' | 'SECKILL' | 'RETURN_INVENTORY'
const route = useRoute()
const router = useRouter()
const listing = ref<ListingVO | null>(null)
const channels = ref<SaleChannelVO[]>([])
const loading = ref(true)
const loadError = ref('')
const selected = ref<Decision>('SECKILL')
const seckillPrice = ref('')
const submitting = ref(false)
const options = [
  { value: 'REAUCTION' as const, title: '重新拍卖', description: '按新的排期再次进入拍场，历史流拍记录保留。', icon: Gavel },
  { value: 'SECKILL' as const, title: '进入 24h 秒杀', description: '仅 1 件库存、无买家保证金，下单锁定 15 分钟。', icon: Zap },
  { value: 'RETURN_INVENTORY' as const, title: '退回库存', description: '不进入其他销售渠道，等待你下一步处置。', icon: Archive },
]
const decisionDeadline = computed(() => {
  const auction = channels.value
    .filter((channel) => channel.channel === 'AUCTION' && channel.status === 'UNSOLD' && channel.endsAt)
    .sort((left, right) => new Date(right.endsAt).getTime() - new Date(left.endsAt).getTime())[0]
  if (!auction) return null
  return new Date(new Date(auction.endsAt).getTime() + 24 * 60 * 60_000).toISOString()
})

async function load(): Promise<void> {
  loading.value = true
  loadError.value = ''
  try {
    const listingId = String(route.params.id)
    const [detail, history] = await Promise.all([
      apiRequest<ListingVO>(`/listings/${listingId}/mine`),
      apiRequest<SaleChannelVO[]>(`/listings/${listingId}/channels`).catch(() => []),
    ])
    listing.value = detail
    channels.value = history
    seckillPrice.value = detail.seckillPrice || ''
    if (!detail.seckillPrice) selected.value = 'RETURN_INVENTORY'
  } catch (error) {
    listing.value = null
    channels.value = []
    loadError.value = errorMessage(error)
  } finally {
    loading.value = false
  }
}

async function submit(): Promise<void> {
  if (!listing.value || submitting.value) return
  if (selected.value === 'SECKILL' && !/^\d+(\.\d{1,2})?$/.test(seckillPrice.value)) {
    ElMessage.warning('请输入有效的秒杀价')
    return
  }
  submitting.value = true
  try {
    await apiRequest<void>(`/listings/${listing.value.id}/unsold-decisions`, {
      method: 'POST',
      body: JSON.stringify({ decision: selected.value, seckillPrice: selected.value === 'SECKILL' ? seckillPrice.value : null }),
    })
    ElMessage.success('流拍处置已提交')
    await router.push(selected.value === 'REAUCTION'
      ? `/workspace/listings/${listing.value.id}`
      : '/workspace/listings')
  } catch (error) {
    ElMessage.error(errorMessage(error))
  } finally {
    submitting.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="workspace-page" v-loading="loading">
    <template v-if="listing">
      <PageHeading eyebrow="流拍处置" :title="listing.title" description="未授权自动降级的商品，流拍后只有 24 小时卖家决策窗口。">
        <template #actions><StatusPill :text="statusText(listing.status)" :tone="listing.status === 'WAITING_SELLER_DECISION' ? 'warning' : 'neutral'" /></template>
      </PageHeading>
      <div class="deadline-band"><div v-if="decisionDeadline"><span>决策截止时间</span><CountdownText :deadline="decisionDeadline" large /></div><div v-else><span>决策截止时间</span><b>等待平台确认</b><p>最新渠道记录尚未包含拍卖结束时间；截止时间确认后才会显示倒计时。</p></div><div><b>超时规则</b><p>超时未选择时自动退回库存，截止时间不可由页面延长。</p></div><TimerReset :size="30" /></div>
      <section class="decision-options">
        <button v-for="option in options" :key="option.value" class="decision-option" :class="{ selected: selected === option.value }" type="button" @click="selected = option.value">
          <span class="decision-option__icon"><component :is="option.icon" :size="20" /></span>
          <StatusPill v-if="option.value === 'SECKILL'" :text="listing.seckillPrice ? '已配置秒杀价' : '需填写秒杀价'" :tone="listing.seckillPrice ? 'success' : 'warning'" />
          <h3>{{ option.title }}</h3><p>{{ option.description }}</p><span class="decision-option__select">{{ selected === option.value ? '已选择' : '选择此方案' }}</span>
        </button>
      </section>
      <div class="decision-form-grid">
        <section class="panel">
          <div class="panel-head"><h3>价格与渠道确认</h3><StatusPill text="平台最终校验" tone="success" /></div>
          <dl class="detail-list"><div><dt>原起拍价</dt><dd>{{ formatMoney(listing.startPrice) }}</dd></div><div><dt>当前状态</dt><dd>{{ statusText(listing.status) }}</dd></div><div><dt>商品库存</dt><dd>1 件</dd></div><div><dt>已授权自动降级</dt><dd>{{ listing.autoDowngrade ? '是' : '否' }}</dd></div></dl>
          <label v-if="selected === 'SECKILL'" class="form-field"><span>秒杀价</span><input v-model="seckillPrice" inputmode="decimal" /><small>必须大于 0 且不高于原起拍价 {{ formatMoney(listing.startPrice) }}</small></label>
          <div v-if="selected === 'REAUCTION'" class="info-panel"><h3>重新排期</h3><p>提交选择后会进入拍品管理页，由你确认新的开拍时间；平台不会自动替你安排场次。</p></div>
          <button class="button button--accent button--wide" type="button" :disabled="submitting || listing.status !== 'WAITING_SELLER_DECISION'" @click="submit">{{ submitting ? '正在提交…' : '确认流拍处置' }}</button>
        </section>
        <aside class="panel side-explainer"><h3>当前渠道记录</h3><div v-for="channel in channels" :key="channel.id" class="rule-line"><b>{{ channelText(channel.channel) }} · {{ statusText(channel.status) }}</b><span>{{ formatMoney(channel.price) }} · {{ formatFullDate(channel.endsAt) }}</span></div><div v-if="!channels.length" class="empty-state"><TimerReset :size="20" /><b>暂无渠道历史</b><p>请稍后重试或确认商品归属。</p></div><div class="rule-line"><b>同一库存单元</b><span>不能同时进入秒杀和商城</span></div><div class="rule-line"><b>最终校验</b><span>平台会核验状态、归属与价格链</span></div></aside>
      </div>
    </template>
    <div v-else-if="!loading" class="panel empty-state"><TimerReset :size="24" /><b>流拍商品暂时无法读取</b><p>{{ loadError || '请确认商品和登录状态。' }}</p><RouterLink class="button button--outline" to="/workspace/listings">返回我的拍品</RouterLink></div>
  </div>
</template>
