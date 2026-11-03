-- LotFlow 全量建库脚本。
-- 不使用 Flyway/Liquibase：Compose 把它挂到 /docker-entrypoint-initdb.d/，只在空数据目录首次启动时执行。
-- 改表结构必须同步改本文件并重建空库；已存在的库没有增量迁移路径，这是有意接受的取舍。

begin;

-- ========== 用户 ==========

create table app_user (
    id bigint generated always as identity,
    username varchar(64) not null,
    phone varchar(32) not null,
    password_hash varchar(100) not null,
    display_name varchar(80) not null,
    role_code varchar(16) not null default 'USER',
    status varchar(16) not null default 'ACTIVE',
    chat_enabled boolean not null default true,
    completed_review_count integer not null default 0,
    credit_score integer not null default 100,
    avatar_object_key varchar(512),
    notify_auction_ending boolean not null default true,
    notify_order_payment boolean not null default true,
    notify_messages boolean not null default true,
    gmt_create timestamptz not null default current_timestamp,
    gmt_modified timestamptz not null default current_timestamp,
    is_deleted boolean not null default false,
    constraint pk_app_user primary key (id),
    constraint uk_app_user_username unique (username),
    constraint uk_app_user_phone unique (phone),
    constraint ck_app_user_role check (role_code in ('USER', 'ADMIN')),
    constraint ck_app_user_status check (status in ('ACTIVE', 'RESTRICTED', 'DISABLED')),
    constraint ck_app_user_review_count check (completed_review_count >= 0),
    constraint ck_app_user_credit check (credit_score between 0 and 120)
);

comment on table app_user is '注册用户及其唯一业务角色';
comment on column app_user.password_hash is 'BCrypt 摘要，明文密码不入库';
comment on column app_user.role_code is '单角色：USER 交易方 / ADMIN 运营';
comment on column app_user.status is 'ACTIVE 可登录；RESTRICTED 和 DISABLED 停用存量令牌';
comment on column app_user.chat_enabled is '账号级聊天默认值，商品未单独设置时继承';
comment on column app_user.completed_review_count is '已通过审核的商品数，前三次审核强制人工';
comment on column app_user.credit_score is '信用分 0-120，只能由带审计的服务端逻辑调整';
comment on column app_user.avatar_object_key is '头像对象的相对存储键，不对外暴露';
comment on column app_user.notify_auction_ending is '拍卖结束提醒开关';
comment on column app_user.notify_order_payment is '订单付款提醒开关';
comment on column app_user.notify_messages is '新消息提醒开关';

-- ========== 商品与图片 ==========

create table listing (
    id bigint generated always as identity,
    seller_id bigint not null,
    title varchar(160) not null,
    description varchar(5000) not null,
    category_code varchar(64) not null,
    condition_code varchar(32) not null,
    defects varchar(2000),
    start_price numeric(18, 2) not null,
    duration_hours integer not null,
    scheduled_start timestamptz not null,
    is_auto_downgrade boolean not null default false,
    seckill_price numeric(18, 2),
    shop_price numeric(18, 2),
    chat_enabled boolean,
    status varchar(40) not null default 'DRAFT',
    rejection_reason varchar(2000),
    images_json jsonb not null default '[]'::jsonb,
    review_status varchar(40),
    review_generation bigint not null default 0,
    risk_level varchar(16),
    review_evidence varchar(4000),
    manual_required boolean not null default true,
    review_decision varchar(32),
    review_decision_source varchar(32),
    reviewed_by bigint,
    reviewed_at timestamptz,
    vector_status varchar(16) not null default 'PENDING',
    vector_indexed_at timestamptz,
    vector_retry_count integer not null default 0,
    vector_last_error varchar(1000),
    gmt_create timestamptz not null default current_timestamp,
    gmt_modified timestamptz not null default current_timestamp,
    is_deleted boolean not null default false,
    version bigint not null default 0,
    constraint pk_listing primary key (id),
    constraint ck_listing_title check (length(btrim(title)) > 0),
    constraint ck_listing_category check (category_code in (
        'CAMERA', 'CARD', 'FIGURE', 'INSTRUMENT', 'GAME_CONSOLE',
        'HANDICRAFT', 'COLLECTIBLE', 'FASHION', 'HOME', 'OTHER')),
    constraint ck_listing_condition check (condition_code in (
        'NEW', 'LIKE_NEW', 'GOOD', 'FAIR', 'POOR', 'FOR_PARTS')),
    constraint ck_listing_duration check (duration_hours between 1 and 72),
    -- 价格链是降级授权边界：商城价 <= 秒杀价 <= 起拍价，任一层缺失则由上一层兜底。
    constraint ck_listing_price_chain check (
        start_price > 0
        and (seckill_price is null or (seckill_price > 0 and seckill_price <= start_price))
        and (shop_price is null or (shop_price > 0
             and shop_price <= coalesce(seckill_price, start_price)))),
    constraint ck_listing_images_array check (jsonb_typeof(images_json) = 'array'),
    constraint ck_listing_status check (status in (
        'DRAFT', 'PENDING_REVIEW', 'APPROVED', 'CHANGES_REQUIRED', 'REJECTED',
        'SCHEDULED', 'AUCTION_LIVE', 'SOLD_PENDING_PAYMENT',
        'COMPLETED', 'DISPUTED', 'WAITING_SELLER_DECISION',
        'SECKILL', 'SHOP', 'RETURNED', 'CANCELLED')),
    constraint ck_listing_review_status check (review_status is null or review_status in (
        'PENDING_AI', 'MANUAL_PENDING', 'APPROVED_BY_HUMAN',
        'APPROVED_BY_SYSTEM_POLICY', 'CHANGES_REQUIRED', 'REJECTED')),
    constraint ck_listing_review_generation check (review_generation >= 0),
    constraint ck_listing_risk check (risk_level is null or risk_level in ('LOW', 'MEDIUM', 'HIGH')),
    constraint ck_listing_review_decision check (
        review_decision is null or review_decision in ('APPROVED', 'REJECTED', 'CHANGES_REQUIRED')),
    constraint ck_listing_review_decision_source check (
        review_decision_source is null or review_decision_source in ('HUMAN', 'SYSTEM_POLICY')),
    constraint ck_listing_vector_status check (vector_status in ('PENDING', 'INDEXED', 'FAILED')),
    constraint ck_listing_vector_retry check (vector_retry_count >= 0)
);

