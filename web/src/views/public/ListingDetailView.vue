<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { BadgeCheck, Bot, MessagesSquare, ShieldCheck, Sparkles } from 'lucide-vue-next'
import ProductArtwork from '@/components/ProductArtwork.vue'
import CountdownText from '@/components/CountdownText.vue'
import StatusPill from '@/components/StatusPill.vue'
import EmptyState from '@/components/EmptyState.vue'
import { apiRequest, createIdempotencyKey, errorMessage } from '@/api/client'
import { useAuthStore } from '@/stores/auth'
import type { AuctionVO, ConversationVO, CursorPage, ListingVO, OrderVO } from '@/types/api'
import type { ChannelType, MarketLot } from '@/types/catalog'
import { channelForStatus, listingToMarketLot } from '@/utils/catalog'
import { formatMoney, statusText } from '@/utils/format'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const loading = ref(true)
const acting = ref(false)
const error = ref('')
const listing = ref<ListingVO | null>(null)
const auction = ref<AuctionVO | null>(null)
const lot = ref<MarketLot | null>(null)
const channel = ref<ChannelType>('auction')
const bidAmount = ref('')

const listingId = computed(() => String(route.params.id))
const channelLabel = computed(() => ({ auction: '拍卖', seckill: '秒杀', shop: '商城' })[channel.value])
const channelTone = computed(() => ({ auction: 'auction', seckill: 'seckill', shop: 'shop' } as const)[channel.value])
const actionText = computed(() => ({ auction: '确认出价', seckill: '立即抢购', shop: '立即购买' })[channel.value])
const price = computed(() => lot.value?.price || listing.value?.startPrice || '0.00')
const minimumBid = computed(() => addMoney(price.value, minimumIncrement(price.value)))
const deadline = computed(() => auction.value?.endTime || null)
const canBid = computed(() => channel.value === 'auction' && listing.value?.status === 'AUCTION_LIVE' && Boolean(auction.value))
const canPurchase = computed(() => listing.value?.status === channel.value.toUpperCase())

function addMoney(value: string, increment: string): string {
  const toCents = (money: string): bigint => {
    const [whole = '0', fraction = ''] = money.split('.')
    return BigInt(whole) * 100n + BigInt((fraction + '00').slice(0, 2))
  }
  const cents = toCents(value) + toCents(increment)
  return `${cents / 100n}.${String(cents % 100n).padStart(2, '0')}`
}

function minimumIncrement(value: string): string {
  const numeric = Number(value)
  if (!Number.isFinite(numeric)) return '0.00'
  if (numeric < 100) return '5.00'
  if (numeric < 1000) return '10.00'
  if (numeric < 10000) return '50.00'
  return '100.00'
}

async function load(): Promise<void> {
  loading.value = true
  error.value = ''
  try {
    const detail = await apiRequest<ListingVO>(`/listings/${listingId.value}`)
    listing.value = detail
    const auctionPage = await apiRequest<CursorPage<AuctionVO>>('/auctions?limit=100')
      .catch(() => ({ items: [], nextCursor: null }))
    auction.value = auctionPage.items.find((item) => item.listingId === listingId.value) || null
    const inferredChannel = channelForStatus(detail.status)
    channel.value = inferredChannel
    lot.value = listingToMarketLot(detail, auction.value, 0, channel.value)
    const basePrice = auction.value?.currentPrice || detail.startPrice
    bidAmount.value = addMoney(basePrice, minimumIncrement(basePrice))
  } catch (cause) {
    error.value = errorMessage(cause)
    ElMessage.error(error.value)
  } finally {
    loading.value = false
  }
}

function requireLogin(): boolean {
  if (auth.isAuthenticated) return true
  void router.push({ path: '/auth', query: { redirect: route.fullPath } })
  return false
}

async function placeBid(): Promise<void> {
  if (!requireLogin() || acting.value) return
  if (!auction.value || !canBid.value) {
    ElMessage.warning('当前拍卖不可出价')
    return
  }
  acting.value = true
  try {
    const response = await apiRequest<{ auction: AuctionVO }>(`/auctions/${auction.value.id}/bids`, {
      method: 'POST',
      headers: { 'Idempotency-Key': createIdempotencyKey('bid') },
      body: JSON.stringify({ amount: bidAmount.value }),
    })
    auction.value = response.auction
    ElMessage.success('出价成功，保证金已按规定冻结。')
  } catch (error) {
    ElMessage.error(errorMessage(error))
  } finally {
    acting.value = false
  }
}

async function purchase(): Promise<void> {
  if (!requireLogin() || acting.value) return
  if (!canPurchase.value) {
    ElMessage.warning(`当前商品不在${channelLabel.value}渠道可售状态`)
    return
  }
  acting.value = true
  try {
    const order = await apiRequest<OrderVO>(`/${channel.value}/${listingId.value}/orders`, {
      method: 'POST',
      headers: { 'Idempotency-Key': createIdempotencyKey(channel.value === 'seckill' ? 'seckill-order' : 'shop-order') },
    })
    ElMessage.success('订单已创建，库存已为你锁定。')
    await router.push(`/orders/${order.id}`)
  } catch (error) {
    ElMessage.error(errorMessage(error))
  } finally {
    acting.value = false
  }
}

async function openConversation(): Promise<void> {
  if (!requireLogin() || acting.value) return
  if (listing.value?.sellerId === auth.user?.id) {
    ElMessage.info('这是你发布的商品，无需与自己发起会话。')
    return
  }
  acting.value = true
  try {
    const conversation = await apiRequest<ConversationVO>('/conversations', {
      method: 'POST',
      body: JSON.stringify({ listingId: listingId.value }),
    })
    await router.push({ path: '/messages', query: { conversationId: conversation.id } })
  } catch (error) {
    ElMessage.error(errorMessage(error))
  } finally {
    acting.value = false
  }
}

