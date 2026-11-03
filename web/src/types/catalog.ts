export type ChannelType = 'auction' | 'seckill' | 'shop'

export type ArtworkKind =
  | 'camera'
  | 'console'
  | 'mug'
  | 'guitar'
  | 'cards'
  | 'watch'
  | 'figurine'
  | 'speaker'
  | 'vase'
  | 'film'

export interface MarketLot {
  id: string
  auctionId?: string
  lotNo: string
  channel: ChannelType
  title: string
  subtitle: string
  description: string
  price: string
  startPrice: string
  seckillPrice: string | null
  shopPrice: string | null
  deposit: string | null
  bidCount: number | null
  endsAt: string | null
  status: string
  artwork: ArtworkKind
  tone: string
  coverUrl?: string | null
  sellerName: string | null
  autoDowngrade: boolean
  category: string
  conditionCode: string
  condition: string
  defects: string
}