create index idx_listing_seller_status on listing (seller_id, status, id desc)
    where is_deleted = false;
-- 公开列表只覆盖四种在售状态，避免全表状态扫描。
create index idx_listing_public_status on listing (status, id desc)
    where is_deleted = false and status in ('SCHEDULED', 'AUCTION_LIVE', 'SECKILL', 'SHOP');
create index idx_listing_review_queue on listing (id desc)
    where is_deleted = false and status = 'PENDING_REVIEW';
create index idx_listing_vector_retry on listing (id)
    where is_deleted = false and vector_status = 'FAILED' and vector_retry_count < 10;

-- 图片元数据存放在 listing.images_json，图片 ID 由该序列统一分配。
create sequence listing_image_id_seq start with 1 increment by 1;

comment on table listing is '拍卖/秒杀/商城商品，含审核快照与向量索引状态';
comment on column listing.start_price is '起拍价，同时是价格链上限';
comment on column listing.duration_hours is '拍卖时长 1-72 小时';
comment on column listing.is_auto_downgrade is '卖家是否授权流拍后自动降级为秒杀和商城';
comment on column listing.seckill_price is '秒杀价，不得超过起拍价';
comment on column listing.shop_price is '商城价，卖家授权的最低售价';
comment on column listing.chat_enabled is '商品级聊天开关，null 表示继承卖家默认值';
comment on column listing.status is '商品生命周期，取值与 ck_listing_status 一致';
comment on column listing.images_json is '有序图片元数据快照（对象键与哈希），不单独建表';
comment on column listing.review_generation is '提审批次号，用于丢弃过期的 AI 审核结果';
comment on column listing.risk_level is 'AI 风险建议，不是最终审核结论';
comment on column listing.manual_required is '平台策略是否要求人工终审';
comment on column listing.vector_status is '向量索引状态，失败时由补偿任务重试';
comment on column listing.version is '乐观锁版本，草稿编辑用 If-Match 校验';

-- ========== 拍卖与出价 ==========

create table auction (
    id bigint generated always as identity,
    listing_id bigint not null,
    seller_id bigint not null,
    attempt_no integer not null,
    start_price numeric(18, 2) not null,
    current_price numeric(18, 2) not null,
    status varchar(20) not null default 'SCHEDULED',
    start_time timestamptz not null,
    end_time timestamptz not null,
    extension_seconds integer not null default 0,
    highest_bidder_id bigint,
    highest_bid_id bigint,
    gmt_create timestamptz not null default current_timestamp,
    gmt_modified timestamptz not null default current_timestamp,
    is_deleted boolean not null default false,
    version bigint not null default 0,
    constraint pk_auction primary key (id),
    constraint uk_auction_listing_attempt unique (listing_id, attempt_no),
    constraint ck_auction_attempt check (attempt_no > 0),
    constraint ck_auction_price check (start_price > 0 and current_price >= start_price),
    constraint ck_auction_time check (end_time > start_time),
    constraint ck_auction_status check (status in ('SCHEDULED', 'LIVE', 'ENDED', 'UNSOLD', 'CANCELLED')),
    constraint ck_auction_extension check (extension_seconds >= 0)
);

