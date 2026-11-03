package com.lotflow.service.impl;

import com.lotflow.service.IdempotencyService;
import com.lotflow.service.IdempotencyService.IdempotencyClaim;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lotflow.common.BusinessException;
import com.lotflow.common.ResultCode;
import com.lotflow.entity.IdempotencyRecordDO;
import com.lotflow.mapper.IdempotencyRecordMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** 业务事务内的并发幂等键管理。 */
@Service
@RequiredArgsConstructor
public class IdempotencyServiceImpl implements IdempotencyService {

    private final IdempotencyRecordMapper mapper;
    private final ObjectMapper objectMapper;

    @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    @Override
    public IdempotencyClaim claim(String scope, String key, Object request) {
        String hash = hash(request);
        // 唯一索引收敛同键并发；未插入成功时再区分已完成、处理中和请求体不一致。
        Long insertedId = mapper.insertIfAbsent(scope, key, hash);
        if (insertedId != null) {
            return new IdempotencyClaim(insertedId, null, false);
        }
        IdempotencyRecordDO existing = mapper.selectByScopeAndKey(scope, key);
        if (existing == null) {
            throw new BusinessException(ResultCode.CONFLICT, "幂等记录不可用，请重试");
        }
        if (!existing.getRequestHash().equals(hash)) {
            throw new BusinessException(ResultCode.IDEMPOTENCY_CONFLICT, "幂等键已被不同请求使用");
        }
        return new IdempotencyClaim(existing.getId(), existing.getResourceId(),
                "COMPLETED".equals(existing.getStatus()));
    }

    @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    @Override
    public void complete(IdempotencyClaim claim, long resourceId) {
        // 业务结果与幂等完成状态使用同一个事务提交，不能让两者出现半成功。
        IdempotencyRecordDO record = new IdempotencyRecordDO();
        record.setId(claim.recordId());
        record.setResourceId(resourceId);
        record.setStatus("COMPLETED");
        mapper.updateById(record);
    }

    private String hash(Object request) {
        try {
            byte[] json = objectMapper.writeValueAsString(request).getBytes(StandardCharsets.UTF_8);
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(json));
        } catch (JsonProcessingException | NoSuchAlgorithmException exception) {
            throw new IllegalStateException("cannot hash idempotent request", exception);
        }
    }
}
