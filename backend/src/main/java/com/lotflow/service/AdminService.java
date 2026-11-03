package com.lotflow.service;

import com.lotflow.dto.CreditAdjustmentDTO;
import com.lotflow.dto.DisputeResolutionDTO;
import com.lotflow.dto.RiskDispositionDTO;
import com.lotflow.dto.UserStatusDTO;

/** 运营后台人工处置。 */
public interface AdminService {

    void resolveDispute(long adminId, long disputeId, DisputeResolutionDTO request);

    void updateUserStatus(long adminId, long userId, UserStatusDTO request);

    void adjustCredit(long adminId, long userId, CreditAdjustmentDTO request);

    void retryInventoryRelease(long adminId, long orderId);

    void disposeRiskEvent(long adminId, long messageId, RiskDispositionDTO request);
}