create index idx_auction_due on auction (status, start_time, end_time)
    where is_deleted = false and status in ('SCHEDULED', 'LIVE');
create index idx_auction_listing on auction (listing_id, id desc)
    where is_deleted = false;

comment on table auction is '单商品单场拍卖，出价与关单的并发边界';
comment on column auction.attempt_no is '同一商品的第几场拍卖，流拍重拍时递增';
comment on column auction.current_price is '当前最高有效出价，无出价时等于起拍价';
comment on column auction.end_time is '结束时间，含反狙击顺延';
comment on column auction.extension_seconds is '累计顺延秒数，上限由平台规则控制';
comment on column auction.highest_bidder_id is '当前最高出价人';
comment on column auction.version is '乐观锁版本，出价在行锁内更新';

create table bid (
    id bigint generated always as identity,
    auction_id bigint not null,
    bidder_id bigint not null,
    amount numeric(18, 2) not null,
    deposit_required numeric(18, 2) not null,
    idempotency_key varchar(128) not null,
    status varchar(16) not null default 'VALID',
    gmt_create timestamptz not null default current_timestamp,
    gmt_modified timestamptz not null default current_timestamp,
    is_deleted boolean not null default false,
    version bigint not null default 0,
    constraint pk_bid primary key (id),
    constraint uk_bid_idempotency unique (auction_id, bidder_id, idempotency_key),
    constraint ck_bid_amount check (amount > 0),
    constraint ck_bid_deposit check (deposit_required >= 0),
    constraint ck_bid_status check (status in ('VALID', 'CANCELLED'))
);

create index idx_bid_auction_amount on bid (auction_id, amount desc, id desc)
    where is_deleted = false and status = 'VALID';

comment on table bid is '出价事实，写入后不再修改金额';
comment on column bid.deposit_required is '按该出价金额计算的买家保证金';
comment on column bid.idempotency_key is '客户端命令键，唯一约束保证重复请求只产生一条出价';

-- ========== 钱包与保证金 ==========

create table wallet_account (
    id bigint generated always as identity,
    account_code varchar(80) not null,
    owner_type varchar(16) not null,
    owner_id bigint,
    available_balance numeric(18, 2) not null default 0,
    frozen_balance numeric(18, 2) not null default 0,
    gmt_create timestamptz not null default current_timestamp,
    gmt_modified timestamptz not null default current_timestamp,
    is_deleted boolean not null default false,
    version bigint not null default 0,
    constraint pk_wallet_account primary key (id),
    constraint uk_wallet_account_code unique (account_code),
    constraint ck_wallet_owner_type check (owner_type in ('USER', 'PLATFORM')),
    constraint ck_wallet_owner check (
        (owner_type = 'USER' and owner_id is not null)
        or (owner_type = 'PLATFORM' and owner_id is null)),
    constraint ck_wallet_balance check (available_balance >= 0 and frozen_balance >= 0)
);

-- 三个平台账户必须先存在：担保收付和充值来源都按 account_code 定位。
insert into wallet_account (account_code, owner_type, owner_id, available_balance, frozen_balance)
values ('PLATFORM_ESCROW', 'PLATFORM', null, 0, 0),
       ('PLATFORM_REVENUE', 'PLATFORM', null, 0, 0),
       ('PLATFORM_RECHARGE', 'PLATFORM', null, 0, 0);

create unique index uk_wallet_user_owner on wallet_account (owner_id)
    where owner_type = 'USER' and is_deleted = false;

comment on table wallet_account is '用户与平台钱包余额';
comment on column wallet_account.account_code is '账户稳定编码，资金命令按它定位并确定加锁顺序';
comment on column wallet_account.owner_type is 'USER 或 PLATFORM';
comment on column wallet_account.owner_id is '用户账户为用户 ID，平台账户为 null';
comment on column wallet_account.available_balance is '可用余额，只能通过带条件的原子更新改变';
comment on column wallet_account.frozen_balance is '冻结余额，竞拍保证金和担保资金占用';

