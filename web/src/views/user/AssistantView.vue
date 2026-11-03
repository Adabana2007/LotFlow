<script setup lang="ts">
import { nextTick, onMounted, ref } from 'vue'
import { Bot, Plus, Send, ShieldCheck, Sparkles, Trash2 } from 'lucide-vue-next'
import PageHeading from '@/components/PageHeading.vue'
import { apiRequest, apiStream, errorMessage } from '@/api/client'
import { formatDateTime } from '@/utils/format'
import type { AssistantConversation, AssistantEvent, AssistantMessage, CursorPage } from '@/types/api'

interface ChatEntry {
  id: number
  role: 'user' | 'assistant'
  text: string
}

const question = ref('')
const loading = ref(false)
const hasToken = ref(false)
const historyLoading = ref(false)
const conversations = ref<AssistantConversation[]>([])
const activeId = ref('')
const listError = ref('')
const stream = ref<HTMLElement | null>(null)
const entries = ref<ChatEntry[]>([])
let nextId = 1

const prompts = [
  '拍卖流拍后，什么时候会进入秒杀？',
  '秒杀付款超时以后库存会怎么处理？',
  '确认收货后，货款会立刻结算给卖家吗？',
  '我的冻结保证金在哪里查看？',
]

onMounted(() => {
  void loadConversations()
})

function welcome(): ChatEntry[] {
  return [
    { id: nextId++, role: 'assistant', text: '你好，我是 LotFlow 平台助手。我可以解释拍卖、保证金、付款、履约和争议规则，也能只读查询你自己的拍品、订单与钱包。' },
    { id: nextId++, role: 'assistant', text: '我不会发布商品、出价、付款、改价、结算或处罚账号。' },
  ]
}

async function loadConversations(): Promise<void> {
  try {
    const page = await apiRequest<CursorPage<AssistantConversation>>('/ai/assistant/conversations?limit=20')
    conversations.value = page.items
    const first = page.items[0]
    if (first) {
      await select(first.id)
      return
    }
    entries.value = welcome()
  } catch (error) {
    listError.value = errorMessage(error)
    entries.value = welcome()
  }
}

async function refreshConversations(): Promise<void> {
  try {
    const page = await apiRequest<CursorPage<AssistantConversation>>('/ai/assistant/conversations?limit=20')
    conversations.value = page.items
  } catch {
    // 列表刷新失败只影响侧栏，当前对话已经完成，重新进入页面会再拉一次。
  }
}

async function select(id: string): Promise<void> {
  if (!id || loading.value) return
  activeId.value = id
  historyLoading.value = true
  try {
    const messages = await apiRequest<AssistantMessage[]>(
      `/ai/assistant/conversations/${id}/messages`)
    entries.value = messages.length
      ? messages.map((message) => ({ id: nextId++, role: message.role, text: message.content }))
      : welcome()
    await scrollBottom()
  } catch (error) {
    entries.value = [{ id: nextId++, role: 'assistant', text: `历史加载失败：${errorMessage(error)}` }]
  } finally {
    historyLoading.value = false
  }
}

async function createConversation(title: string): Promise<void> {
  const created = await apiRequest<AssistantConversation>('/ai/assistant/conversations', {
    method: 'POST',
    body: JSON.stringify({ title }),
  })
  conversations.value = [created, ...conversations.value]
  activeId.value = created.id
}

async function startNew(): Promise<void> {
  if (loading.value) return
  try {
    await createConversation('新会话')
    entries.value = welcome()
  } catch (error) {
    listError.value = errorMessage(error)
  }
}

async function remove(id: string): Promise<void> {
  if (loading.value) return
  if (!window.confirm('删除后这段对话无法恢复，确定删除？')) return
  try {
    await apiRequest(`/ai/assistant/conversations/${id}`, { method: 'DELETE' })
    conversations.value = conversations.value.filter((item) => item.id !== id)
    if (activeId.value !== id) return
    activeId.value = ''
    entries.value = welcome()
    const next = conversations.value[0]
    if (next) await select(next.id)
  } catch (error) {
    listError.value = errorMessage(error)
  }
}

