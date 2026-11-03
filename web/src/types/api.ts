export interface CursorPage<T> {
  items: T[]
  nextCursor: string | null
}

export interface UserVO {
  id: string
  phone: string
  displayName: string
  status: string
  creditScore: number
  chatEnabled: boolean
  roles: string[]
  avatarUrl: string | null
  notifyAuctionEnding: boolean
  notifyOrderPayment: boolean
  notifyMessages: boolean
}

export interface ListingVO {
  id: string
  sellerId: string
  title: string
  description: string
  categoryCode: string
  conditionCode: string
  defects: string | null
  startPrice: string
  seckillPrice: string | null
  shopPrice: string | null
  durationHours: number
  scheduledStart: string
  autoDowngrade: boolean
  status: string
  rejectionReason: string | null
  createdAt: string
  updatedAt: string
  version: number
  coverUrl?: string | null
}

export interface AdminListingImageVO {
  id: string
  contentType: string
  sizeBytes: number
  sortOrder: number
  url: string
  expiresAt: string
}

export interface AdminListingDetailVO {
  listing: ListingVO
  images: AdminListingImageVO[]
}

export interface AuctionVO {
  id: string
  listingId: string
  sellerId: string
  startPrice: string
  currentPrice: string
  status: string
  startTime: string
  endTime: string
  extensionSeconds: number
  highestBidderId: string | null
}

export interface BidVO {
  id: string
  auctionId: string
  bidderId: string
  amount: string
  depositRequired: string
  createdAt: string
  auction: AuctionVO
}

export interface SaleChannelVO {
  id: string
  listingId: string
  channel: 'AUCTION' | 'SECKILL' | 'SHOP'
  status: string
  price: string
  startsAt: string
  endsAt: string
  orderId: string | null
}

export interface OrderVO {
  id: string
  orderNo: string
  listingId: string
  buyerId: string
  sellerId: string
  sourceType: string
  amount: string
  depositApplied: string
  status: string
  paymentDeadline: string | null
  shipDeadline: string | null
  shippedAt: string | null
  receiptDeadline: string | null
  receivedAt: string | null
  disputeDeadline: string | null
  disputeOpenedAt: string | null
  offeredSecondChance: boolean
  escrowAmount: string | null
  paidAt: string | null
  carrier: string | null
  trackingNo: string | null
  disputeStatus: string | null
  disputeReason: string | null
  disputeEvidence: string | null
  disputeResolution: string | null
  disputeResolvedAt: string | null
  reservationStatus: string | null
  reservationExpiresAt: string | null
}

export interface OrderSummaryVO {
  id: string
  orderNo: string
  listingId: string
  listingTitle: string
  buyerId: string
  sellerId: string
  counterpartyName: string
  role: 'BUYER' | 'SELLER'
  sourceType: string
  amount: string
  status: string
  offeredSecondChance: boolean
  paymentDeadline: string | null
  shipDeadline: string | null
  createdAt: string
  disputeStatus: string | null
  reservationStatus: string | null
  reservationExpiresAt: string | null
}

export interface WalletVO {
  accountId: string
  availableBalance: string
  frozenBalance: string
}

export interface WalletTransactionVO {
  id: string
  businessKey: string
  transactionType: string
  availableChange: string
  frozenChange: string
  availableAfter: string
  frozenAfter: string
  createdAt: string
}

export interface ReviewTaskVO {
  id: string
  listingId: string
  sellerId: string
  status: string
  riskLevel: string | null
  evidenceSummary: string | null
  manualRequired: boolean
  createdAt: string
}

export interface RiskEventVO {
  id: string
  messageId: string
  riskLevel: string
  categories: string
  evidenceSummary: string
  requiresHumanReview: boolean
  occurredAt: string
}

export interface AdminUserVO {
  id: string
  phone: string
  displayName: string
  status: string
  creditScore: number
  completedReviewCount: number
}

export interface AiTaskVO {
  id: string
  taskType: string
  status: string
  outputSummary: string | null
  modelName: string | null
  promptVersion: string | null
  promptTokens: number | null
  completionTokens: number | null
  latencyMs: number | null
  traceId: string | null
  errorCode: string | null
  createdAt: string
}

export interface BidHistoryVO {
  id: string
  auctionId: string
  listingId: string
  listingTitle: string
  amount: string
  depositRequired: string
  bidStatus: string
  auctionStatus: string
  currentPrice: string
  highestBidderId: string | null
  endsAt: string
  createdAt: string
}

