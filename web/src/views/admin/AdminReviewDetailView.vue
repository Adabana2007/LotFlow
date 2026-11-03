<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Bot, ClipboardCheck, FileSearch, ShieldAlert, Sparkles } from 'lucide-vue-next'
import PageHeading from '@/components/PageHeading.vue'
import ProductArtwork from '@/components/ProductArtwork.vue'
import StatusPill from '@/components/StatusPill.vue'
import { apiRequest, errorMessage } from '@/api/client'
import type { AdminListingDetailVO, AdminListingImageVO, CursorPage, ListingVO, ReviewTaskVO } from '@/types/api'
import { listingToMarketLot } from '@/utils/catalog'
import { categoryText, conditionText, formatMoney, formatDateTime, statusText } from '@/utils/format'

const route = useRoute()
const router = useRouter()
const task = ref<ReviewTaskVO | null>(null)
const listing = ref<ListingVO | null>(null)
const images = ref<AdminListingImageVO[]>([])
const loading = ref(true)
const loadError = ref('')
const decision = ref<'APPROVED' | 'REJECTED' | 'CHANGES_REQUIRED'>('APPROVED')
const reason = ref('')
const submitting = ref(false)

async function load(): Promise<void> {
  loading.value = true
  loadError.value = ''
  try {
    const page = await apiRequest<CursorPage<ReviewTaskVO>>('/admin/review-tasks?limit=100')
    const found = page.items.find((item) => item.id === String(route.params.taskId)) || null
    task.value = found
    if (!found) {
      loadError.value = '当前审核任务不存在或已移出队列。'
      listing.value = null
      images.value = []
      return
    }
    // 待审商品不在公开状态白名单内，必须走后台详情接口才能同时拿到完整描述和实拍图证据。
    const detail = await apiRequest<AdminListingDetailVO>(`/admin/listings/${found.listingId}`).catch(() => null)
    listing.value = detail?.listing ?? null
    images.value = detail?.images ?? []
  } catch (error) {
    task.value = null
    listing.value = null
    images.value = []
    loadError.value = errorMessage(error)
  } finally {
    loading.value = false
  }
}

