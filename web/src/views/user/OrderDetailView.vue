<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { RouterLink, useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { AlertCircle, MessageSquareText, PackageCheck, RotateCcw, ShieldCheck, Truck } from 'lucide-vue-next'
import PageHeading from '@/components/PageHeading.vue'
import ProductArtwork from '@/components/ProductArtwork.vue'
import StatusPill from '@/components/StatusPill.vue'
import CountdownText from '@/components/CountdownText.vue'
import { apiRequest, createIdempotencyKey, errorMessage } from '@/api/client'
import { useAuthStore } from '@/stores/auth'
import type { ListingVO, OrderVO, WalletVO } from '@/types/api'
import { listingToMarketLot } from '@/utils/catalog'
import { formatMoney, statusText } from '@/utils/format'

const route = useRoute()
const auth = useAuthStore()
const order = ref<OrderVO | null>(null)
const listing = ref<ListingVO | null>(null)
const wallet = ref<WalletVO | null>(null)
const loading = ref(true)
const acting = ref(false)
const loadError = ref('')
const disputeVisible = ref(false)
const disputeForm = reactive({ reason: '', evidenceSummary: '' })
const shipmentVisible = ref(false)
const shipmentForm = reactive({ carrier: '', trackingNo: '' })

const isPendingPayment = computed(() => ['PENDING_PAYMENT', 'SECOND_CHANCE'].includes(order.value?.status || ''))
const isShipped = computed(() => order.value?.status === 'SHIPPED')
const isBuyer = computed(() => order.value?.buyerId === auth.user?.id)
const isSeller = computed(() => order.value?.sellerId === auth.user?.id)
const canShip = computed(() => isSeller.value && order.value?.status === 'PAID')
const canOfferSecondChance = computed(() => isSeller.value
  && order.value?.status === 'BUYER_DEFAULTED' && !order.value?.offeredSecondChance)
const canOpenDispute = computed(() => isBuyer.value && order.value?.status === 'RECEIVED'
  && Boolean(order.value?.disputeDeadline) && new Date(order.value!.disputeDeadline!).getTime() > Date.now())
const sourceLabel = computed(() => order.value?.sourceType === 'SECKILL' ? '24h 秒杀' : order.value?.sourceType === 'SHOP' ? '一口价商城' : '拍卖成交')
const walletBalance = computed(() => wallet.value ? formatMoney(wallet.value.availableBalance) : '暂不可用')
const lot = computed(() => listing.value
  ? listingToMarketLot(listing.value, null, 0, order.value?.sourceType === 'SECKILL' ? 'seckill' : order.value?.sourceType === 'SHOP' ? 'shop' : 'auction')
  : null)

async function load(): Promise<void> {
  loading.value = true
  loadError.value = ''
  const id = String(route.params.id)
  try {
    order.value = await apiRequest<OrderVO>(`/orders/${id}`)
    const [listingResult, walletResult] = await Promise.all([
      apiRequest<ListingVO>(`/listings/${order.value.listingId}`).catch(() => null),
      auth.isAuthenticated ? apiRequest<WalletVO>('/wallet').catch(() => null) : Promise.resolve(null),
    ])
    listing.value = listingResult
    wallet.value = walletResult
  } catch (error) {
    order.value = null
    listing.value = null
    loadError.value = errorMessage(error)
  } finally {
    loading.value = false
  }
}

async function pay(): Promise<void> {
  if (!order.value || acting.value) return
  acting.value = true
  try {
    order.value = await apiRequest<OrderVO>(`/orders/${order.value.id}/payments`, {
      method: 'POST',
      headers: { 'Idempotency-Key': createIdempotencyKey('payment') },
    })
    ElMessage.success('钱包付款成功，订单已进入履约流程')
  } catch (error) {
    ElMessage.error(errorMessage(error))
    if (String((error as { status?: number }).status) === '409') await load()
  } finally {
    acting.value = false
  }
}

async function confirmReceipt(): Promise<void> {
  if (!order.value || acting.value) return
  acting.value = true
  try {
    order.value = await apiRequest<OrderVO>(`/orders/${order.value.id}/receipt-confirmations`, { method: 'POST' })
    ElMessage.success('已确认收货，资金将等待争议期结束后结算')
  } catch (error) {
    ElMessage.error(errorMessage(error))
  } finally {
    acting.value = false
  }
}

async function ship(): Promise<void> {
  if (!order.value || acting.value) return
  if (!shipmentForm.carrier.trim() || !shipmentForm.trackingNo.trim()) {
    ElMessage.warning('请填写承运方和运单号')
    return
  }
  acting.value = true
  try {
    order.value = await apiRequest<OrderVO>(`/orders/${order.value.id}/shipments`, {
      method: 'POST',
      body: JSON.stringify(shipmentForm),
    })
    shipmentVisible.value = false
    ElMessage.success('发货信息已记录，买家可以开始确认收货倒计时')
  } catch (error) {
    ElMessage.error(errorMessage(error))
  } finally {
    acting.value = false
  }
}

async function offerSecondChance(): Promise<void> {
  if (!order.value || acting.value) return
  acting.value = true
  try {
    order.value = await apiRequest<OrderVO>(`/orders/${order.value.id}/second-chance-offers`, { method: 'POST' })
    ElMessage.success('已向第二高出价者发出顺延付款资格')
  } catch (error) {
    ElMessage.error(errorMessage(error))
  } finally {
    acting.value = false
  }
}

async function openDispute(): Promise<void> {
  if (!order.value || acting.value) return
  if (!disputeForm.reason.trim()) {
    ElMessage.warning('请填写争议原因')
    return
  }
  acting.value = true
  try {
    order.value = await apiRequest<OrderVO>(`/orders/${order.value.id}/disputes`, {
      method: 'POST',
      body: JSON.stringify(disputeForm),
    })
    disputeVisible.value = false
    ElMessage.success('争议已发起，订单资金暂停结算')
  } catch (error) {
    ElMessage.error(errorMessage(error))
  } finally {
    acting.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="workspace-page" v-loading="loading">
    <template v-if="order">
      <PageHeading eyebrow="订单详情与支付" :title="isPendingPayment ? '订单详情与支付' : '订单详情'" :description="isPendingPayment ? '秒杀订单 · 单件库存已为你锁定。' : '状态与金额以平台订单记录为准。'">
        <template #actions><RouterLink class="button button--outline button--with-icon" to="/messages"><MessageSquareText :size="15" /> 联系卖家</RouterLink></template>
      </PageHeading>
      <div class="order-status-banner" :class="{ 'order-status-banner--dark': isPendingPayment }"><div><StatusPill :text="sourceLabel" :tone="order.sourceType === 'SECKILL' ? 'seckill' : order.sourceType === 'SHOP' ? 'shop' : 'auction'" /><StatusPill :text="statusText(order.status)" tone="warning" /><h3>{{ isPendingPayment ? '请在倒计时结束前完成虚拟钱包付款；超时后库存自动释放。' : '订单状态变化都会留下完整记录。' }}</h3><p>订单号 {{ order.orderNo }}</p></div><div v-if="order.paymentDeadline" class="order-status-banner__time"><span>剩余付款时间</span><CountdownText :deadline="order.paymentDeadline" large /><small>以平台订单时间为准</small></div></div>
      <div class="order-detail-grid">
        <div class="order-detail-main">
          <section class="panel order-product-panel"><div class="panel-head"><h3>订单信息</h3><StatusPill :text="listing ? '订单已关联商品' : '商品摘要暂不可用'" :tone="listing ? 'success' : 'warning'" /></div><div class="order-product"><ProductArtwork :kind="lot?.artwork || 'camera'" :tone="lot?.tone || 'tone-celadon'" /><div><h3>{{ lot?.title || `商品 #${order.listingId}` }}</h3><p>{{ lot?.subtitle || '商品公开摘要暂时不可用，订单金额和状态仍以平台记录为准。' }}</p><StatusPill :text="statusText(order.status)" :tone="isPendingPayment ? 'warning' : 'success'" /></div></div><dl class="detail-list detail-list--wide"><div><dt>订单号</dt><dd>{{ order.orderNo }}</dd></div><div><dt>卖家编号</dt><dd>{{ order.sellerId }}</dd></div><div><dt>渠道来源</dt><dd>{{ sourceLabel }}</dd></div><div><dt>保证金</dt><dd>{{ order.sourceType === 'SECKILL' ? '秒杀免买家保证金' : formatMoney(order.depositApplied) }}</dd></div></dl></section>
          <section class="panel timeline-panel"><div class="panel-head"><h3>状态时间线</h3><span class="muted-note">所有时间以平台记录为准</span></div><div class="status-timeline"><div class="done"><i /><div><b>订单创建 / 库存锁定</b><p>秒杀下单后，系统会为这件商品保留 15 分钟库存。</p></div><span>已完成</span></div><div :class="{ done: !isPendingPayment, current: isPendingPayment }"><i /><div><b>等待虚拟钱包付款</b><p>重复提交不会重复扣款，付款结果以平台记录为准。</p></div><span>{{ isPendingPayment ? '进行中' : '已完成' }}</span></div><div :class="{ done: order.status === 'PAID' || isShipped }"><i /><div><b>付款完成 / 进入履约</b><p>支付成功后才会进入待发货状态。</p></div><span>{{ order.status === 'PAID' || isShipped ? '已完成' : '待发生' }}</span></div><div><i /><div><b>超时释放库存</b><p>超过付款截止时间后，系统会自动释放库存。</p></div><span>{{ isPendingPayment ? '待执行' : '不适用' }}</span></div></div></section>
        </div>
        <aside class="order-payment-side">
          <section class="panel payment-panel">
            <div class="panel-head"><h3>支付与履约</h3><StatusPill text="平台虚拟钱包" tone="success" /></div>
            <span>可用余额</span><strong class="wallet-balance">{{ walletBalance }}</strong>
            <dl class="detail-list"><div><dt>商品金额</dt><dd>{{ formatMoney(order.amount) }}</dd></div><div><dt>保证金</dt><dd>{{ order.sourceType === 'SECKILL' ? '免保证金' : formatMoney(order.depositApplied) }}</dd></div><div class="emphasis"><dt>{{ isSeller ? '应收货款' : '应付' }}</dt><dd>{{ formatMoney(order.amount) }}</dd></div></dl>
            <button v-if="isBuyer && isPendingPayment" class="button button--accent button--wide" type="button" :disabled="acting" @click="pay">{{ acting ? '正在支付…' : '确认使用钱包付款' }}</button>
            <button v-else-if="isBuyer && isShipped" class="button button--dark button--wide button--with-icon" type="button" :disabled="acting" @click="confirmReceipt"><PackageCheck :size="15" /> 确认收货</button>
            <button v-else-if="canShip" class="button button--dark button--wide button--with-icon" type="button" @click="shipmentVisible = true"><Truck :size="15" /> 填写发货信息</button>
            <button v-else-if="canOfferSecondChance" class="button button--accent button--wide button--with-icon" type="button" :disabled="acting" @click="offerSecondChance"><RotateCcw :size="15" /> 顺延第二高出价者</button>
            <button v-else class="button button--outline button--wide" type="button" disabled>{{ statusText(order.status) }}</button>
            <p class="payment-caption">使用平台虚拟钱包结算，不连接银行卡；每次余额变化都会保留资金记录。</p>
          </section>
          <section class="panel order-help-panel"><ShieldCheck :size="18" /><div><b>资金安全边界</b><p>货款先进入平台担保账户，确认收货且争议期结束后再结算给卖家。</p></div></section>
          <RouterLink v-if="order.disputeStatus" class="button button--outline button--wide" :to="`/orders/${order.id}/disputes/${order.id}`">查看争议证据与进度</RouterLink>
          <button v-else-if="canOpenDispute" class="text-danger-button" type="button" @click="disputeVisible = true">对订单发起争议</button>
          <section class="warm-note"><AlertCircle :size="16" /><p>秒杀库存只锁定一次。刷新页面不会重置付款时限，也不会重新占用库存。</p></section>
        </aside>
      </div>
    </template>
    <div v-else-if="!loading" class="panel empty-state"><PackageCheck :size="24" /><b>订单不存在或无权限</b><p>{{ loadError || '请确认订单编号和当前登录账号。' }}</p><RouterLink class="button button--outline" to="/orders">返回订单入口</RouterLink></div>
    <ElDialog v-model="disputeVisible" title="发起交易争议" width="560px"><div class="dialog-form"><label class="form-field"><span>争议原因</span><input v-model="disputeForm.reason" maxlength="2000" placeholder="例如：商品与描述明显不符" /></label><label class="form-field"><span>证据摘要</span><textarea v-model="disputeForm.evidenceSummary" rows="4" maxlength="3000" placeholder="说明图片、沟通记录和物流证据；不要填写隐私密钥" /></label></div><template #footer><button class="button button--outline" type="button" @click="disputeVisible = false">取消</button><button class="button button--accent" type="button" :disabled="acting" @click="openDispute">提交人工处理</button></template></ElDialog>
    <ElDialog v-model="shipmentVisible" title="填写发货信息" width="520px">
      <div class="dialog-form"><label class="form-field"><span>承运方</span><input v-model="shipmentForm.carrier" maxlength="80" placeholder="例如：顺丰速运" /></label><label class="form-field"><span>运单号</span><input v-model="shipmentForm.trackingNo" maxlength="120" placeholder="填写真实运单号" /></label></div>
      <template #footer><button class="button button--outline" type="button" @click="shipmentVisible = false">取消</button><button class="button button--dark" type="button" :disabled="acting" @click="ship">确认发货</button></template>
    </ElDialog>
  </div>
</template>
