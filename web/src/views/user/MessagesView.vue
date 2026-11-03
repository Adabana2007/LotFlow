<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Client, type StompSubscription } from '@stomp/stompjs'
import { MessageCircle, Search, ShieldAlert, Wifi, WifiOff } from 'lucide-vue-next'
import PageHeading from '@/components/PageHeading.vue'
import StatusPill from '@/components/StatusPill.vue'
import { apiRequest, errorMessage } from '@/api/client'
import { useAuthStore } from '@/stores/auth'
import type {
  ConversationSummaryVO,
  CursorPage,
  MessageVO,
} from '@/types/api'
import { formatDateTime } from '@/utils/format'

interface RealtimeTicketVO {
  ticket: string
  expiresAt: string
}

interface RealtimeMessageEnvelope {
  type: string
  resourceId: string
  sequence: number
  occurredAt: string
  data: MessageVO
}

const route = useRoute()
const auth = useAuthStore()
const loading = ref(true)
const messageLoading = ref(false)
const sending = ref(false)
const loadError = ref('')
const messageError = ref('')
const conversations = ref<ConversationSummaryVO[]>([])
const messages = ref<MessageVO[]>([])
const selectedId = ref('')
const search = ref('')
const draft = ref('')
const connected = ref(false)
const stream = ref<HTMLElement | null>(null)
const subscriptions = new Map<string, StompSubscription>()
let client: Client | null = null

const selected = computed(() => conversations.value.find((item) => item.id === selectedId.value) || null)
const visibleConversations = computed(() => {
  const keyword = search.value.trim().toLowerCase()
  if (!keyword) return conversations.value
  return conversations.value.filter((item) => `${item.listingTitle}${item.otherPartyName}${item.lastMessage}`.toLowerCase().includes(keyword))
})
const totalUnread = computed(() => conversations.value.reduce((sum, item) => sum + item.unreadCount, 0))

function notifyUnread(): void {
  window.dispatchEvent(new CustomEvent('lotflow:unread-changed', { detail: totalUnread.value }))
}

function sortMessages(): void {
  messages.value = [...new Map(messages.value.map((message) => [message.id, message])).values()]
    .sort((left, right) => left.sequence - right.sequence)
}

async function scrollToBottom(): Promise<void> {
  await nextTick()
  if (stream.value) stream.value.scrollTop = stream.value.scrollHeight
}

function updateConversation(message: MessageVO): ConversationSummaryVO | undefined {
  const summary = conversations.value.find((item) => item.id === message.conversationId)
  if (!summary) return undefined
  summary.lastSequence = Math.max(summary.lastSequence, message.sequence)
  summary.lastMessage = message.content
  summary.lastMessageAt = message.occurredAt
  return summary
}

function appendMessage(message: MessageVO): boolean {
  if (messages.value.some((item) => item.id === message.id || item.sequence === message.sequence)) return false
  messages.value.push(message)
  sortMessages()
  return true
}

async function markRead(sequence: number): Promise<void> {
  if (!selected.value || sequence <= 0) return
  try {
    await apiRequest<{ unread: number }>(`/conversations/${selected.value.id}/read-cursor?sequence=${sequence}`, { method: 'PUT' })
    const summary = conversations.value.find((item) => item.id === selected.value?.id)
    if (summary) summary.unreadCount = 0
    notifyUnread()
  } catch (error) {
    messageError.value = errorMessage(error)
  }
}

async function loadMessages(id: string, resume = false): Promise<void> {
  if (!id || messageLoading.value) return
  messageLoading.value = true
  messageError.value = ''
  try {
    const lastSequence = resume ? (messages.value.at(-1)?.sequence || 0) : 0
    const suffix = lastSequence > 0 ? `&afterSequence=${lastSequence}` : ''
    const page = await apiRequest<CursorPage<MessageVO>>(`/conversations/${id}/messages?limit=100${suffix}`)
    if (!resume) messages.value = []
    for (const message of page.items) appendMessage(message)
    await scrollToBottom()
    const latest = messages.value.at(-1)
    if (latest) await markRead(latest.sequence)
  } catch (error) {
    messageError.value = errorMessage(error)
  } finally {
    messageLoading.value = false
  }
}

