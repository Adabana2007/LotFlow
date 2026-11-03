import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

import PublicLayout from '@/layouts/PublicLayout.vue'
import UserLayout from '@/layouts/UserLayout.vue'
import AdminLayout from '@/layouts/AdminLayout.vue'

import HomeView from '@/views/public/HomeView.vue'
import AuctionsView from '@/views/public/AuctionsView.vue'
import MarketChannelView from '@/views/public/MarketChannelView.vue'
import SearchView from '@/views/public/SearchView.vue'
import ListingDetailView from '@/views/public/ListingDetailView.vue'
import AuthView from '@/views/AuthView.vue'

import WorkspaceView from '@/views/user/WorkspaceView.vue'
import MyListingsView from '@/views/user/MyListingsView.vue'
import ListingManageView from '@/views/user/ListingManageView.vue'
import UnsoldDecisionView from '@/views/user/UnsoldDecisionView.vue'
import PublishView from '@/views/user/PublishView.vue'
import MyBidsView from '@/views/user/MyBidsView.vue'
import OrdersView from '@/views/user/OrdersView.vue'
import OrderDetailView from '@/views/user/OrderDetailView.vue'
import DisputeDetailView from '@/views/user/DisputeDetailView.vue'
import WalletView from '@/views/user/WalletView.vue'
import MessagesView from '@/views/user/MessagesView.vue'
import NotificationsView from '@/views/user/NotificationsView.vue'
import AssistantView from '@/views/user/AssistantView.vue'
import SettingsView from '@/views/user/SettingsView.vue'