create table wallet_transaction (
    id bigint generated always as identity,
    account_id bigint not null,
    business_key varchar(160) not null,
    transaction_type varchar(64) not null,
    available_change numeric(18, 2) not null,
    frozen_change numeric(18, 2) not null,
    available_after numeric(18, 2) not null,
    frozen_after numeric(18, 2) not null,
    remark varchar(500),
    gmt_create timestamptz not null default current_timestamp,
    constraint pk_wallet_transaction primary key (id),
    constraint uk_wallet_transaction_business unique (account_id, business_key),
    constraint ck_wallet_transaction_after check (available_after >= 0 and frozen_after >= 0)
);

create index idx_wallet_transaction_account on wallet_transaction (account_id, id desc);

comment on table wallet_transaction is '不可变资金流水，每次余额变化对应一条';
comment on column wallet_transaction.business_key is '账户内唯一的业务键，重试时用于幂等';
comment on column wallet_transaction.transaction_type is '稳定操作码：FREEZE / UNFREEZE / ESCROW_* / COMPENSATION_*';
comment on column wallet_transaction.available_change is '带符号的可用余额变化';
comment on column wallet_transaction.frozen_change is '带符号的冻结余额变化';
comment on column wallet_transaction.available_after is '本次变更后的可用余额快照';
comment on column wallet_transaction.frozen_after is '本次变更后的冻结余额快照';
comment on column wallet_transaction.remark is '不含隐私的审计备注';

create table deposit_hold (
    id bigint generated always as identity,
    auction_id bigint not null,
    user_id bigint not null,
    deposit_type varchar(16) not null,
    amount numeric(18, 2) not null,
    status varchar(20) not null default 'HELD',
    business_key varchar(160) not null,
    gmt_create timestamptz not null default current_timestamp,
    gmt_modified timestamptz not null default current_timestamp,
    is_deleted boolean not null default false,
    version bigint not null default 0,
    constraint pk_deposit_hold primary key (id),
    constraint uk_deposit_hold_subject unique (auction_id, user_id, deposit_type),
    constraint uk_deposit_hold_business unique (business_key),
    constraint ck_deposit_type check (deposit_type in ('BUYER', 'SELLER')),
    constraint ck_deposit_amount check (amount > 0),
    constraint ck_deposit_status check (status in ('HELD', 'RELEASED', 'CONSUMED', 'COMPENSATED'))
);

comment on table deposit_hold is '单场拍卖的买卖双方保证金义务';
comment on column deposit_hold.deposit_type is 'BUYER 竞拍保证金 或 SELLER 履约担保';
comment on column deposit_hold.status is 'HELD 冻结中；RELEASED 解冻；CONSUMED 已转担保；COMPENSATED 已赔付';
comment on column deposit_hold.business_key is '对应钱包命令键，与流水唯一约束配合防重复冻结';

-- ========== 销售渠道 ==========

create table sale_channel (
    id bigint generated always as identity,
    listing_id bigint not null,
    channel varchar(16) not null,
    status varchar(20) not null,
    price numeric(18, 2) not null,
    starts_at timestamptz not null,
    ends_at timestamptz not null,
    order_id bigint,
    decision_status varchar(16),
    decision_value varchar(24),
    decision_expires_at timestamptz,
    gmt_create timestamptz not null default current_timestamp,
    gmt_modified timestamptz not null default current_timestamp,
    is_deleted boolean not null default false,
    version bigint not null default 0,
    constraint pk_sale_channel primary key (id),
    constraint ck_sale_channel_type check (channel in ('AUCTION', 'SECKILL', 'SHOP')),
    constraint ck_sale_channel_status check (status in ('SCHEDULED', 'ACTIVE', 'SOLD', 'UNSOLD', 'CANCELLED')),
    constraint ck_sale_channel_price check (price > 0),
    constraint ck_sale_channel_time check (ends_at > starts_at),
    constraint ck_sale_channel_decision_status check (
        decision_status is null or decision_status in ('WAITING', 'DECIDED', 'EXPIRED')),
    constraint ck_sale_channel_decision_value check (
        decision_value is null or decision_value in ('REAUCTION', 'SECKILL', 'RETURN_INVENTORY')),
    constraint ck_sale_channel_decision_window check (
        (decision_status is null and decision_expires_at is null)
        or (decision_status is not null and decision_expires_at is not null))
);

-- 同一商品只能有一条未结束渠道；SOLD 也占用该唯一性，直到订单结束或渠道释放。
create unique index uk_sale_channel_open_listing on sale_channel (listing_id)
    where is_deleted = false and status in ('SCHEDULED', 'ACTIVE', 'SOLD');
create index idx_sale_channel_due on sale_channel (ends_at, id)
    where is_deleted = false and status = 'ACTIVE';
create index idx_sale_channel_decision_due on sale_channel (decision_expires_at, id)
    where is_deleted = false and decision_status = 'WAITING';