async function selectConversation(id: string): Promise<void> {
  if (!id) return
  if (selectedId.value === id) {
    const summary = conversations.value.find((item) => item.id === id)
    if (summary) await markRead(summary.lastSequence)
    return
  }
  selectedId.value = id
  messages.value = []
  await loadMessages(id)
}

function handleRealtime(payload: RealtimeMessageEnvelope): void {
  if (payload.type !== 'MESSAGE_CREATED' || !payload.data) return
  const message = payload.data
  const summary = updateConversation(message)
  if (message.conversationId === selectedId.value) {
    if (appendMessage(message)) void scrollToBottom()
    void markRead(message.sequence)
  } else if (summary) {
    summary.unreadCount += 1
    notifyUnread()
  }
}

function subscribeConversation(conversation: ConversationSummaryVO): void {
  if (!client?.connected || subscriptions.has(conversation.id)) return
  subscriptions.set(conversation.id, client.subscribe(
    `/topic/conversations/${conversation.id}`,
    (frame) => {
      try {
        handleRealtime(JSON.parse(frame.body) as RealtimeMessageEnvelope)
      } catch {
        messageError.value = '实时消息格式异常，历史记录仍可通过重新进入页面恢复'
      }
    },
  ))
}

function subscribeAll(): void {
  for (const conversation of conversations.value) subscribeConversation(conversation)
}

async function loadConversations(): Promise<void> {
  loading.value = true
  loadError.value = ''
  try {
    const page = await apiRequest<CursorPage<ConversationSummaryVO>>('/conversations?limit=100')
    conversations.value = page.items
    subscribeAll()
    const queryId = String(route.query.conversationId || '')
    const target = page.items.find((item) => item.id === queryId) || page.items[0]
    if (target) await selectConversation(target.id)
    notifyUnread()
  } catch (error) {
    conversations.value = []
    messages.value = []
    loadError.value = errorMessage(error)
  } finally {
    loading.value = false
  }
}

async function send(): Promise<void> {
  const content = draft.value.trim()
  if (!selected.value || !content || sending.value) return
  sending.value = true
  messageError.value = ''
  try {
    const message = await apiRequest<MessageVO>(`/conversations/${selected.value.id}/messages`, {
      method: 'POST',
      body: JSON.stringify({ content }),
    })
    if (appendMessage(message)) {
      updateConversation(message)
      draft.value = ''
      await scrollToBottom()
    }
  } catch (error) {
    messageError.value = errorMessage(error)
  } finally {
    sending.value = false
  }
}

async function connectRealtime(): Promise<void> {
  client = new Client({
    reconnectDelay: 5000,
    heartbeatIncoming: 10000,
    heartbeatOutgoing: 10000,
    beforeConnect: async () => {
      const ticket = await apiRequest<RealtimeTicketVO>('/realtime/tickets', { method: 'POST' })
      const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
      client!.brokerURL = `${protocol}//${window.location.host}/ws?ticket=${encodeURIComponent(ticket.ticket)}`
    },
  })
  client.onConnect = () => {
    connected.value = true
    subscribeAll()
    if (selectedId.value) void loadMessages(selectedId.value, true)
  }
  client.onWebSocketClose = () => {
    connected.value = false
    subscriptions.clear()
  }
  client.onStompError = (frame) => {
    connected.value = false
    messageError.value = frame.headers.message || '实时连接出现异常，正在尝试恢复'
  }
  client.activate()
}

onMounted(async () => {
  await loadConversations()
  try {
    await connectRealtime()
  } catch (error) {
    ElMessage.warning(errorMessage(error))
  }
})

onBeforeUnmount(() => {
  for (const subscription of subscriptions.values()) subscription.unsubscribe()
  subscriptions.clear()
  void client?.deactivate()
})
</script>

