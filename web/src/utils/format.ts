export function formatMoney(value?: string | number | null): string {
  if (value === undefined || value === null || value === '') return '—'
  const text = String(value)
  const [integer = '0', decimal = ''] = text.split('.')
  const normalizedInteger = integer.replace(/\B(?=(\d{3})+(?!\d))/g, ',')
  return `¥${normalizedInteger}.${(decimal + '00').slice(0, 2)}`
}

export function formatDateTime(value?: string | null): string {
  if (!value) return '未设置'
  return new Intl.DateTimeFormat('zh-CN', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  }).format(new Date(value))
}

export function formatFullDate(value?: string | null): string {
  if (!value) return '未设置'
  return new Intl.DateTimeFormat('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  }).format(new Date(value))
}

export function toDateTimeLocal(date: Date): string {
  const pad = (part: number) => String(part).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}`
}

export function statusText(status?: string | null): string {
  const labels: Record<string, string> = {
    DRAFT: '草稿',
    PENDING_REVIEW: '待审核',
    APPROVED: '已通过',
    CHANGES_REQUIRED: '需修改',
    REJECTED: '已驳回',
    SCHEDULED: '待开始',
    AUCTION_LIVE: '拍卖中',
    WAITING_SELLER_DECISION: '待卖家处置',
    SECKILL: '秒杀中',
    SHOP: '商城中',
    RETURNED: '已退回库存',
    SOLD_PENDING_PAYMENT: '成交待付款',
    PENDING_PAYMENT: '待付款',
    PAID: '已付款',
    SHIPPED: '已发货',
    RECEIVED: '已收货',
    DISPUTED: '争议中',
    COMPLETED: '已完成',
    CANCELLED: '已取消',
    SECOND_CHANCE: '顺延待付款',
    BUYER_DEFAULTED: '买家违约',
    LIVE: '竞拍中',
    ENDED: '已结束',
    UNSOLD: '流拍',
    ACTIVE: '正常',
    DISABLED: '已停用',
    RESTRICTED: '已限制',
    LOW: '低风险',
    MEDIUM: '中风险',
    HIGH: '高风险',
    PENDING: '待处理',
    MANUAL_PENDING: '待人工',
    QUEUED: '排队中',
    RUNNING: '生成中',
    SUCCEEDED: '已完成',
    FAILED: '失败',
    FALLBACK: '降级完成',
    OPEN: '处理中',
    RESOLVED: '已解决',
    BUYER_WINS: '买家主张成立',
    SELLER_WINS: '卖家主张成立',
    BUYER_WITHDRAWS: '买家撤回争议',
    RESERVED: '库存已锁定',
    ORDERED: '已生成订单',
    EXPIRED: '已过期',
    WAITING: '等待决定',
    DECIDED: '已决定',
  }
  return status ? labels[status] || status : '未知'
}

export function categoryText(code?: string | null): string {
  const labels: Record<string, string> = {
    CAMERA: '相机与影像',
    CARD: '收藏卡',
    FIGURE: '手办与模型',
    INSTRUMENT: '乐器',
    GAME_CONSOLE: '复古游戏机',
    HANDICRAFT: '手工作品',
    COLLECTIBLE: '收藏品',
    FASHION: '服饰与配饰',
    HOME: '家居器物',
    OTHER: '其他物品',
  }
  return code ? labels[code] || code : '未分类'
}

export function conditionText(code?: string | null): string {
  const labels: Record<string, string> = {
    NEW: '全新',
    LIKE_NEW: '近全新',
    GOOD: '正常使用',
    FAIR: '使用痕迹明显',
    POOR: '功能或外观问题较多',
    FOR_PARTS: '仅作配件',
  }
  return code ? labels[code] || code : '未说明'
}

export function sourceText(source?: string | null): string {
  const labels: Record<string, string> = {
    AUCTION: '拍卖成交',
    SECKILL: '秒杀',
    SHOP: '商城',
  }
  return source ? labels[source] || source : '未知渠道'
}

export function channelText(channel?: string | null): string {
  const labels: Record<string, string> = {
    AUCTION: '拍卖',
    SECKILL: '秒杀',
    SHOP: '商城',
  }
  return channel ? labels[channel] || channel : '未进入渠道'
}

export function aiTaskTypeText(taskType?: string | null): string {
  const labels: Record<string, string> = {
    LISTING_DRAFT: '发布建议',
    LISTING_REVIEW: '商品风险提示',
    SELLER_ANALYSIS: '经营分析',
    DISPUTE_SUMMARY: '争议摘要',
    CHAT_RISK: '聊天风险标签',
  }
  return taskType ? labels[taskType] || taskType : '未记录'
}

export function assetClass(index: number): string {
  const tones = ['tone-celadon', 'tone-sand', 'tone-stone', 'tone-rose']
  return tones[index % tones.length] || 'tone-sand'
}

export function initials(name?: string): string {
  return (name || '访客').trim().slice(0, 1)
}