comment on table sale_channel is '商品的唯一在售渠道，以及流拍后的卖家决定';
comment on column sale_channel.channel is 'AUCTION / SECKILL / SHOP';
comment on column sale_channel.status is 'SCHEDULED 待开售；ACTIVE 在售；SOLD 已成交；UNSOLD 未售出；CANCELLED 下架';
comment on column sale_channel.price is '渠道成交价快照';
comment on column sale_channel.order_id is '消耗该库存的订单';
comment on column sale_channel.decision_status is '流拍处置状态：WAITING / DECIDED / EXPIRED';
comment on column sale_channel.decision_value is '卖家选择：重新拍卖、转秒杀或退回库存';
comment on column sale_channel.decision_expires_at is '卖家决策截止时间，超时自动回库';

-- ========== 订单 ==========

create table order_record (
    id bigint generated always as identity,
    order_no varchar(40) not null,
    listing_id bigint not null,
    auction_id bigint,
    seller_id bigint not null,
    buyer_id bigint not null,
    source_type varchar(16) not null,
    amount numeric(18, 2) not null,
    deposit_applied numeric(18, 2) not null default 0,
    status varchar(32) not null,
    payment_deadline timestamptz,
    ship_deadline timestamptz,
    shipped_at timestamptz,
    receipt_deadline timestamptz,
    received_at timestamptz,
    dispute_deadline timestamptz,
    dispute_opened_at timestamptz,
    escrow_amount numeric(18, 2) not null default 0,
    offered_second_chance boolean not null default false,
    payment_key varchar(128),
    paid_at timestamptz,
    carrier varchar(80),
    tracking_no varchar(120),
    dispute_status varchar(16),
    dispute_reason varchar(2000),
    dispute_evidence varchar(3000),
    dispute_resolution varchar(2000),
    dispute_resolved_by bigint,
    dispute_resolved_at timestamptz,
    reservation_status varchar(16),
    reservation_expires_at timestamptz,
    reservation_key varchar(128),
    gmt_create timestamptz not null default current_timestamp,
    gmt_modified timestamptz not null default current_timestamp,
    is_deleted boolean not null default false,
    version bigint not null default 0,
    constraint pk_order_record primary key (id),
    constraint uk_order_record_no unique (order_no),
    constraint uk_order_record_payment_key unique (payment_key),
    constraint uk_order_record_reservation_key unique (reservation_key),
    constraint ck_order_record_source check (source_type in ('AUCTION', 'SECKILL', 'SHOP')),
    constraint ck_order_record_amount check (amount > 0 and deposit_applied >= 0 and escrow_amount >= 0),
    constraint ck_order_record_parties check (seller_id <> buyer_id),
    constraint ck_order_record_status check (status in (
        'PENDING_PAYMENT', 'PAID', 'SHIPPED', 'RECEIVED', 'DISPUTED',
        'COMPLETED', 'BUYER_DEFAULTED', 'CANCELLED', 'SECOND_CHANCE')),
    constraint ck_order_record_dispute_status check (
        dispute_status is null or dispute_status in ('OPEN', 'RESOLVED')),
    constraint ck_order_record_reservation_status check (
        reservation_status is null or reservation_status in ('RESERVED', 'ORDERED', 'EXPIRED')),
    -- 只有秒杀订单带预占字段，其他来源出现预占字段说明写错了下单分支。
    constraint ck_order_record_reservation check (
        (source_type = 'SECKILL' and reservation_status is not null
            and reservation_expires_at is not null and reservation_key is not null)
        or (source_type <> 'SECKILL' and reservation_status is null
            and reservation_expires_at is null and reservation_key is null))
);

-- 同一商品同时只能有一个进行中的订单，第二高顺延依赖该约束防止超卖。
create unique index uk_order_record_live_listing on order_record (listing_id)
    where is_deleted = false and status in (
        'PENDING_PAYMENT', 'SECOND_CHANCE', 'PAID', 'SHIPPED', 'RECEIVED', 'DISPUTED');
-- 到期扫描按状态分支各自判断截止时间；单列部分索引让每个分支只扫自己的到期行。
create index idx_order_due_payment on order_record (payment_deadline, id)
    where is_deleted = false and status in ('PENDING_PAYMENT', 'SECOND_CHANCE');
create index idx_order_due_ship on order_record (ship_deadline, id)
    where is_deleted = false and status = 'PAID';
create index idx_order_due_receipt on order_record (receipt_deadline, id)
    where is_deleted = false and status = 'SHIPPED';
create index idx_order_due_dispute on order_record (dispute_deadline, id)
    where is_deleted = false and status = 'RECEIVED';