import AdminOverviewView from '@/views/admin/AdminOverviewView.vue'
import AdminReviewsView from '@/views/admin/AdminReviewsView.vue'
import AdminReviewDetailView from '@/views/admin/AdminReviewDetailView.vue'
import AdminRiskView from '@/views/admin/AdminRiskView.vue'
import AdminDisputesView from '@/views/admin/AdminDisputesView.vue'
import AdminDisputeDetailView from '@/views/admin/AdminDisputeDetailView.vue'
import AdminOrdersView from '@/views/admin/AdminOrdersView.vue'
import AdminFinanceView from '@/views/admin/AdminFinanceView.vue'
import AdminUsersView from '@/views/admin/AdminUsersView.vue'
import AdminAiLogsView from '@/views/admin/AdminAiLogsView.vue'
import AdminInventoryView from '@/views/admin/AdminInventoryView.vue'
import AdminKnowledgeView from '@/views/admin/AdminKnowledgeView.vue'
import NotFoundView from '@/views/NotFoundView.vue'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/',
      component: PublicLayout,
      children: [
        { path: '', name: 'home', component: HomeView, meta: { title: '首页', breadcrumb: ['首页'] } },
        { path: 'auctions', name: 'auctions', component: AuctionsView, meta: { title: '拍场', breadcrumb: ['拍场'], breadcrumbNote: '拍卖中的普通旧物会在这里继续流转' } },
        { path: 'seckill', name: 'seckill', component: MarketChannelView, props: { channel: 'seckill' }, meta: { title: '秒杀', breadcrumb: ['秒杀'] } },
        { path: 'shop', name: 'shop', component: MarketChannelView, props: { channel: 'shop' }, meta: { title: '一口价商城', breadcrumb: ['一口价商城'] } },
        { path: 'search', name: 'search', component: SearchView, meta: { title: 'AI 找物', breadcrumb: ['AI 找物'] } },
        { path: 'listings/:id', name: 'listing-detail', component: ListingDetailView, meta: { title: '商品详情', breadcrumb: ['拍场', '商品详情'] } },
      ],
    },
    {
      path: '/',
      component: UserLayout,
      meta: { requiresUser: true },
      children: [
        { path: 'workspace', name: 'workspace', component: WorkspaceView, meta: { title: '用户工作台', breadcrumb: ['用户工作台'] } },
        { path: 'workspace/listings', name: 'my-listings', component: MyListingsView, meta: { title: '我的拍品', breadcrumb: ['用户工作台', '我的拍品'] } },
        { path: 'workspace/listings/:id', name: 'listing-manage', component: ListingManageView, meta: { title: '拍品管理', breadcrumb: ['用户工作台', '拍品管理'] } },
        { path: 'workspace/listings/:id/unsold', name: 'unsold-decision', component: UnsoldDecisionView, meta: { title: '流拍处置', breadcrumb: ['用户工作台', '流拍处置'] } },
        { path: 'workspace/publish', name: 'publish', component: PublishView, meta: { title: 'AI 发布工作台', breadcrumb: ['用户工作台', 'AI 发布工作台'] } },
        { path: 'workspace/bids', name: 'my-bids', component: MyBidsView, meta: { title: '我的出价', breadcrumb: ['用户工作台', '我的出价'] } },
        { path: 'orders', name: 'orders', component: OrdersView, meta: { title: '订单', breadcrumb: ['用户工作台', '订单'] } },
        { path: 'orders/:id', name: 'order-detail', component: OrderDetailView, meta: { title: '订单详情', breadcrumb: ['用户工作台', '订单详情'] } },
        { path: 'orders/:id/disputes/:disputeId', name: 'user-dispute-detail', component: DisputeDetailView, meta: { title: '争议详情', breadcrumb: ['用户工作台', '争议详情'] } },
        { path: 'wallet', name: 'wallet', component: WalletView, meta: { title: '钱包', breadcrumb: ['用户工作台', '钱包'] } },
        { path: 'messages', name: 'messages', component: MessagesView, meta: { title: '消息', breadcrumb: ['用户工作台', '消息'] } },
        { path: 'notifications', name: 'notifications', component: NotificationsView, meta: { title: '通知', breadcrumb: ['用户工作台', '通知'] } },
        { path: 'assistant', name: 'assistant', component: AssistantView, meta: { title: 'AI 平台助手', breadcrumb: ['用户工作台', 'AI 平台助手'] } },
        { path: 'settings', name: 'settings', component: SettingsView, meta: { title: '账号设置', breadcrumb: ['用户工作台', '账号设置'] } },
      ],
    },
    { path: '/auth', name: 'auth', component: AuthView, meta: { title: '登录注册' } },
    {
      path: '/admin',
      component: AdminLayout,
      meta: { title: '运营后台', requiresAdmin: true },
      children: [
        { path: '', name: 'admin-overview', component: AdminOverviewView, meta: { title: '运营总览' } },
        { path: 'reviews', name: 'admin-reviews', component: AdminReviewsView, meta: { title: '商品审核' } },
        { path: 'reviews/:taskId', name: 'admin-review-detail', component: AdminReviewDetailView, meta: { title: '商品审核详情' } },
        { path: 'risk', name: 'admin-risk', component: AdminRiskView, meta: { title: '聊天风险' } },
        { path: 'disputes', name: 'admin-disputes', component: AdminDisputesView, meta: { title: '交易争议' } },
        { path: 'disputes/:disputeId', name: 'admin-dispute-detail', component: AdminDisputeDetailView, meta: { title: '争议裁决详情' } },
        { path: 'orders', name: 'admin-orders', component: AdminOrdersView, meta: { title: '订单运营' } },
        { path: 'finance', name: 'admin-finance', component: AdminFinanceView, meta: { title: '资金审计' } },
        { path: 'users', name: 'admin-users', component: AdminUsersView, meta: { title: '用户与信用' } },
        { path: 'inventory', name: 'admin-inventory', component: AdminInventoryView, meta: { title: '库存释放诊断' } },
        { path: 'ai-logs', name: 'admin-ai-logs', component: AdminAiLogsView, meta: { title: 'AI 使用审计' } },
        { path: 'knowledge', name: 'admin-knowledge', component: AdminKnowledgeView, meta: { title: '知识库索引' } },
        { path: 'assistant', name: 'admin-assistant', component: AssistantView, meta: { title: 'AI 治理助手' } },
      ],
    },
    { path: '/:pathMatch(.*)*', name: 'not-found', component: NotFoundView, meta: { title: '页面不存在' } },
  ],
  scrollBehavior() {
    return { top: 0 }
  },
})

router.beforeEach(async (to) => {
  const auth = useAuthStore()
  await auth.restore()
  const requiresUser = to.matched.some((record) => record.meta.requiresUser)
  const requiresAdmin = to.matched.some((record) => record.meta.requiresAdmin)

  if ((requiresUser || requiresAdmin) && !auth.isAuthenticated) {
    return { name: 'auth', query: { redirect: to.fullPath } }
  }
  if (requiresAdmin && auth.isAuthenticated && !auth.isAdmin) return { name: 'home' }
  // 管理员没有买家和卖家身份，用户中心对他只会是一屏空数据，统一送回运营后台。
  if (requiresUser && auth.isAdmin) return { name: 'admin-overview' }
  if (to.name === 'auth' && auth.isAuthenticated) {
    return { name: auth.isAdmin ? 'admin-overview' : 'workspace' }
  }
  return true
})

export default router