async function ask(text?: string): Promise<void> {
  const content = (text || question.value).trim()
  if (!content || loading.value) return
  loading.value = true
  hasToken.value = false
  let replyIndex = -1
  try {
    if (!activeId.value) {
      await createConversation(content.slice(0, 40))
      entries.value = []
    }
    entries.value.push({ id: nextId++, role: 'user', text: content })
    // 先插入空回答，之后只通过 entries.value[index] 追加内容；直接改原始对象不会触发 Vue 更新。
    replyIndex = entries.value.push({ id: nextId++, role: 'assistant', text: '' }) - 1
    question.value = ''
    await scrollBottom()
    const response = await apiStream(`/ai/assistant/conversations/${activeId.value}/queries`, {
      method: 'POST',
      body: JSON.stringify({ question: content }),
    })
    await readStream(response, replyIndex)
  } catch (error) {
    const failure = `暂时无法回答：${errorMessage(error)}。平台规则和人工客服入口仍然可用。`
    if (replyIndex >= 0) {
      entryAt(replyIndex).text = failure
    } else {
      entries.value.push({ id: nextId++, role: 'assistant', text: failure })
    }
  } finally {
    loading.value = false
    await scrollBottom()
    await refreshConversations()
  }
}

async function readStream(response: Response, replyIndex: number): Promise<void> {
  const reader = response.body?.getReader()
  if (!reader) throw new Error('SSE stream unavailable')
  const decoder = new TextDecoder()
  const state: StreamState = { pending: '' }
  let buffer = ''
  for (;;) {
    const { done, value } = await reader.read()
    if (done) break
    buffer = (buffer + decoder.decode(value, { stream: true })).replace(/\r\n/g, '\n')
    let boundary = buffer.indexOf('\n\n')
    while (boundary >= 0) {
      applyEvent(buffer.slice(0, boundary), replyIndex, state)
      buffer = buffer.slice(boundary + 2)
      boundary = buffer.indexOf('\n\n')
    }
    await scrollBottom()
  }
  flushPending(replyIndex, state)
}

interface StreamState {
  pending: string
}

function applyEvent(block: string, replyIndex: number, state: StreamState): void {
  // 只解析以空行结尾的完整 SSE 块，跨 chunk 的半条 JSON 留到下一轮拼接。
  for (const line of block.split('\n')) {
    if (!line.startsWith('data:')) continue
    const payload = line.slice(5).trim()
    if (!payload) continue
    const event = JSON.parse(payload) as AssistantEvent
    if (event.type === 'token') {
      appendVisible(replyIndex, event.text, state)
    } else if (event.type === 'error') {
      entryAt(replyIndex).text = `暂时无法回答：${event.text}。平台规则和人工客服入口仍然可用。`
    }
  }
}

/**
 * 规则来源标记（[K1]）只在服务端事件里保留，聊天正文不展示。
 * 标记可能被拆在两个 token 里，因此先扣住形如 "[K12" 的尾部，等下一个 token 到齐再过滤。
 */
function appendVisible(replyIndex: number, delta: string, state: StreamState): void {
  const merged = state.pending + delta
  const held = markerPrefix(merged)
  const visible = (held ? merged.slice(0, merged.length - held.length) : merged).replace(/\[K\d+]/g, '')
  if (visible) {
    entryAt(replyIndex).text += visible
    hasToken.value = true
  }
  state.pending = held
}

function flushPending(replyIndex: number, state: StreamState): void {
  const tail = state.pending.replace(/\[K\d+]/g, '')
  if (tail) {
    entryAt(replyIndex).text += tail
    hasToken.value = true
  }
  state.pending = ''
}