create index idx_order_record_buyer on order_record (buyer_id, id desc) where is_deleted = false;
create index idx_order_record_seller on order_record (seller_id, id desc) where is_deleted = false;

comment on table order_record is '订单、支付、履约、争议与秒杀预占事实';
comment on column order_record.source_type is 'AUCTION / SECKILL / SHOP';
comment on column order_record.deposit_applied is '抵扣或预占本单的买家保证金';
comment on column order_record.escrow_amount is '进入平台担保账户的货款';
comment on column order_record.payment_deadline is '买家付款截止时间，超时按来源走违约或释放库存';
comment on column order_record.ship_deadline is '卖家发货截止时间，超时全额退款并扣信用';
comment on column order_record.receipt_deadline is '自动收货时间';
comment on column order_record.dispute_deadline is '确认收货后的争议窗口，结束才结算担保货款';
comment on column order_record.dispute_opened_at is '本次争议发起时间';
comment on column order_record.offered_second_chance is '卖家是否已使用一次顺延资格，防止重复发起';
comment on column order_record.payment_key is '付款命令的幂等键';
comment on column order_record.reservation_status is '秒杀库存预占状态：RESERVED / ORDERED / EXPIRED';
comment on column order_record.reservation_expires_at is '秒杀预占 15 分钟后的释放时间';
comment on column order_record.reservation_key is 'Redis 秒杀预占令牌，与唯一约束配合防重复下单';

-- ========== 会话与消息 ==========

create table conversation (
    id bigint generated always as identity,
    listing_id bigint not null,
    buyer_id bigint not null,
    seller_id bigint not null,
    status varchar(16) not null default 'OPEN',
    last_sequence bigint not null default 0,
    buyer_last_read_sequence bigint not null default 0,
    seller_last_read_sequence bigint not null default 0,
    gmt_create timestamptz not null default current_timestamp,
    gmt_modified timestamptz not null default current_timestamp,
    is_deleted boolean not null default false,
    version bigint not null default 0,
    constraint pk_conversation primary key (id),
    constraint uk_conversation_buyer_listing unique (buyer_id, listing_id),
    constraint ck_conversation_parties check (buyer_id <> seller_id),
    constraint ck_conversation_status check (status in ('OPEN', 'CLOSED')),
    constraint ck_conversation_sequence check (
        last_sequence >= 0 and buyer_last_read_sequence between 0 and last_sequence
        and seller_last_read_sequence between 0 and last_sequence)
);

create index idx_conversation_seller on conversation (seller_id, id desc) where is_deleted = false;

comment on table conversation is '买卖双方会话及单调递增的已读游标';
comment on column conversation.last_sequence is '已分配的最大消息序号，在会话行锁内递增';
comment on column conversation.buyer_last_read_sequence is '买家已读到的最大序号';
comment on column conversation.seller_last_read_sequence is '卖家已读到的最大序号';

create table message (
    id bigint generated always as identity,
    conversation_id bigint not null,
    sender_id bigint not null,
    sequence_no bigint not null,
    content varchar(4000) not null,
    risk_level varchar(16),
    risk_categories varchar(500),
    risk_evidence varchar(2000),
    requires_human_review boolean not null default false,
    gmt_create timestamptz not null default current_timestamp,
    gmt_modified timestamptz not null default current_timestamp,
    is_deleted boolean not null default false,
    version bigint not null default 0,
    constraint pk_message primary key (id),
    constraint uk_message_sequence unique (conversation_id, sequence_no),
    constraint ck_message_sequence check (sequence_no > 0),
    constraint ck_message_content check (length(btrim(content)) > 0),
    constraint ck_message_risk check (risk_level is null or risk_level in ('LOW', 'MEDIUM', 'HIGH'))
);

create index idx_message_conversation_sequence on message (conversation_id, sequence_no);
create index idx_message_risk_review on message (id desc)
    where is_deleted = false and risk_level in ('MEDIUM', 'HIGH');

comment on table message is '聊天消息及 AI 风险建议';
comment on column message.sequence_no is '会话内单调序号，唯一约束保证不重号';
comment on column message.risk_level is 'AI 建议等级 LOW / MEDIUM / HIGH，人工可推翻';
comment on column message.requires_human_review is 'AI 建议是否需要人工跟进';

-- ========== AI 任务与审核 ==========

