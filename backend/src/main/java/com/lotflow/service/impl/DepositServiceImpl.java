package com.lotflow.service.impl;

import com.lotflow.service.DepositService;
import com.lotflow.service.WalletService;

import com.lotflow.common.BusinessException;
import com.lotflow.common.ResultCode;
import com.lotflow.entity.DepositHoldDO;
import com.lotflow.entity.OrderDO;
import com.lotflow.entity.enums.DomainEnums.DepositStatus;
import com.lotflow.entity.enums.DomainEnums.DepositType;
import com.lotflow.mapper.DepositHoldMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;

/** 保证金状态和资金流水联动。 */
@Service
@RequiredArgsConstructor
public class DepositServiceImpl implements DepositService {

    private final DepositHoldMapper mapper;
    private final WalletService walletService;

    @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    @Override
    public void moveBuyerDepositToSeller(OrderDO order) {
        if (order.getAuctionId() == null || order.getDepositApplied().signum() == 0) {
            return;
        }
        DepositHoldDO hold = mapper.selectForUpdate(order.getAuctionId(), order.getBuyerId(),
                DepositType.BUYER);
        if (hold == null || hold.getStatus() != DepositStatus.HELD) {
            // 违约赔付必须基于仍在冻结中的买家保证金，否则说明资金已被支付或补偿任务处理过。
            throw new BusinessException(ResultCode.CONFLICT, "买家保证金状态异常");
        }
        // 违约赔付直接把冻结余额转给卖家，不能先解冻再入账，否则这笔钱可能被其他订单消费。
        walletService.transferFrozenToUser(order.getBuyerId(), order.getSellerId(), hold.getAmount(),
                "deposit:buyer-default:" + order.getId());
        hold.setStatus(DepositStatus.COMPENSATED);
        mapper.updateById(hold);
    }

    @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    @Override
    public void releaseSellerDeposit(Long auctionId) {
        if (auctionId == null) {
            return;
        }
        var holds = mapper.selectHeldByAuction(auctionId);
        holds.stream()
                .filter(hold -> hold.getDepositType() == DepositType.SELLER)
                .findFirst()
                .ifPresent(hold -> {
                    // 卖家保证金释放使用保证金记录 ID 生成业务键，重复超时任务命中同一流水时保持幂等。
                    walletService.releaseFrozen(hold.getUserId(), hold.getAmount(),
                            "deposit:seller-release:" + hold.getId());
                    hold.setStatus(DepositStatus.RELEASED);
                    mapper.updateById(hold);
                });
    }
}
