<script setup lang="ts">
import { computed, onBeforeUnmount, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Bot, ImagePlus, RotateCw, Sparkles, Trash2, WandSparkles } from 'lucide-vue-next'
import PageHeading from '@/components/PageHeading.vue'
import StatusPill from '@/components/StatusPill.vue'
import { apiRequest, apiRequestWithMeta, errorMessage } from '@/api/client'
import { useAuthStore } from '@/stores/auth'
import type { AiTaskVO } from '@/types/api'
import { statusText, toDateTimeLocal } from '@/utils/format'
import { uploadListingImage, validateImage } from '@/utils/upload'

interface SelectedImage {
  file: File
  previewUrl: string
  uploaded: boolean
}

const router = useRouter()
const auth = useAuthStore()
const saving = ref(false)
const generating = ref(false)
const uploading = ref(false)
const draftId = ref<string | null>(null)
const aiTask = ref<AiTaskVO | null>(null)
const aiError = ref('')
const uploadError = ref('')
const fileInput = ref<HTMLInputElement | null>(null)
const selectedImages = ref<SelectedImage[]>([])
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
const form = reactive({
  title: '',
  categoryCode: 'CAMERA',
  conditionCode: 'GOOD',
  description: '',
  defects: '',
  startPrice: '',
  seckillPrice: '',
  shopPrice: '',
  durationHours: 72,
  scheduledStart: toDateTimeLocal(new Date(Date.now() + 24 * 60 * 60_000)),
  autoDowngrade: true,
  chatEnabled: true,
  sellerDescription: '',
})

const priceSummary = computed(() => `${form.shopPrice || '—'} ≤ ${form.seckillPrice || '—'} ≤ ${form.startPrice || '—'}`)

function asCents(value: string): bigint | null {
  if (!/^\d+(\.\d{1,2})?$/.test(value)) return null
  const [whole = '0', fraction = ''] = value.split('.')
  return BigInt(whole) * 100n + BigInt((fraction + '00').slice(0, 2))
}

const priceValid = computed(() => {
  const start = asCents(form.startPrice)
  if (start === null || start <= 0n) return false
  if (!form.autoDowngrade && !form.seckillPrice && !form.shopPrice) return true
  const seckill = asCents(form.seckillPrice)
  const shop = asCents(form.shopPrice)
  return seckill !== null && shop !== null && shop > 0n && shop <= seckill && seckill <= start
})

const canSelectMore = computed(() => selectedImages.value.length < 6)

function ensureEditable(): boolean {
  if (auth.isAuthenticated) return true
  ElMessage.info('请先登录后再发布商品。')
  void router.push({ path: '/auth', query: { redirect: '/workspace/publish' } })
  return false
}

function validate(): boolean {
  if (!form.title.trim() || !form.description.trim() || !form.defects.trim()) {
    ElMessage.warning('请填写标题、描述和已知瑕疵')
    return false
  }
  if (!priceValid.value) {
    ElMessage.warning('价格必须满足 0 < 商城价 ≤ 秒杀价 ≤ 起拍价')
    return false
  }
  const scheduled = new Date(form.scheduledStart).getTime()
  if (!Number.isFinite(scheduled) || scheduled <= Date.now()) {
    ElMessage.warning('排期时间必须晚于当前时间')
    return false
  }
  return true
}

function chooseImages(): void {
  if (canSelectMore.value) fileInput.value?.click()
}

function handleFiles(event: Event): void {
  const input = event.target as HTMLInputElement
  const files = Array.from(input.files || [])
  input.value = ''
  for (const file of files) {
    if (selectedImages.value.length >= 6) {
      ElMessage.warning('最多选择 6 张商品图片')
      break
    }
    try {
      validateImage(file)
      selectedImages.value.push({
        file,
        previewUrl: URL.createObjectURL(file),
        uploaded: false,
      })
    } catch (error) {
      ElMessage.warning(errorMessage(error))
    }
  }
  uploadError.value = ''
}

