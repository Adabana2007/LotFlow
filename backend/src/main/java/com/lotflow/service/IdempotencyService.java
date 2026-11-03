package com.lotflow.service;

/** 业务事务内的并发幂等键管理。 */
public interface IdempotencyService {

    IdempotencyClaim claim(String scope, String key, Object request);

    void complete(IdempotencyClaim claim, long resourceId);

    record IdempotencyClaim(long recordId, Long resourceId, boolean completed) {
    }
}
