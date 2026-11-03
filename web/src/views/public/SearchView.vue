<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Search, Sparkles, WandSparkles } from 'lucide-vue-next'
import PageHeading from '@/components/PageHeading.vue'
import LotCard from '@/components/LotCard.vue'
import EmptyState from '@/components/EmptyState.vue'
import { apiRequest, errorMessage } from '@/api/client'
import type { CursorPage, ListingVO } from '@/types/api'
import type { ChannelType, MarketLot } from '@/types/catalog'
import { listingToMarketLot } from '@/utils/catalog'

const route = useRoute()
const router = useRouter()
const query = ref(String(route.query.q || ''))
const loading = ref(false)
const allResults = ref<MarketLot[]>([])
const error = ref('')
const apiMessage = ref('输入物品、品牌、颜色或使用场景，系统会结合语义理解与关键词找到相关商品。')
const selectedChannels = ref<Record<ChannelType, boolean>>({ auction: true, seckill: true, shop: true })
const minPrice = ref('')
const maxPrice = ref('')
const condition = ref('')
const suggestions = ['500 元以内的胶片相机', '带原盒的掌机', '适合送人的手作杯子', '有故事的老乐器']

const results = computed(() => allResults.value.filter((lot) => {
  if (!selectedChannels.value[lot.channel]) return false
  if (condition.value && lot.conditionCode !== condition.value) return false
  const price = Number(lot.price)
  if (minPrice.value && price < Number(minPrice.value)) return false
  if (maxPrice.value && price > Number(maxPrice.value)) return false
  return true
}))
const resultCaption = computed(() => (loading.value ? '正在检索' : `${results.value.length} 件相关物品`))

async function searchListings(): Promise<void> {
  const text = query.value.trim()
  if (!text) return
  loading.value = true
  error.value = ''
  router.replace({ query: { q: text } })
  try {
    const page = await apiRequest<CursorPage<ListingVO>>(`/search/listings?q=${encodeURIComponent(text)}&limit=20`)
    if (page.items.length) {
      allResults.value = page.items.map((listing, index) => listingToMarketLot(listing, null, index))
      apiMessage.value = '已找到与描述相关的商品；必要时会自动补充关键词结果。'
    } else {
      allResults.value = []
      apiMessage.value = '没有找到匹配物品。可以换一种描述，或减少品牌和价格条件。'
    }
  } catch (cause) {
    allResults.value = []
    error.value = errorMessage(cause)
    apiMessage.value = `检索失败：${error.value}`
  } finally {
    loading.value = false
  }
}

function resetFilters(): void {
  selectedChannels.value = { auction: true, seckill: true, shop: true }
  minPrice.value = ''
  maxPrice.value = ''
  condition.value = ''
}

onMounted(() => {
  if (route.query.q) void searchListings()
})
</script>

<template>
  <div class="page-container">
    <PageHeading eyebrow="AI DISCOVERY" title="用一句话，找到真正合适的东西" description="自然语言只负责检索、排序与解释；是否购买、出价和付款始终由你决定。">
      <template #actions><span class="status-pill status-pill--success"><Sparkles :size="13" />混合检索</span></template>
    </PageHeading>
    <section class="search-hero panel">
      <label class="search-hero__input"><Search :size="20" /><input v-model="query" placeholder="例如：想找一台能正常拍摄、500 元以内的胶片相机" @keyup.enter="searchListings" /></label>
      <button class="button button--accent button--with-icon" type="button" :disabled="loading" @click="searchListings"><WandSparkles :size="15" />{{ loading ? '正在找…' : '开始找物' }}</button>
      <div class="suggestion-row"><span>可以这样问：</span><button v-for="item in suggestions" :key="item" type="button" @click="query = item; searchListings()">{{ item }}</button></div>
    </section>
    <div class="search-layout">
      <aside class="panel filter-panel">
        <div class="panel-head"><h3>筛选</h3><button class="text-button" type="button" @click="resetFilters">重置</button></div>
        <label>渠道</label><div class="check-stack"><span><input v-model="selectedChannels.auction" type="checkbox" /> 拍卖</span><span><input v-model="selectedChannels.seckill" type="checkbox" /> 秒杀</span><span><input v-model="selectedChannels.shop" type="checkbox" /> 一口价商城</span></div>
        <label>价格范围</label><div class="two-inputs"><input v-model="minPrice" class="input" inputmode="decimal" placeholder="最低价" /><input v-model="maxPrice" class="input" inputmode="decimal" placeholder="最高价" /></div>
        <label>成色</label><div class="filter-chip-grid"><button class="filter-chip" :class="{ active: condition === 'NEW' }" type="button" @click="condition = condition === 'NEW' ? '' : 'NEW'">全新</button><button class="filter-chip" :class="{ active: condition === 'LIKE_NEW' }" type="button" @click="condition = condition === 'LIKE_NEW' ? '' : 'LIKE_NEW'">近全新</button><button class="filter-chip" :class="{ active: condition === 'GOOD' }" type="button" @click="condition = condition === 'GOOD' ? '' : 'GOOD'">正常使用</button></div>
        <div class="ai-boundary-note"><Sparkles :size="15" /><p><b>AI 不替你成交</b><br />排序理由只用于解释结果，不会修改商品价格或创建订单。</p></div>
      </aside>
      <section class="search-results">
        <div class="section-heading section-heading--table"><div><h2>检索结果</h2><p>{{ apiMessage }}</p></div><span class="muted-note">{{ resultCaption }}</span></div>
        <div v-if="results.length" class="lot-grid lot-grid--three" v-loading="loading"><LotCard v-for="lot in results" :key="lot.id" :lot="lot" compact /></div>
        <EmptyState v-else :title="error ? '检索暂不可用' : '没有找到匹配物品'" :description="error || '尝试减少条件，或换成“旧相机、手作杯、复古游戏机”等更直接的描述。'" />
      </section>
    </div>
  </div>
</template>