function removeImage(index: number): void {
  const image = selectedImages.value[index]
  if (!image || image.uploaded) return
  URL.revokeObjectURL(image.previewUrl)
  selectedImages.value.splice(index, 1)
}

async function generateDraft(): Promise<void> {
  if (!ensureEditable() || generating.value) return
  if (!form.sellerDescription.trim()) {
    ElMessage.warning('请先用自己的话描述物品')
    return
  }
  generating.value = true
  aiError.value = ''
  try {
    const submitted = await apiRequest<{ taskId: string }>('/ai/listing-drafts', {
      method: 'POST',
      body: JSON.stringify({ sellerDescription: form.sellerDescription, imageKeys: [] }),
    })
    for (let attempt = 0; attempt < 8; attempt += 1) {
      await new Promise((resolve) => window.setTimeout(resolve, 1000))
      aiTask.value = await apiRequest<AiTaskVO>(`/ai/tasks/${submitted.taskId}`)
      if (['SUCCEEDED', 'FAILED', 'FALLBACK'].includes(aiTask.value.status)) break
    }
    if (aiTask.value?.status === 'FAILED') aiError.value = aiTask.value.errorCode || '发布建议生成失败，请继续人工填写。'
  } catch (error) {
    aiError.value = errorMessage(error)
  } finally {
    generating.value = false
  }
}

async function createDraft(): Promise<string | null> {
  if (draftId.value) return draftId.value
  if (!validate() || saving.value) return null
  saving.value = true
  try {
    const result = await apiRequestWithMeta<void>('/listings', {
      method: 'POST',
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
        seckillPrice: form.seckillPrice || null,
        shopPrice: form.shopPrice || null,
        chatEnabled: form.chatEnabled,
      }),
    })
    const location = result.response.headers.get('Location') || ''
    draftId.value = location.split('/').pop() || null
    if (!draftId.value) throw new Error('草稿已创建，但未读取到商品编号')
    return draftId.value
  } catch (error) {
    ElMessage.error(errorMessage(error))
    return null
  } finally {
    saving.value = false
  }
}

async function uploadSelectedImages(id: string): Promise<void> {
  if (uploading.value) return
  uploading.value = true
  uploadError.value = ''
  try {
    for (const [index, image] of selectedImages.value.entries()) {
      if (image.uploaded) continue
      await uploadListingImage(id, image.file, index)
      image.uploaded = true
    }
  } catch (error) {
    uploadError.value = errorMessage(error)
    throw error
  } finally {
    uploading.value = false
  }
}

async function saveAndManage(): Promise<void> {
  if (!ensureEditable()) return
  const id = await createDraft()
  if (!id) return
  try {
    await uploadSelectedImages(id)
    ElMessage.success('草稿和已选图片已保存')
    await router.push(`/workspace/listings/${id}`)
  } catch {
    ElMessage.warning('草稿已保存，请在拍品管理页重试上传图片')
    await router.push(`/workspace/listings/${id}`)
  }
}

async function submitReview(): Promise<void> {
  if (!ensureEditable()) return
  if (!selectedImages.value.length && !draftId.value) {
    ElMessage.warning('请至少选择一张商品图片，或先保存草稿后再补充')
    return
  }
  const id = await createDraft()
  if (!id) return
  try {
    await uploadSelectedImages(id)
    if (!selectedImages.value.length && !draftId.value) return
    const result = await apiRequest<{ reviewTaskId: string }>(`/listings/${id}/submissions`, { method: 'POST' })
    ElMessage.success(`已提交审核，任务编号 ${result.reviewTaskId}`)
    await router.push(`/workspace/listings/${id}`)
  } catch (error) {
    if (uploadError.value) {
      ElMessage.warning('图片上传未完成，草稿已保存，可在拍品管理页重试')
      await router.push(`/workspace/listings/${id}`)
      return
    }
    ElMessage.error(errorMessage(error))
  }
}