async function submitDecision(): Promise<void> {
  if (!task.value || submitting.value) return
  if (!reason.value.trim()) {
    ElMessage.warning('请填写人工审核理由')
    return
  }
  submitting.value = true
  try {
    await apiRequest<void>(`/admin/review-tasks/${task.value.id}/decisions`, {
      method: 'POST',
      body: JSON.stringify({ decision: decision.value, reason: reason.value.trim() }),
    })
    ElMessage.success('人工审核决定已提交并写入审计')
    await router.push('/admin/reviews')
  } catch (error) {
    ElMessage.error(errorMessage(error))
  } finally {
    submitting.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="admin-page" v-loading="loading">
    <template v-if="task">
      <PageHeading eyebrow="REVIEW DETAIL" :title="`审核任务 R-${task.id}`" :description="`创建于 ${formatDateTime(task.createdAt)} · 最终结果必须由人工提交`">
        <template #actions><StatusPill :text="statusText(task.riskLevel)" :tone="task.riskLevel === 'HIGH' ? 'danger' : task.riskLevel === 'MEDIUM' ? 'warning' : 'success'" /><StatusPill text="审核任务" tone="success" /></template>
      </PageHeading>
      <div class="review-detail-layout">
        <div class="review-detail-main">
          <section class="panel review-goods"><div class="panel-head"><div><h3>待审商品</h3><p>商品编号 {{ task.listingId }} · 卖家编号 {{ task.sellerId }}</p></div><span class="mono">R-{{ task.id }}</span></div><div class="review-goods__body"><div v-if="images.length" class="review-gallery"><img v-for="image in images" :key="image.id" :src="image.url" :alt="'商品实拍图 ' + (image.sortOrder + 1)" /></div><ProductArtwork v-else :kind="listing ? listingToMarketLot(listing).artwork : 'camera'" :tone="listing ? listingToMarketLot(listing).tone : 'tone-celadon'" :lot-no="task.listingId" /><div v-if="listing"><div class="detail-heading__meta"><StatusPill :text="statusText(listing.status)" tone="warning" /><StatusPill :text="categoryText(listing.categoryCode)" /></div><h2>{{ listing.title }}</h2><p>{{ listing.description }}</p><dl class="detail-list"><div><dt>成色</dt><dd>{{ conditionText(listing.conditionCode) }}</dd></div><div><dt>瑕疵</dt><dd>{{ listing.defects || '未填写' }}</dd></div><div><dt>价格链</dt><dd>{{ formatMoney(listing.shopPrice) }} ≤ {{ formatMoney(listing.seckillPrice) }} ≤ {{ formatMoney(listing.startPrice) }}</dd></div><div><dt>自动降级</dt><dd>{{ listing.autoDowngrade ? '已授权' : '未授权' }}</dd></div></dl></div><div v-else class="empty-state"><FileSearch :size="22" /><b>商品详情暂不可用</b><p>审核任务已返回，但完整商品描述与图片证据暂时不可用；不会只凭任务摘要直接给出结论。</p></div></div></section>
          <section class="panel evidence-review"><div class="panel-head"><h3><FileSearch :size="16" /> 风险摘要</h3><span class="muted-note">只读信息，不自动决定结果</span></div><div v-if="task.evidenceSummary" class="evidence-summary">{{ task.evidenceSummary }}</div><div v-else class="empty-state"><ShieldAlert :size="22" /><b>暂无风险摘要</b><p>没有摘要时应由审核员自行核对证据，而不是假定风险为空。</p></div><div class="evidence-check-list"><div><ClipboardCheck :size="16" /><p><b>任务状态：{{ statusText(task.status) }}</b><br />人工结论提交后会进入审核审计。</p><StatusPill :text="task.manualRequired ? '需人工' : '可人工复核'" :tone="task.manualRequired ? 'warning' : 'neutral'" /></div></div></section>
        </div>
        <aside class="review-action-side">
          <section class="panel ai-review-panel"><div class="panel-head"><h3><Bot :size="16" /> AI 审核证据</h3><Sparkles :size="16" /></div><p>AI 只提供风险标签与证据摘要，没有自动通过、驳回或发布权限。</p><div class="ai-tag-grid"><span>风险等级 <b>{{ statusText(task.riskLevel) }}</b></span><span>人工要求 <b>{{ task.manualRequired ? '是' : '否' }}</b></span><span>证据摘要 <b>{{ task.evidenceSummary ? '已提供' : '未提供' }}</b></span><span>最终决定 <b>人工</b></span></div></section>
          <section class="panel review-decision"><div class="panel-head"><h3>人工最终决定</h3><StatusPill text="必须填写理由" tone="warning" /></div><div class="decision-radio"><label v-for="item in [{ value: 'APPROVED', label: '通过' }, { value: 'REJECTED', label: '驳回' }, { value: 'CHANGES_REQUIRED', label: '要求修改' }]" :key="item.value"><input v-model="decision" type="radio" :value="item.value" />{{ item.label }}</label></div><label class="form-field"><span>审核理由</span><textarea v-model="reason" rows="5" maxlength="2000" placeholder="说明判断依据；该理由会进入审计记录" /></label><button class="button button--dark button--wide" type="button" :disabled="submitting || !listing" @click="submitDecision">{{ submitting ? '正在提交…' : listing ? '提交人工决定' : '商品证据暂不可用' }}</button></section>
        </aside>
      </div>
      </template>
    <div v-else-if="!loading" class="panel empty-state"><FileSearch :size="24" /><b>审核任务无法读取</b><p>{{ loadError || '任务不存在或已离开当前队列。' }}</p><RouterLink class="button button--outline" to="/admin/reviews">返回审核队列</RouterLink></div>
  </div>
</template>

<style scoped>
.review-gallery { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 8px; align-content: start; }
.review-gallery img { width: 100%; aspect-ratio: 4 / 3; object-fit: cover; border: 1px solid var(--line); border-radius: 4px; background: var(--surface-muted); }
</style>
