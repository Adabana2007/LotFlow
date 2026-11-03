<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import { ArrowRight, BadgeCheck, Gavel, Sparkles } from 'lucide-vue-next'
import ChannelPath from '@/components/ChannelPath.vue'
import LotCard from '@/components/LotCard.vue'
import EmptyState from '@/components/EmptyState.vue'
import { apiRequest, errorMessage } from '@/api/client'
import type { AuctionVO, CursorPage, ListingVO } from '@/types/api'
import type { MarketLot } from '@/types/catalog'
import { listingToMarketLot } from '@/utils/catalog'

const loading = ref(true)
const error = ref('')
const auctions = ref<AuctionVO[]>([])
const listings = ref<ListingVO[]>([])
const featuredLots = ref<MarketLot[]>([])

const homeMetrics = computed(() => [
  { label: '当前拍卖', value: String(auctions.value.length), note: '正在进行的拍场' },
  { label: '可见商品', value: String(listings.value.length), note: '正在公开展示' },
  { label: '秒杀在售', value: String(listings.value.filter((item) => item.status === 'SECKILL').length), note: '单件库存' },
  { label: '商城在售', value: String(listings.value.filter((item) => item.status === 'SHOP').length), note: '一口价单件' },
])

async function load(): Promise<void> {
  loading.value = true
  error.value = ''
  try {
    const [auctionPage, listingPage] = await Promise.all([
      apiRequest<CursorPage<AuctionVO>>('/auctions?limit=12&activeOnly=true').catch(() => ({ items: [], nextCursor: null })),
      apiRequest<CursorPage<ListingVO>>('/listings?limit=30').catch(() => ({ items: [], nextCursor: null })),
    ])
    auctions.value = auctionPage.items
    listings.value = listingPage.items

    const listingById = new Map(listingPage.items.map((item) => [item.id, item]))
    const auctionLots = auctionPage.items.flatMap((auction, index) => {
      const listing = listingById.get(auction.listingId)
      return listing ? [listingToMarketLot(listing, auction, index, 'auction')] : []
    })
    const channelLots = listingPage.items
      .filter((item) => item.status === 'SECKILL' || item.status === 'SHOP')
      .map((item, index) => listingToMarketLot(item, null, auctionLots.length + index))
    featuredLots.value = [...auctionLots, ...channelLots].slice(0, 3)
  } catch (cause) {
    error.value = errorMessage(cause)
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<template>
  <PageContainer>
    <section class="home-hero">
      <div class="home-hero__copy">
        <span class="eyebrow">EVERYTHING DESERVES A SECOND CHANCE</span>
        <h1>每个人都有<br />值得被认真拍卖的东西</h1>
        <p>旧相机、游戏机、手办、乐器或手工制品。流拍不是结束，而是进入秒杀和商城继续寻找新主人。</p>
        <div class="button-row">
          <RouterLink class="button button--accent button--with-icon" to="/workspace/publish">
            <Sparkles :size="15" /> 发起一场拍卖
          </RouterLink>
          <RouterLink class="button button--outline button--with-icon" to="/auctions">
            <Gavel :size="15" /> 先看看拍场
          </RouterLink>
        </div>
      </div>
      <div class="panel home-path-panel">
        <div class="section-heading section-heading--tight">
          <div>
            <span class="eyebrow">THE LOTFLOW PATH</span>
            <h2>卖不掉，就换一种方式遇见</h2>
          </div>
          <span class="status-pill status-pill--success"><BadgeCheck :size="13" />卖家可预先授权</span>
        </div>
        <ChannelPath />
        <div class="path-footnote">
          <span>未授权：流拍后有 24 小时决定重新拍卖、进入秒杀或退回库存。</span>
          <b>价格：商城价 ≤ 秒杀价 ≤ 起拍价</b>
        </div>
      </div>
    </section>

    <section class="metric-strip">
      <div v-for="metric in homeMetrics" :key="metric.label" class="metric-strip__item">
        <span>{{ metric.label }}</span>
        <strong v-loading="loading">{{ loading ? '—' : metric.value }}</strong>
        <small>{{ metric.note }}</small>
      </div>
    </section>

    <section class="page-section">
      <div class="section-heading">
        <div><h2>今天，普通的东西正在流转</h2><p>从收藏级到日常旧物，都能被认真描述和公平交易。</p></div>
        <RouterLink class="text-link" to="/auctions">查看全部拍品 <ArrowRight :size="14" /></RouterLink>
      </div>
      <div v-if="featuredLots.length" class="lot-grid lot-grid--three" v-loading="loading">
        <LotCard v-for="lot in featuredLots" :key="lot.id" :lot="lot" />
      </div>
      <EmptyState v-else :title="error ? '暂时无法读取拍品' : '当前还没有可展示的拍品'" :description="error || '商品通过审核并进入拍卖后会显示在这里。'" />
    </section>

    <section class="split-callout">
      <div>
        <span class="eyebrow">SIMPLE START</span>
        <h2>从一张照片和一句真实描述开始</h2>
        <p>AI 只整理草稿和提示遗漏，最终标题、价格与发布动作始终由你确认。</p>
      </div>
      <div class="split-callout__steps">
        <span><b>01</b> 描述物品</span>
        <span><b>02</b> 核对价格链</span>
        <span><b>03</b> 提交人工审核</span>
      </div>
    </section>
  </PageContainer>
</template>

<script lang="ts">
import { defineComponent, h } from 'vue'

const PageContainer = defineComponent({
  setup(_, { slots }) {
    return () => h('div', { class: 'page-container' }, slots.default?.())
  },
})

export default { components: { PageContainer } }
</script>
