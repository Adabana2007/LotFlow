package com.lotflow.entity.enums;

/** 业务状态集中定义，数据库只保存稳定字符串。 */
public final class DomainEnums {

    private DomainEnums() {
    }

    public enum UserStatus { ACTIVE, RESTRICTED, DISABLED }

    public enum RoleCode { USER, ADMIN }

    public enum ListingStatus {
        DRAFT, PENDING_REVIEW, APPROVED, CHANGES_REQUIRED, REJECTED,
        SCHEDULED, AUCTION_LIVE, SOLD_PENDING_PAYMENT,
        COMPLETED, DISPUTED, WAITING_SELLER_DECISION,
        SECKILL, SHOP, RETURNED, CANCELLED
    }

    public enum ReviewDecisionValue { APPROVED, REJECTED, CHANGES_REQUIRED }

    public enum ReviewDecisionSource { HUMAN, SYSTEM_POLICY }

    public enum ReviewStatus {
        PENDING_AI, MANUAL_PENDING, APPROVED_BY_HUMAN,
        APPROVED_BY_SYSTEM_POLICY, CHANGES_REQUIRED, REJECTED
    }

    public enum RiskLevel { LOW, MEDIUM, HIGH }

    public enum AuctionStatus { SCHEDULED, LIVE, ENDED, UNSOLD, CANCELLED }

    public enum BidStatus { VALID, CANCELLED }

    public enum DepositType { BUYER, SELLER }

    public enum DepositStatus { HELD, RELEASED, CONSUMED, COMPENSATED }

    public enum OrderStatus {
        PENDING_PAYMENT, PAID, SHIPPED, RECEIVED, DISPUTED,
        COMPLETED, BUYER_DEFAULTED, CANCELLED, SECOND_CHANCE
    }

    public enum SaleChannelType { AUCTION, SECKILL, SHOP }

    public enum SaleChannelStatus { SCHEDULED, ACTIVE, SOLD, UNSOLD, CANCELLED }

    public enum ChannelDecisionStatus { WAITING, DECIDED, EXPIRED }

    public enum ChannelDecisionValue { REAUCTION, SECKILL, RETURN_INVENTORY }

    public enum ReservationStatus { RESERVED, ORDERED, EXPIRED }

    public enum ConversationStatus { OPEN, CLOSED }

    public enum AiTaskStatus { QUEUED, RUNNING, SUCCEEDED, FAILED, FALLBACK }

    public enum VectorIndexStatus { PENDING, INDEXED, FAILED }

    public enum DisputeStatus { OPEN, RESOLVED }

    public enum DisputeResolutionValue { BUYER_WINS, SELLER_WINS, BUYER_WITHDRAWS }

    public enum NotificationType { AUCTION, ORDER, MESSAGE }
}
