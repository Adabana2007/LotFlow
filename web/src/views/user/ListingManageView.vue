<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  Bot,
  CalendarClock,
  ChevronRight,
  ImagePlus,
  LockKeyhole,
  RefreshCw,
  Send,
  Sparkles,
  TriangleAlert,
  UploadCloud,
} from 'lucide-vue-next'
import PageHeading from '@/components/PageHeading.vue'
import ProductArtwork from '@/components/ProductArtwork.vue'
import StatusPill from '@/components/StatusPill.vue'
import { apiRequest, apiRequestWithMeta, errorMessage } from '@/api/client'
import type {
  AiTaskVO,
  ListingVO,
  SaleChannelVO,
  SellerListingImageVO,
} from '@/types/api'
import { channelForStatus, listingToMarketLot } from '@/utils/catalog'
import {
  categoryText,
  conditionText,
  formatFullDate,
  formatMoney,
  statusText,
  toDateTimeLocal,
} from '@/utils/format'
import { uploadListingImage } from '@/utils/upload'

const route = useRoute()
const router = useRouter()
const listing = ref<ListingVO | null>(null)
const channels = ref<SaleChannelVO[]>([])
const images = ref<SellerListingImageVO[]>([])
const loading = ref(true)
const saving = ref(false)
const uploading = ref(false)
const submitting = ref(false)
const scheduling = ref(false)
const analyzing = ref(false)
const loadError = ref('')
const uploadError = ref('')
const analysisError = ref('')
const analysisTask = ref<AiTaskVO | null>(null)
const fileInput = ref<HTMLInputElement | null>(null)
const scheduleTime = ref('')
const form = reactive({
  title: '',
  description: '',
  categoryCode: 'CAMERA',
  conditionCode: 'GOOD',
  defects: '',
  startPrice: '',
  seckillPrice: '',
  shopPrice: '',
  durationHours: 24,
  scheduledStart: '',
  autoDowngrade: true,
  chatEnabled: true,
})

const categories = [
  ['CAMERA', '相机与影像'],
  ['CARD', '收藏卡'],
  ['FIGURE', '手办与模型'],
  ['INSTRUMENT', '乐器'],
  ['GAME_CONSOLE', '复古游戏机'],
  ['HANDICRAFT', '手工作品'],
  ['COLLECTIBLE', '收藏品'],
  ['FASHION', '服饰与配饰'],
  ['HOME', '家居器物'],
  ['OTHER', '其他物品'],
] as const
const conditions = [
  ['NEW', '全新'],
  ['LIKE_NEW', '近全新'],
  ['GOOD', '正常使用'],
  ['FAIR', '使用痕迹明显'],
  ['POOR', '功能或外观问题较多'],
  ['FOR_PARTS', '仅作配件'],
] as const

const lot = computed(() => listing.value ? listingToMarketLot(listing.value, null, 0, channelForStatus(listing.value.status)) : null)
const editable = computed(() => Boolean(listing.value && ['DRAFT', 'CHANGES_REQUIRED'].includes(listing.value.status)))
const canSchedule = computed(() => listing.value?.status === 'APPROVED')
const validPriceChain = computed(() => {
  const cents = (value: string): bigint | null => {
    if (!value) return null
    if (!/^\d+(\.\d{1,2})?$/.test(value)) return null
    const [whole = '0', fraction = ''] = value.split('.')
    return BigInt(whole) * 100n + BigInt((fraction + '00').slice(0, 2))
  }
  const start = cents(form.startPrice)
  const seckill = cents(form.seckillPrice)
  const shop = cents(form.shopPrice)
  if (start === null || start <= 0n) return false
  if (!form.autoDowngrade) return true
  return seckill !== null && shop !== null && shop > 0n && shop <= seckill && seckill <= start
})

function channelLabel(channel: string): string {
  return ({ AUCTION: '拍卖', SECKILL: '秒杀', SHOP: '商城' } as Record<string, string>)[channel] || channel
}