<template>
  <div class="workspace-page messages-page">
    <PageHeading eyebrow="消息" title="把交易沟通留在平台内" description="消息先保存再推送；断线重连后会从最后序号补齐，站外转账与联系方式会被风控标记。">
      <template #actions><StatusPill :text="connected ? '实时连接正常' : '正在恢复实时连接'" :tone="connected ? 'success' : 'warning'" /></template>
    </PageHeading>
    <div v-loading="loading" class="messages-shell panel">
      <section class="conversation-list">
        <label class="conversation-search"><Search :size="15" /><input v-model="search" placeholder="搜索会话或商品" /></label>
        <div class="conversation-summary"><Wifi v-if="connected" :size="14" /><WifiOff v-else :size="14" /><span>{{ connected ? '实时更新' : '连接恢复中' }}</span><b>{{ totalUnread }} 条未读</b></div>
        <button v-for="conversation in visibleConversations" :key="conversation.id" class="conversation-item" :class="{ active: selectedId === conversation.id }" type="button" @click="selectConversation(conversation.id)">
          <span class="avatar">{{ conversation.otherPartyName.slice(0, 1) }}</span>
          <div><b>{{ conversation.otherPartyName }}</b><span>{{ conversation.listingTitle }}</span><p>{{ conversation.lastMessage || '还没有消息' }}</p></div>
          <small>{{ conversation.unreadCount ? `${conversation.unreadCount} 未读` : formatDateTime(conversation.lastMessageAt) }}</small>
        </button>
        <div v-if="!visibleConversations.length && !loading" class="conversation-list-empty"><MessageCircle :size="22" /><b>{{ loadError ? '会话读取失败' : '暂无会话' }}</b><p>{{ loadError || '可以从商品详情联系卖家，已有会话会自动出现在这里。' }}</p></div>
      </section>

      <section v-if="selected" class="chat-main">
        <header class="chat-header"><div><b>{{ selected.otherPartyName }}</b><span>{{ selected.listingTitle }}</span></div><StatusPill :text="selected.status === 'OPEN' ? '会话进行中' : '会话已关闭'" :tone="selected.status === 'OPEN' ? 'success' : 'neutral'" /></header>
        <div ref="stream" v-loading="messageLoading" class="message-stream">
          <div v-if="messages.length" class="chat-date">最近消息</div>
          <div v-for="message in messages" :key="message.id" class="message-row" :class="{ buyer: message.senderId === auth.user?.id }">
            <div><p>{{ message.content }}</p><span>{{ formatDateTime(message.occurredAt) }} · 序号 {{ message.sequence }}</span></div>
          </div>
          <div v-if="!messages.length && !messageLoading" class="empty-state"><MessageCircle :size="22" /><b>{{ messageError ? '消息读取失败' : '从第一条消息开始' }}</b><p>{{ messageError || '交易事实和沟通记录会分别安全保存。' }}</p></div>
          <div v-if="messageError && messages.length" class="chat-inline-error">{{ messageError }}</div>
          <div class="chat-risk-tip"><ShieldAlert :size="15" /><span>风控只提示和标记风险，不会自动封号；订单履约沟通仍受平台记录保护。</span></div>
        </div>
        <div class="chat-composer"><textarea v-model="draft" rows="3" maxlength="4000" placeholder="输入消息；平台会保留与订单相关的沟通记录" @keydown.ctrl.enter.prevent="send" /><div><span>Ctrl + Enter 发送</span><button class="button button--dark button--with-icon" type="button" :disabled="sending || !draft.trim()" @click="send">{{ sending ? '发送中…' : '发送' }}</button></div></div>
      </section>
      <div v-else class="empty-state chat-main-placeholder"><MessageCircle :size="24" /><b>选择一个会话</b><p>会话列表、未读数和实时消息都在这里管理。</p></div>

      <aside class="chat-context">
        <h3>会话边界</h3><p>消息会先安全保存，再实时推送；断线后重新进入仍可查看历史。</p><div class="rule-line"><b>消息记录</b><span>发送成功即保存</span></div><div class="rule-line"><b>实时更新</b><span>新消息与未读状态</span></div><div class="rule-line"><b>风控</b><span>人工复核，不自动处罚</span></div>
        <div class="chat-context-empty"><MessageCircle :size="18" /><span>已读位置只影响未读统计，不改变交易事实</span></div>
      </aside>
    </div>
  </div>
</template>

<style scoped>
.conversation-summary { display: flex; align-items: center; gap: 6px; margin: 0 4px 8px; color: var(--muted); font-size: 12px; }
.conversation-summary b { margin-left: auto; color: var(--ink); }
.chat-main-placeholder { border: 0; }
.chat-inline-error { margin: 12px 16px; padding: 9px 11px; border: 1px solid #e1b2a9; background: var(--accent-soft); color: var(--accent-dark); font-size: 12px; }
</style>