create table ai_task (
    id bigint generated always as identity,
    user_id bigint,
    listing_id bigint,
    review_generation bigint,
    task_type varchar(40) not null,
    status varchar(16) not null default 'QUEUED',
    input_summary varchar(8000),
    output_summary varchar(8000),
    model_name varchar(100),
    prompt_version varchar(32),
    prompt_tokens integer,
    completion_tokens integer,
    latency_ms bigint,
    trace_id varchar(64),
    error_code varchar(100),
    gmt_create timestamptz not null default current_timestamp,
    gmt_modified timestamptz not null default current_timestamp,
    is_deleted boolean not null default false,
    version bigint not null default 0,
    constraint pk_ai_task primary key (id),
    constraint ck_ai_task_status check (status in ('QUEUED', 'RUNNING', 'SUCCEEDED', 'FAILED', 'FALLBACK')),
    constraint ck_ai_task_tokens check (
        (prompt_tokens is null or prompt_tokens >= 0)
        and (completion_tokens is null or completion_tokens >= 0)
        and (latency_ms is null or latency_ms >= 0))
);

create index idx_ai_task_user on ai_task (user_id, id desc) where is_deleted = false;
create index idx_ai_task_listing on ai_task (listing_id, review_generation) where is_deleted = false;

comment on table ai_task is 'AI 任务队列与可审计的模型调用指标';
comment on column ai_task.review_generation is '提审批次，用于丢弃上一轮的过期审核结果';
comment on column ai_task.status is 'QUEUED / RUNNING / SUCCEEDED / FAILED / FALLBACK';
comment on column ai_task.input_summary is '截断后的非敏感输入摘要';
comment on column ai_task.output_summary is '截断后的结构化输出摘要';
comment on column ai_task.error_code is '稳定失败码，不含密钥和用户隐私';

create table audit_log (
    id bigint generated always as identity,
    actor_id bigint,
    subject_user_id bigint,
    action varchar(64) not null,
    resource_type varchar(32) not null,
    resource_id bigint,
    before_state varchar(4000),
    after_state varchar(4000),
    change_value integer,
    reason varchar(2000),
    gmt_create timestamptz not null default current_timestamp,
    gmt_modified timestamptz not null default current_timestamp,
    is_deleted boolean not null default false,
    version bigint not null default 0,
    constraint pk_audit_log primary key (id),
    constraint ck_audit_log_action check (length(btrim(action)) > 0)
);

create index idx_audit_log_subject_action on audit_log (subject_user_id, action, id desc)
    where is_deleted = false;
create index idx_audit_log_resource on audit_log (resource_type, resource_id, id desc)
    where is_deleted = false;

comment on table audit_log is '审核、信用、账号和争议动作的统一审计事实';
comment on column audit_log.actor_id is '操作者 ID，系统策略为 null';
comment on column audit_log.change_value is '信用分等带符号数值变化';
comment on column audit_log.reason is '业务原因，写入前不得包含隐私内容';

-- ========== 可靠性与通知 ==========

create table idempotency_record (
    id bigint generated always as identity,
    command_scope varchar(64) not null,
    idempotency_key varchar(128) not null,
    request_hash varchar(64) not null,
    resource_id bigint,
    status varchar(16) not null default 'PROCESSING',
    gmt_create timestamptz not null default current_timestamp,
    gmt_modified timestamptz not null default current_timestamp,
    constraint pk_idempotency_record primary key (id),
    constraint uk_idempotency_record_key unique (command_scope, idempotency_key),
    constraint ck_idempotency_record_status check (status in ('PROCESSING', 'COMPLETED'))
);

create index idx_idempotency_record_created on idempotency_record (gmt_create);

comment on table idempotency_record is '并发命令的幂等占位与首次成功结果';
comment on column idempotency_record.command_scope is '业务命令命名空间';
comment on column idempotency_record.request_hash is '请求体摘要，同一键换请求体时拒绝';
comment on column idempotency_record.resource_id is '首次成功命令产生的资源 ID';
comment on column idempotency_record.status is 'PROCESSING 处理中；COMPLETED 已有结果';

create table outbox_event (
    id bigint generated always as identity,
    event_type varchar(100) not null,
    aggregate_type varchar(50) not null,
    aggregate_id bigint not null,
    payload jsonb not null,
    status varchar(16) not null default 'PENDING',
    available_at timestamptz not null default current_timestamp,
    published_at timestamptz,
    retry_count integer not null default 0,
    last_error varchar(1000),
    gmt_create timestamptz not null default current_timestamp,
    gmt_modified timestamptz not null default current_timestamp,
    constraint pk_outbox_event primary key (id),
    constraint ck_outbox_event_status check (status in ('PENDING', 'PUBLISHED', 'FAILED')),
    constraint ck_outbox_event_retry check (retry_count >= 0)
);

create index idx_outbox_event_pending on outbox_event (available_at, id)
    where status in ('PENDING', 'FAILED');