onBeforeUnmount(() => {
  for (const image of selectedImages.value) URL.revokeObjectURL(image.previewUrl)
})
</script>

<template>
  <div class="workspace-page">
    <PageHeading eyebrow="AI 发布工作台" title="先把物品讲清楚，再由人工确认发布" description="所有商品从拍卖开始；AI 只整理信息，不自动发布、改价或决定成交。">
      <template #actions><StatusPill text="人工最终确认" tone="success" /></template>
    </PageHeading>
    <div class="publish-layout">
      <section class="panel publish-form-panel">
        <div class="panel-head"><h3>商品信息</h3><StatusPill text="人工可编辑" tone="success" /></div>
        <label class="form-field"><span>标题</span><input v-model="form.title" maxlength="160" placeholder="写清品牌、型号和物品类型" /></label>
        <div class="form-grid-two">
          <label class="form-field"><span>类目</span><select v-model="form.categoryCode"><option v-for="[value, label] in categories" :key="value" :value="value">{{ label }}</option></select></label>
          <label class="form-field"><span>成色</span><select v-model="form.conditionCode"><option v-for="[value, label] in conditions" :key="value" :value="value">{{ label }}</option></select></label>
        </div>
        <label class="form-field"><span>描述</span><textarea v-model="form.description" rows="5" maxlength="5000" placeholder="说明来源、使用情况、功能和随附配件" /></label>
        <label class="form-field"><span>必须披露的瑕疵</span><textarea v-model="form.defects" rows="3" maxlength="2000" placeholder="逐项写明划痕、磨损、缺失或功能异常" /></label>
        <div class="form-grid-three">
          <label class="form-field"><span>起拍价</span><input v-model="form.startPrice" inputmode="decimal" placeholder="0.00" /></label>
          <label class="form-field"><span>秒杀价</span><input v-model="form.seckillPrice" inputmode="decimal" placeholder="不高于起拍价" /></label>
          <label class="form-field"><span>商城价 / 最低售价</span><input v-model="form.shopPrice" inputmode="decimal" placeholder="不高于秒杀价" /></label>
        </div>
        <div class="price-validation" :class="{ invalid: !priceValid }"><span>价格链校验：{{ form.autoDowngrade ? priceSummary : '由卖家后续决定降级' }}</span><b>{{ priceValid ? '通过' : '需要修正' }}</b></div>
        <div class="form-grid-two">
          <label class="form-field"><span>拍卖时长</span><select v-model="form.durationHours"><option :value="24">24 小时</option><option :value="48">48 小时</option><option :value="72">72 小时</option></select></label>
          <label class="form-field"><span>计划开始</span><input v-model="form.scheduledStart" type="datetime-local" /></label>
        </div>
        <div class="authorization-box"><div><b>流拍后自动降级</b><p>拍卖流拍 → 24h 秒杀 → 30d 商城 → 退回库存。</p></div><el-switch v-model="form.autoDowngrade" active-text="已授权" inactive-text="由我决定" /></div>
      </section>

      <section class="panel image-panel">
        <div class="panel-head"><h3>商品图片</h3><StatusPill :text="`${selectedImages.length} / 6 张`" tone="neutral" /></div>
        <div class="image-grid publish-image-grid">
          <div v-for="(image, index) in selectedImages" :key="image.previewUrl" class="publish-image-preview">
            <img :src="image.previewUrl" :alt="`商品图片 ${index + 1}`" />
            <span>{{ image.uploaded ? '已上传' : '待上传' }}</span>
            <button v-if="!image.uploaded" type="button" title="移除图片" @click="removeImage(index)"><Trash2 :size="14" /></button>
          </div>
          <button v-if="canSelectMore" class="image-placeholder publish-image-add" type="button" @click="chooseImages"><ImagePlus :size="20" /><span>添加图片</span></button>
        </div>
        <input ref="fileInput" class="hidden-file-input" type="file" accept="image/jpeg,image/png,image/webp" multiple @change="handleFiles" />
        <button class="button button--outline button--wide button--with-icon" type="button" :disabled="!canSelectMore || uploading" @click="chooseImages"><ImagePlus :size="15" />选择商品图片</button>
        <div v-if="uploadError" class="warning-block"><RotateCw :size="15" /><p>{{ uploadError }}<br />草稿会保留，可在拍品管理页重新上传。</p></div>
        <h4>披露检查</h4>
        <div class="metadata-note">请确认图片清晰、瑕疵可辨、配件完整，并在文字中逐项说明可见磨损和功能问题。最终内容由卖家确认。</div>
      </section>

      <aside class="panel ai-publish-panel">
        <div class="panel-head"><h3><Bot :size="16" /> AI 发布助手</h3><Sparkles :size="17" /></div>
        <label class="form-field"><span>用你自己的话描述物品</span><textarea v-model="form.sellerDescription" rows="6" placeholder="例如：家里的旧相机，功能正常，镜头边缘有磨损，附原装皮套" /></label>
        <button class="button button--accent button--wide button--with-icon" type="button" :disabled="generating" @click="generateDraft"><WandSparkles :size="15" />{{ generating ? '正在生成…' : aiTask ? '重新生成建议' : '生成发布建议' }}</button>
        <div v-if="aiTask" class="ai-task-result"><div><StatusPill :text="statusText(aiTask.status)" :tone="aiTask.status === 'SUCCEEDED' ? 'success' : 'warning'" /><span>AI 建议</span></div><p>{{ aiTask.outputSummary || '正在生成建议，请稍候。' }}</p></div>
        <div v-if="aiError" class="warning-block"><RotateCw :size="15" /><p>{{ aiError }}<br />AI 失败不影响人工填写和保存草稿。</p></div>
        <div v-if="!aiTask" class="ai-publish-advice"><div><b>尚未生成建议</b><p>生成后的整理结果会显示在这里，不会自动填入表单或直接发布。</p></div></div>
        <div class="ai-boundary-mini">AI 不会自动发布、改价、出价、付款或决定成交。</div>
      </aside>
    </div>
    <div class="publish-submit-bar panel">
      <div><b>{{ draftId ? `草稿编号 ${draftId}` : '保存后进入商品管理' }}</b><p>提交审核前至少需要一张图片；审核结果和修改意见会回到拍品管理页。</p></div>
      <button class="button button--outline" type="button" :disabled="saving || uploading" @click="saveAndManage">{{ saving ? '正在保存…' : '保存草稿' }}</button>
      <button class="button button--dark" type="button" :disabled="saving || uploading" @click="submitReview">{{ uploading ? '正在上传…' : '提交审核' }}</button>
    </div>
  </div>