function populate(detail: ListingVO): void {
  form.title = detail.title
  form.description = detail.description
  form.categoryCode = detail.categoryCode
  form.conditionCode = detail.conditionCode
  form.defects = detail.defects || ''
  form.startPrice = detail.startPrice
  form.seckillPrice = detail.seckillPrice || ''
  form.shopPrice = detail.shopPrice || ''
  form.durationHours = detail.durationHours
  form.scheduledStart = toDateTimeLocal(new Date(detail.scheduledStart))
  scheduleTime.value = form.scheduledStart
  form.autoDowngrade = detail.autoDowngrade
  form.chatEnabled = true
}

async function load(): Promise<void> {
  loading.value = true
  loadError.value = ''
  uploadError.value = ''
  try {
    const listingId = String(route.params.id)
    const [detail, history, imageRows] = await Promise.all([
      apiRequest<ListingVO>(`/listings/${listingId}/mine`),
      apiRequest<SaleChannelVO[]>(`/listings/${listingId}/channels`).catch(() => []),
      apiRequest<SellerListingImageVO[]>(`/listings/${listingId}/images`).catch(() => []),
    ])
    listing.value = detail
    channels.value = history
    images.value = imageRows
    populate(detail)
  } catch (error) {
    listing.value = null
    channels.value = []
    images.value = []
    loadError.value = errorMessage(error)
  } finally {
    loading.value = false
  }
}

function validateEdit(): boolean {
  if (!form.title.trim() || !form.description.trim() || !form.defects.trim()) {
    ElMessage.warning('请填写标题、描述和已知瑕疵')
    return false
  }
  if (!validPriceChain.value) {
    ElMessage.warning('价格必须满足 0 < 商城价 ≤ 秒杀价 ≤ 起拍价')
    return false
  }
  return true
}

async function save(): Promise<void> {
  if (!listing.value || !editable.value || saving.value) return
  if (!validateEdit()) return
  saving.value = true
  try {
    await apiRequest<void>(`/listings/${listing.value.id}`, {
      method: 'PUT',
      headers: { 'If-Match': String(listing.value.version) },
      body: JSON.stringify({
        title: form.title.trim(),
        description: form.description.trim(),
        categoryCode: form.categoryCode,
        conditionCode: form.conditionCode,
        defects: form.defects.trim(),
        startPrice: form.startPrice,
        durationHours: form.durationHours,
        scheduledStart: new Date(form.scheduledStart).toISOString(),
        autoDowngrade: form.autoDowngrade,
        seckillPrice: form.autoDowngrade ? form.seckillPrice : null,
        shopPrice: form.autoDowngrade ? form.shopPrice : null,
        chatEnabled: form.chatEnabled,
      }),
    })
    ElMessage.success('商品信息已保存')
    await load()
  } catch (error) {
    ElMessage.error(errorMessage(error))
  } finally {
    saving.value = false
  }
}

async function handleFiles(event: Event): Promise<void> {
  const input = event.target as HTMLInputElement
  const files = Array.from(input.files || [])
  input.value = ''
  if (!files.length || uploading.value) return
  uploading.value = true
  uploadError.value = ''
  try {
    for (const [index, file] of files.entries()) {
      if (!listing.value || !editable.value) break
      await uploadListingImage(listing.value.id, file, images.value.length + index)
    }
    await load()
    ElMessage.success('商品图片已保存')
  } catch (error) {
    uploadError.value = errorMessage(error)
  } finally {
    uploading.value = false
  }
}

async function submitReview(): Promise<void> {
  if (!listing.value || submitting.value) return
  if (!images.value.length) {
    ElMessage.warning('至少上传一张商品图片后再提交审核')
    return
  }
  submitting.value = true
  try {
    const result = await apiRequest<{ reviewTaskId: string }>(`/listings/${listing.value.id}/submissions`, { method: 'POST' })
    ElMessage.success(`已提交审核，任务编号 ${result.reviewTaskId}`)
    await load()
  } catch (error) {
    ElMessage.error(errorMessage(error))
  } finally {
    submitting.value = false
  }
}