function markerPrefix(value: string): string {
  const match = value.match(/\[(?:K\d*)?$/)
  return match ? match[0] : ''
}

async function scrollBottom(): Promise<void> {
  await nextTick()
  stream.value?.scrollTo({ top: stream.value.scrollHeight, behavior: 'smooth' })
}

function entryAt(index: number): ChatEntry {
  const entry = entries.value[index]
  if (!entry) throw new Error('assistant entry missing')
  return entry
}
</script>

<template>
  <div class="workspace-page">
    <PageHeading eyebrow="AI 平台助手" title="解释规则，不替你做交易决定" description="答案结合平台规则与只读工具；工具绑定当前用户，不能查询他人数据。">
      <template #actions><span class="status-pill status-pill--success"><ShieldCheck :size="13" />只读工具</span></template>
    </PageHeading>
    <div class="assistant-layout">
      <section class="panel assistant-chat">
        <header class="assistant-chat__header"><div><span class="assistant-bot"><Bot :size="18" /></span><div><b>LotFlow 平台助手</b><span>平台规则 · 语义检索 · 只读查询</span></div></div><span class="status-pill status-pill--success">在线</span></header>
        <div ref="stream" class="assistant-stream">
          <div v-for="entry in entries" :key="entry.id" class="assistant-message" :class="entry.role">
            <span v-if="entry.role === 'assistant'" class="assistant-bot"><Sparkles :size="15" /></span>
            <div><p>{{ entry.text }}</p></div>
          </div>
          <div v-if="historyLoading || (loading && !hasToken)" class="assistant-message assistant"><span class="assistant-bot"><Sparkles :size="15" /></span><div class="thinking-dots"><i /><i /><i /></div></div>
        </div>
        <div class="assistant-suggestions"><button v-for="prompt in prompts" :key="prompt" type="button" @click="ask(prompt)">{{ prompt }}</button></div>
        <div class="assistant-composer"><textarea v-model="question" rows="3" placeholder="询问规则、自己的订单或拍卖状态…" @keydown.ctrl.enter.prevent="ask()" /><div><span>AI 只能建议、检索、摘要和解释</span><button class="button button--accent button--with-icon" type="button" :disabled="loading" @click="ask()"><Send :size="14" />发送</button></div></div>
      </section>
      <aside class="assistant-side">
        <section class="panel assistant-conversations">
          <div class="panel-head"><h3>最近会话</h3><button class="icon-button" type="button" :disabled="loading" title="新建会话" @click="startNew"><Plus :size="15" /></button></div>
          <p v-if="listError" class="conversation-hint">{{ listError }}</p>
          <p v-else-if="!conversations.length" class="conversation-hint">直接提问会自动创建会话</p>
          <ul v-else class="conversation-list">
            <li v-for="item in conversations" :key="item.id" :class="{ 'is-active': item.id === activeId }">
              <button class="conversation-title" type="button" :disabled="loading" @click="select(item.id)">
                <span>{{ item.title }}</span>
                <em>{{ formatDateTime(item.lastMessageAt) }}</em>
              </button>
              <button class="conversation-delete" type="button" :disabled="loading" title="删除会话" @click="remove(item.id)"><Trash2 :size="13" /></button>
            </li>
          </ul>
        </section>
        <section class="assistant-boundary panel"><div class="panel-head"><h3>能力边界</h3><Sparkles :size="16" /></div><div class="ability-row allowed"><span>可以</span><p>解释平台规则、查询自己的只读数据、提供引用</p></div><div class="ability-row denied"><span>不可以</span><p>发布、出价、付款、改价、结算、惩罚或最终审核</p></div><div class="ability-row neutral"><span>故障时</span><p>保留人工入口，核心交易不等待 AI</p></div></section>
        <section class="panel assistant-context"><h3>本次上下文</h3><div class="rule-line"><b>身份</b><span>当前登录账号</span></div><div class="rule-line"><b>查询</b><span>商品、订单与钱包只读信息</span></div><div class="rule-line"><b>模型</b><span>DeepSeek</span></div><div class="rule-line"><b>追踪</b><span>回答依据与生成记录可追溯</span></div></section>
      </aside>
    </div>
  </div>
</template>