</template>

<style scoped>
.publish-image-grid { margin-bottom: 9px; }
.publish-image-preview { position: relative; height: 105px; overflow: hidden; border: 1px solid var(--line); border-radius: 4px; background: var(--surface-muted); }
.publish-image-preview img { width: 100%; height: 100%; object-fit: cover; }
.publish-image-preview span { position: absolute; right: 7px; bottom: 7px; padding: 3px 7px; border-radius: 3px; background: rgba(27, 30, 26, .82); color: #fff; font-size: 12px; }
.publish-image-preview button { position: absolute; top: 7px; right: 7px; display: grid; width: 28px; height: 28px; place-items: center; border: 0; border-radius: 3px; background: rgba(27, 30, 26, .82); color: #fff; }
.publish-image-add { width: 100%; cursor: pointer; }
.hidden-file-input { display: none; }
.publish-submit-bar { display: flex; align-items: center; gap: 10px; margin-top: 13px; padding: 14px 16px; }
.publish-submit-bar > div { flex: 1; }
.publish-submit-bar b { font-size: 14px; }
.publish-submit-bar p { margin-top: 4px; color: var(--muted); font-size: 12px; line-height: 1.55; }
@media (max-width: 760px) { .publish-submit-bar { align-items: stretch; flex-direction: column; } }
</style>