watch(() => route.query.channel, () => void load())
onMounted(load)
</script>

<template>
  <div v-if="lot" class="page-container detail-page" v-loading="loading">
    <div class="detail-heading">
      <div>
        <div class="detail-heading__meta"><StatusPill :text="channelLabel" :tone="channelTone" /><StatusPill :text="statusText(lot.status)" :tone="lot.status === 'AUCTION_LIVE' || lot.status === 'SECKILL' ? 'success' : 'warning'" /><span>LOT {{ lot.lotNo }} · {{ lot.category }}</span></div>
        <h1>{{ lot.title }}</h1>
        <p>{{ lot.description }}</p>
      </div>
      <button class="button button--outline button--with-icon" type="button" @click="openConversation"><MessagesSquare :size="15" /> 联系卖家</button>
    </div>

    <div class="listing-detail-layout">
      <div class="listing-detail-main">
        <img v-if="lot.coverUrl" :src="lot.coverUrl" :alt="lot.title" class="detail-photo" />
        <ProductArtwork v-else :kind="lot.artwork" :tone="lot.tone" :lot-no="lot.lotNo" large />
        <div class="listing-evidence-grid">
          <div class="panel evidence-panel"><h3>已披露信息</h3><dl><div><dt>成色</dt><dd>{{ lot.condition }}</dd></div><div><dt>瑕疵</dt><dd>{{ lot.defects }}</dd></div><div><dt>渠道</dt><dd>{{ channelLabel }} · 单库存单元</dd></div></dl></div>
          <div class="panel evidence-panel"><h3>渠道历史</h3><div class="channel-progress"><span class="active">拍卖</span><span :class="{ active: channel !== 'auction' }">24h 秒杀</span><span :class="{ active: channel === 'shop' }">30d 商城</span></div><p>{{ lot.autoDowngrade ? '卖家已授权：流拍后自动进入 24 小时秒杀，再进入 30 天商城。' : '卖家未授权：流拍后进入 24 小时决策期，不会自动降级。' }}</p></div>
          <div class="panel evidence-panel seller-panel"><h3>卖家</h3><div class="seller-line"><span class="avatar">{{ listing?.sellerId.slice(-1) }}</span><div><b>卖家编号 {{ listing?.sellerId }}</b><span>信用与交易记录以账号页和订单事实为准</span></div></div><p>沟通请使用平台消息；站外转账不受平台争议保护。</p></div>
        </div>
      </div>

      <aside class="purchase-sidebar">
        <section class="panel purchase-panel">
          <div class="purchase-panel__head"><span>当前{{ channel === 'auction' ? '最高价' : '售价' }}</span><StatusPill text="实时同步" tone="success" /></div>
          <strong class="purchase-price">{{ formatMoney(price) }}</strong>
          <div class="lock-countdown"><span>{{ channel === 'auction' ? '距离结束' : '渠道截止时间' }}</span><CountdownText v-if="deadline" :deadline="deadline" large /><b v-else class="muted-note">以平台渠道状态为准</b></div>

          <template v-if="channel === 'auction'">
            <label class="form-label">你的出价</label>
            <div class="amount-input"><span>¥</span><input v-model="bidAmount" inputmode="decimal" /><small>最低 {{ formatMoney(minimumBid) }}</small></div>
            <div class="form-hint"><span>保证金按出价自动计算</span><span>提交后不可撤销</span></div>
            <button class="button button--accent button--wide" type="button" :disabled="acting || !canBid" @click="placeBid">{{ canBid ? actionText : '当前不可出价' }}</button>
          </template>
          <template v-else>
            <div class="channel-facts"><div><span>{{ channel === 'seckill' ? '库存' : '交易方式' }}</span><b>{{ channel === 'seckill' ? '仅 1 件' : '单件一口价' }}</b></div><div><span>保证金</span><b>{{ channel === 'seckill' ? '买家免保证金' : '钱包担保支付' }}</b></div><div><span>付款时限</span><b>{{ channel === 'seckill' ? '下单后 15 分钟' : '按订单截止时间' }}</b></div></div>
            <button class="button button--accent button--wide" type="button" :disabled="acting || !canPurchase" @click="purchase">{{ canPurchase ? actionText : '当前渠道未开放购买' }}</button>
          </template>

          <div class="purchase-rules"><div><span>支付方式</span><b>平台虚拟钱包</b></div><div><span>结算</span><b>确认收货且争议期结束后</b></div><div><span>同时下单</span><b>先到先得，并显示最新状态</b></div></div>
          <div class="ai-advice"><Bot :size="17" /><div><b>AI 信息核对</b><p>AI 可以辅助整理描述和提示遗漏信息，但是否出价、成交和资金处理均由业务规则与人工决定。</p></div></div>
          <div class="channel-rule"><ShieldCheck :size="15" /><p>拍卖保证金与最低加价由平台校验；秒杀锁库存 15 分钟；商城只出售单件。</p></div>
        </section>
      </aside>
    </div>

    <section class="panel ai-boundary-strip"><Sparkles :size="18" /><div><b>商品信息边界</b><p>商品细节以卖家披露和人工审核为准；AI 只协助整理文字，不判断成色、真伪或价值。</p></div><BadgeCheck :size="17" /><span>资金、成交、最终审核均不由 AI 决定</span></section>
  </div>
  <EmptyState v-else :title="error ? '商品暂时无法读取' : '商品不存在'" :description="error || '请确认商品链接是否正确，或稍后重试。'" />
</template>