export interface ConversationVO {
  id: string
  listingId: string
  buyerId: string
  sellerId: string
  status: string
  lastSequence: number
}

export interface MessageVO {
  id: string
  conversationId: string
  senderId: string
  sequence: number
  content: string
  occurredAt: string
}

export interface TodoItemVO {
  type: string
  resourceId: string
  title: string
  description: string
  route: string
  dueAt: string | null
}

export interface ConversationSummaryVO {
  id: string
  listingId: string
  listingTitle: string
  otherPartyId: string
  otherPartyName: string
  status: string
  lastSequence: number
  unreadCount: number
  lastMessage: string
  lastMessageAt: string | null
}

export interface DisputeVO {
  id: string
  orderId: string
  orderNo: string
  listingId: string
  listingTitle: string
  buyerId: string
  buyerName: string
  sellerId: string
  sellerName: string
  amount: string
  depositApplied: string
  escrowAmount: string
  orderStatus: string
  disputeStatus: string
  reason: string
  evidence: string
  resolution: string | null
  resolvedBy: string | null
  paidAt: string | null
  shippedAt: string | null
  receivedAt: string | null
  disputeOpenedAt: string
  createdAt: string
  resolvedAt: string | null
}

export interface DisputeTimelineVO {
  type: string
  title: string
  detail: string
  occurredAt: string
}

export interface DisputeDetailVO {
  dispute: DisputeVO
  timeline: DisputeTimelineVO[]
}

export interface SellerListingImageVO {
  id: string
  sortOrder: number
  sizeBytes: number
  contentType: string
  url: string
  expiresAt: string
}

export interface MediaUploadVO {
  objectKey: string
  uploadUrl: string
  httpMethod: string
  headers: Record<string, string>
  expiresAt: string
}

export interface UserSessionVO {
  id: string
  deviceName: string
  createdAt: string
  lastUsedAt: string
  current: boolean
}

export interface NotificationVO {
  id: string
  type: string
  title: string
  content: string
  resourceType: string
  resourceId: string
  read: boolean
  occurredAt: string
}

export interface CreditRecordVO {
  id: string
  changeValue: number
  reason: string
  createdAt: string
}

export interface CreditVO {
  userId: string
  creditScore: number
  records: CreditRecordVO[]
}

export interface InventoryDiagnosticVO {
  orderId: string
  orderNo: string
  listingId: string
  listingTitle: string
  sourceType: string
  orderStatus: string
  reservationStatus: string | null
  deadline: string
  overdueSeconds: number
}

export interface AdminReportSummaryVO {
  completedGmv: string
  escrowBalance: string
  totalOrders: number
  pendingPaymentOrders: number
  disputedOrders: number
  activeAuctions: number
  activeSeckills: number
  activeShops: number
  unsoldAuctions: number
  downgradeRate: string
}

export interface ChannelInventoryVO {
  channel: 'AUCTION' | 'SECKILL' | 'SHOP'
  activeCount: number
  soldCount: number
  unsoldCount: number
  scheduledCount: number
}

export interface DailyTradeVO {
  day: string
  completedOrders: number
  completedAmount: string
}

export interface AdminReportsVO {
  summary: AdminReportSummaryVO
  channels: ChannelInventoryVO[]
  trend: DailyTradeVO[]
}

export interface WalletReconciliationVO {
  accountId: string
  accountCode: string
  ownerType: string
  ownerId: string | null
  availableBalance: string
  frozenBalance: string
  ledgerAvailableBalance: string
  ledgerFrozenBalance: string
  difference: string
  consistent: boolean
}

export interface AiUsageByModelVO {
  modelName: string
  calls: number
  promptTokens: number
  completionTokens: number
  averageLatencyMs: number | null
  estimatedCost: string | null
}

export interface AiUsageSummaryVO {
  totals: {
    totalCalls: number
    succeededCalls: number
    failedCalls: number
    promptTokens: number
    completionTokens: number
    averageLatencyMs: number | null
  }
  costConfigured: boolean
  estimatedCost: string | null
  models: AiUsageByModelVO[]
}

export interface KnowledgeIndexVO {
  ready: boolean
  activeVersion: string | null
  lastError: string | null
}

export interface AssistantConversation {
  id: string
  title: string
  lastMessageAt: string
}

export interface AssistantMessage {
  role: 'user' | 'assistant'
  content: string
}

export interface AssistantEvent {
  type: 'token' | 'done' | 'error'
  text: string
  citations?: string[]
}