comment on table outbox_event is '事务内写出、提交后投递 RabbitMQ 的 Outbox';
comment on column outbox_event.id is '同时作为消费端幂等的事件 ID';
comment on column outbox_event.payload is '不含密钥和完整隐私内容的事件正文';
comment on column outbox_event.available_at is '最早可投递时间，失败后在这里退避';
comment on column outbox_event.last_error is '最近一次失败原因，不含敏感信息';

create table consumed_event (
    id bigint generated always as identity,
    event_id bigint not null,
    consumer_name varchar(80) not null,
    status varchar(16) not null default 'PROCESSING',
    retry_count integer not null default 0,
    last_error varchar(1000),
    started_at timestamptz not null default current_timestamp,
    completed_at timestamptz,
    constraint pk_consumed_event primary key (id),
    constraint uk_consumed_event_consumer unique (event_id, consumer_name),
    constraint ck_consumed_event_status check (status in ('PROCESSING', 'SUCCEEDED', 'FAILED')),
    constraint ck_consumed_event_retry check (retry_count >= 0)
);

create index idx_consumed_event_stale on consumed_event (started_at)
    where status = 'PROCESSING';

comment on table consumed_event is '消费端幂等状态，重复投递在此被收敛';
comment on column consumed_event.consumer_name is '稳定消费者标识';
comment on column consumed_event.status is 'PROCESSING / SUCCEEDED / FAILED';
comment on column consumed_event.started_at is '用于回收长时间停留在 PROCESSING 的记录';

create table notification (
    id bigint generated always as identity,
    user_id bigint not null,
    notification_type varchar(16) not null,
    title varchar(120) not null,
    content varchar(500) not null,
    resource_type varchar(32) not null,
    resource_id bigint not null,
    biz_key varchar(120) not null,
    is_read boolean not null default false,
    gmt_create timestamptz not null default current_timestamp,
    gmt_modified timestamptz not null default current_timestamp,
    is_deleted boolean not null default false,
    version bigint not null default 0,
    constraint pk_notification primary key (id),
    constraint uk_notification_biz unique (biz_key),
    constraint ck_notification_type check (notification_type in ('AUCTION', 'ORDER', 'MESSAGE')),
    constraint ck_notification_text check (length(btrim(title)) > 0 and length(btrim(content)) > 0)
);

create index idx_notification_user on notification (user_id, id desc) where is_deleted = false;
create index idx_notification_unread on notification (user_id)
    where is_deleted = false and is_read = false;

comment on table notification is '由集成事件落库的站内通知';
comment on column notification.notification_type is '按偏好分组：AUCTION / ORDER / MESSAGE';
comment on column notification.biz_key is '事件 ID 与收件人派生的唯一投递键，重复投递不重复入库';
comment on column notification.content is '业务摘要，不写密钥和完整私聊内容';

-- ========== 助手会话 ==========

-- 表结构由 Spring AI 的 JdbcChatMemoryRepository 决定，列名和索引不能改写。
create table spring_ai_chat_memory (
    conversation_id varchar(36) not null,
    content text not null,
    type varchar(10) not null check (type in ('USER', 'ASSISTANT', 'SYSTEM', 'TOOL')),
    "timestamp" timestamp not null,
    sequence_id bigint not null
);

create index spring_ai_chat_memory_conversation_id_timestamp_idx
    on spring_ai_chat_memory (conversation_id, "timestamp");
create index spring_ai_chat_memory_conversation_id_sequence_id_idx
    on spring_ai_chat_memory (conversation_id, sequence_id);

comment on table spring_ai_chat_memory is 'Spring AI ChatMemory 消息存储，结构由官方仓库维护';

create table assistant_conversation (
    id bigint generated always as identity,
    user_id bigint not null,
    title varchar(120) not null,
    last_message_at timestamptz not null default current_timestamp,
    gmt_create timestamptz not null default current_timestamp,
    gmt_modified timestamptz not null default current_timestamp,
    is_deleted boolean not null default false,
    version bigint not null default 0,
    constraint pk_assistant_conversation primary key (id),
    constraint ck_assistant_conversation_title check (length(btrim(title)) > 0)
);

create index idx_assistant_conversation_user
    on assistant_conversation (user_id, last_message_at desc, id desc)
    where is_deleted = false;

comment on table assistant_conversation is 'AI 助手会话元数据与归属';
comment on column assistant_conversation.user_id is '会话所有者，所有读写都按它隔离';
comment on column assistant_conversation.title is '由首个问题截断生成的标题';
comment on column assistant_conversation.last_message_at is '最近活动时间，会话列表按它排序';

commit;
