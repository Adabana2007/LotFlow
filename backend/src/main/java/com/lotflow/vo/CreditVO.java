package com.lotflow.vo;

import java.util.List;

/** 当前信用分和近期变化。 */
public record CreditVO(String userId, int creditScore, List<CreditRecordVO> records) {
}
