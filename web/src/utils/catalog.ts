import type { AuctionVO, ListingVO } from '@/types/api'
import type { ArtworkKind, ChannelType, MarketLot } from '@/types/catalog'
import { assetClass, categoryText, conditionText } from '@/utils/format'

export const channelLabels: Record<ChannelType, string> = {
  auction: '拍卖',
  seckill: '秒杀',
  shop: '商城',
}

const artworkByCategory: Record<string, ArtworkKind> = {
  camera: 'camera',
  game: 'console',
  instrument: 'guitar',
  craft: 'mug',
  collectible: 'figurine',
  watch: 'watch',
  audio: 'speaker',
  cards: 'cards',
}

function artworkFor(categoryCode: string, index: number): ArtworkKind {
  const normalized = categoryCode.toLowerCase()
  if (normalized.includes('camera')) return 'camera'
  if (normalized.includes('game')) return 'console'
  if (normalized.includes('instrument') || normalized.includes('guitar')) return 'guitar'
  if (normalized.includes('craft') || normalized.includes('ceramic') || normalized.includes('mug')) return 'mug'
  if (normalized.includes('card')) return 'cards'
  if (normalized.includes('watch')) return 'watch'
  if (normalized.includes('figure') || normalized.includes('collect')) return 'figurine'
  if (normalized.includes('audio') || normalized.includes('speaker')) return 'speaker'
  const fallback = Object.entries(artworkByCategory).find(([key]) => normalized.includes(key))
  return fallback?.[1] || (['camera', 'console', 'mug', 'guitar'] as const)[index % 4] || 'camera'
}

export function channelForStatus(status: string, fallback: ChannelType = 'auction'): ChannelType {
  if (status === 'SECKILL') return 'seckill'
  if (status === 'SHOP') return 'shop'
  if (status === 'AUCTION_LIVE' || status === 'SCHEDULED' || status === 'WAITING_SELLER_DECISION') {
    return 'auction'
  }
  return fallback
}

export function listingToMarketLot(
  listing: ListingVO,
  auction?: AuctionVO | null,
  index = 0,
  channel = channelForStatus(listing.status),
): MarketLot {
  const price = auction?.currentPrice
    || (channel === 'seckill' ? listing.seckillPrice : channel === 'shop' ? listing.shopPrice : null)
    || listing.startPrice

  return {
    id: listing.id,
    auctionId: auction?.id,
    lotNo: `#${listing.id}`,
    channel,
    title: listing.title,
    subtitle: listing.defects || listing.description,
    description: listing.description,
    price,
    startPrice: listing.startPrice,
    seckillPrice: listing.seckillPrice,
    shopPrice: listing.shopPrice,
    deposit: null,
    bidCount: null,
    endsAt: auction?.endTime || null,
    status: listing.status,
    artwork: artworkFor(listing.categoryCode, index),
    tone: assetClass(index),
    coverUrl: listing.coverUrl ?? null,
    sellerName: null,
    autoDowngrade: listing.autoDowngrade,
    category: categoryText(listing.categoryCode),
    conditionCode: listing.conditionCode,
    condition: conditionText(listing.conditionCode),
    defects: listing.defects || '',
  }
}
