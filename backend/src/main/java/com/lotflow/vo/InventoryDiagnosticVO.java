package com.lotflow.vo;

import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

/** 已超过付款时限、仍需释放库存的订单诊断。 */
@Getter
@Setter
public class InventoryDiagnosticVO {
    private String orderId;
    private String orderNo;
    private String listingId;
    private String listingTitle;
    private String sourceType;
    private String orderStatus;
    private String reservationStatus;
    private OffsetDateTime deadline;
    private long overdueSeconds;
}