async function scheduleAuction(): Promise<void> {
  if (!listing.value || scheduling.value) return
  const scheduled = new Date(scheduleTime.value)
  if (!Number.isFinite(scheduled.getTime()) || scheduled.getTime() <= Date.now()) {
    ElMessage.warning('请选择晚于当前时间的开拍时间')
    return
  }
  scheduling.value = true
  try {
    const result = await apiRequest<{ auctionId: string }>(`/listings/${listing.value.id}/auction-schedules`, {
      method: 'POST',
      body: JSON.stringify({ scheduledStart: scheduled.toISOString() }),
    })
    ElMessage.success(`拍卖已排期，场次编号 ${result.auctionId}`)
    await router.push('/workspace/listings')
  } catch (error) {
    ElMessage.error(errorMessage(error))
  } finally {
    scheduling.value = false
  }
}

async function analyze(): Promise<void> {
  if (!listing.value || analyzing.value) return
  analyzing.value = true
  analysisError.value = ''
  try {
    const submitted = await apiRequest<{ taskId: string }>('/ai/seller-analyses', {
      method: 'POST',
      body: JSON.stringify({ listingId: listing.value.id }),
    })
    for (let attempt = 0; attempt < 12; attempt += 1) {
      await new Promise((resolve) => window.setTimeout(resolve, 1000))
      analysisTask.value = await apiRequest<AiTaskVO>(`/ai/tasks/${submitted.taskId}`)
      if (['SUCCEEDED', 'FAILED', 'FALLBACK'].includes(analysisTask.value.status)) break
    }
    if (analysisTask.value?.status === 'FAILED') {
      analysisError.value = analysisTask.value.errorCode || '经营分析暂时不可用'
    }
  } catch (error) {
    analysisError.value = errorMessage(error)
  } finally {
    analyzing.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="workspace-page" v-loading="loading">
    <template v-if="listing && lot">
      <PageHeading eyebrow="拍品管理" :title="listing.title" description="编辑、上传图片、提交审核与确认排期都在同一生命周期内完成。">
        <template #actions><RouterLink class="button button--outline" to="/workspace/listings">返回我的拍品</RouterLink><RouterLink v-if="listing.status === 'WAITING_SELLER_DECISION'" class="button button--dark" :to="`/workspace/listings/${listing.id}/unsold`">处理流拍</RouterLink></template>
      </PageHeading>

      <div class="manage-product-head panel">
        <ProductArtwork :kind="lot.artwork" :tone="lot.tone" :lot-no="listing.id" />
        <div><div class="detail-heading__meta"><StatusPill :text="statusText(listing.status)" :tone="listing.status === 'REJECTED' ? 'danger' : editable ? 'warning' : 'success'" /><StatusPill :text="categoryText(listing.categoryCode)" /><StatusPill :text="conditionText(listing.conditionCode)" /></div><h2>{{ listing.title }}</h2><p>{{ listing.description }}</p><div class="meta-pairs"><span>版本 <b>{{ listing.version }}</b></span><span>时长 <b>{{ listing.durationHours }} 小时</b></span><span>计划开始 <b>{{ formatFullDate(listing.scheduledStart) }}</b></span></div></div>
      </div>

      <div class="manage-grid">
        <section class="panel manage-edit-panel">
          <div class="panel-head"><div><h3>商品信息</h3><p>{{ editable ? '保存时使用版本校验，避免覆盖其他设备刚完成的内容。' : '当前状态只读；后续变更新请按对应业务动作处理。' }}</p></div><StatusPill :text="editable ? '可编辑' : '只读'" :tone="editable ? 'success' : 'neutral'" /></div>
          <div class="form-stack" :class="{ 'form-stack--readonly': !editable }">
            <label class="form-field"><span>标题</span><input v-model="form.title" maxlength="160" :disabled="!editable" /></label>
            <div class="form-grid-two"><label class="form-field"><span>类目</span><select v-model="form.categoryCode" :disabled="!editable"><option v-for="item in categories" :key="item[0]" :value="item[0]">{{ item[1] }}</option></select></label><label class="form-field"><span>成色</span><select v-model="form.conditionCode" :disabled="!editable"><option v-for="item in conditions" :key="item[0]" :value="item[0]">{{ item[1] }}</option></select></label></div>
            <label class="form-field"><span>描述</span><textarea v-model="form.description" rows="5" maxlength="5000" :disabled="!editable" /></label>
            <label class="form-field"><span>已知瑕疵</span><textarea v-model="form.defects" rows="3" maxlength="2000" :disabled="!editable" /></label>
            <div class="form-grid-three"><label class="form-field"><span>起拍价</span><input v-model="form.startPrice" inputmode="decimal" :disabled="!editable" /></label><label class="form-field"><span>秒杀价</span><input v-model="form.seckillPrice" inputmode="decimal" :disabled="!editable || !form.autoDowngrade" /></label><label class="form-field"><span>商城价 / 最低售价</span><input v-model="form.shopPrice" inputmode="decimal" :disabled="!editable || !form.autoDowngrade" /></label></div>
            <div class="price-validation" :class="{ invalid: !validPriceChain }"><span>{{ formatMoney(form.startPrice) }} 起拍 · {{ form.autoDowngrade ? `${formatMoney(form.seckillPrice)} 秒杀 · ${formatMoney(form.shopPrice)} 商城` : '流拍后由卖家决定' }}</span><b>{{ validPriceChain ? '价格链有效' : '需要修正' }}</b></div>
            <div class="form-grid-two"><label class="form-field"><span>拍卖时长</span><select v-model="form.durationHours" :disabled="!editable"><option :value="24">24 小时</option><option :value="48">48 小时</option><option :value="72">72 小时</option></select></label><label class="form-field"><span>计划开始</span><input v-model="form.scheduledStart" type="datetime-local" :disabled="!editable" /></label></div>
            <div class="authorization-box"><div><b>流拍后自动降级</b><p>拍卖 → 24h 秒杀 → 30d 商城；未授权时流拍后进入 24h 卖家决策。</p></div><el-switch v-model="form.autoDowngrade" :disabled="!editable" active-text="已授权" inactive-text="由我决定" /></div>
            <button v-if="editable" class="button button--dark button--wide" type="button" :disabled="saving" @click="save">{{ saving ? '正在保存…' : '保存商品信息' }}</button>
          </div>
        </section>

        <aside class="manage-actions-side">
          <section class="panel">
            <div class="panel-head"><h3>下一步</h3><StatusPill :text="statusText(listing.status)" :tone="editable ? 'warning' : 'success'" /></div>
            <p v-if="editable" class="action-help">至少上传一张图片后可提交审核。AI 只给风险提示，最终由平台规则和人工审核决定。</p>
            <button v-if="editable" class="button button--accent button--wide button--with-icon" type="button" :disabled="submitting || images.length === 0" @click="submitReview"><Send :size="15" />{{ submitting ? '正在提交…' : '提交平台审核' }}</button>
            <template v-if="canSchedule">
              <p class="action-help">审核已通过，确认具体开拍时间后会冻结卖家保证金并创建拍卖。</p>
              <label class="form-field"><span>开拍时间</span><input v-model="scheduleTime" type="datetime-local" /></label>
              <button class="button button--dark button--wide button--with-icon" type="button" :disabled="scheduling" @click="scheduleAuction"><CalendarClock :size="15" />{{ scheduling ? '正在排期…' : '确认拍卖排期' }}</button>
            </template>
            <div v-if="listing.rejectionReason" class="warning-block"><TriangleAlert :size="16" /><p><b>审核意见</b><br />{{ listing.rejectionReason }}</p></div>
          </section>
          <section class="panel">
            <div class="panel-head"><h3><Bot :size="16" /> AI 经营分析</h3><StatusPill text="只建议" tone="warning" /></div>
            <p>分析会引用当前商品、出价和渠道事实，不会自动改价或改变渠道。</p>
            <button class="button button--outline button--wide button--with-icon" type="button" :disabled="analyzing" @click="analyze"><Sparkles :size="15" />{{ analyzing ? '正在分析…' : analysisTask ? '重新分析' : '生成经营建议' }}</button>
            <div v-if="analysisTask" class="ai-task-result"><StatusPill :text="statusText(analysisTask.status)" :tone="analysisTask.status === 'SUCCEEDED' ? 'success' : 'warning'" /><p>{{ analysisTask.outputSummary || '正在整理分析结果。' }}</p></div>
            <div v-if="analysisError" class="warning-block"><TriangleAlert :size="16" /><p>{{ analysisError }}<br />建议不可用不会影响商品和交易流程。</p></div>
          </section>
        </aside>
      </div>

      <section class="panel image-management-panel">
        <div class="panel-head"><div><h3>商品图片</h3><p>支持 JPG、PNG、WebP，单张不超过 10 MB；上传失败可重新选择。</p></div><StatusPill :text="`${images.length} 张`" tone="neutral" /></div>
        <div class="managed-image-grid"><div v-for="image in images" :key="image.id" class="managed-image"><img :src="image.url" :alt="`商品图片 ${image.sortOrder + 1}`" /><span>{{ Math.ceil(image.sizeBytes / 1024) }} KB</span></div><button v-if="editable" class="image-add-card" type="button" :disabled="uploading" @click="fileInput?.click()"><ImagePlus :size="22" /><span>{{ uploading ? '正在上传…' : '添加图片' }}</span></button></div>
        <input ref="fileInput" class="hidden-file-input" type="file" accept="image/jpeg,image/png,image/webp" multiple @change="handleFiles" />
        <div v-if="uploadError" class="warning-block"><UploadCloud :size="16" /><p>{{ uploadError }}<br />重新选择文件即可重试。</p></div>
      </section>

      <section class="panel">
        <div class="panel-head"><div><h3>渠道历史</h3><p>同一商品在任意时刻只允许一个有效销售渠道。</p></div><StatusPill :text="`${channels.length} 条`" tone="neutral" /></div>
        <div v-if="channels.length" class="channel-history-list"><div v-for="channel in channels" :key="channel.id" class="rule-line"><b>{{ channelLabel(channel.channel) }} · {{ statusText(channel.status) }}</b><span>{{ formatMoney(channel.price) }} · {{ formatFullDate(channel.startsAt) }} 至 {{ formatFullDate(channel.endsAt) }}</span></div></div>
        <div v-else class="empty-state"><LockKeyhole :size="20" /><b>暂无渠道记录</b><p>完成审核和排期后，拍卖渠道会出现在这里。</p></div>
      </section>
    </template>
    <div v-else-if="!loading" class="panel empty-state"><b>商品不存在或无权限查看</b><p>{{ loadError || '请确认商品归属和当前登录账号。' }}</p><RouterLink class="button button--outline" to="/workspace/listings">返回我的拍品 <ChevronRight :size="14" /></RouterLink></div>
  </div>
</template>

<style scoped>
.form-stack { display: grid; gap: 16px; }
.form-stack--readonly { opacity: .84; }
.manage-actions-side { display: grid; align-content: start; gap: 16px; }
.manage-actions-side .panel { display: grid; gap: 14px; }
.action-help { color: var(--muted); font-size: 13px; line-height: 1.7; }
.managed-image-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(150px, 1fr)); gap: 12px; margin-top: 16px; }
.managed-image { position: relative; overflow: hidden; min-height: 140px; border: 1px solid var(--line); border-radius: 4px; background: var(--surface-muted); }
.managed-image img { width: 100%; height: 140px; object-fit: cover; }
.managed-image span { position: absolute; right: 8px; bottom: 8px; padding: 3px 7px; border-radius: 3px; background: rgba(27, 30, 26, .8); color: #fff; font-size: 12px; }
.image-add-card { display: grid; min-height: 140px; place-content: center; gap: 8px; border: 1px dashed var(--line-strong); border-radius: 4px; background: transparent; color: var(--muted); }
.image-add-card:hover { border-color: var(--ink); color: var(--ink); }
.hidden-file-input { display: none; }
</style>
