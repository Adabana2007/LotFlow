package com.lotflow.ai.review;

import com.lotflow.entity.ListingDO;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;

/** 服务端确定性规则先于 AI，禁止仅凭模型结果自动通过。 */
@Component
public class ReviewPolicy {

    private static final List<String> FORBIDDEN_TERMS = List.of(
            "象牙", "毒品", "枪支", "假货", "高仿", "复刻", "身份证", "银行卡");

    public boolean allowsAutoApproval(ListingDO listing, long imageCount) {
        if (imageCount <= 0) {
            // 没有图片无法核对描述和实物一致性，因此平台策略直接取消自动通过资格。
            return false;
        }
        String text = (listing.getTitle() + " " + listing.getDescription() + " "
                + (listing.getDefects() == null ? "" : listing.getDefects())).toLowerCase(Locale.ROOT);
        // 违禁词是服务端确定性红线，命中后即使模型判为低风险也只能转人工审核。
        return FORBIDDEN_TERMS.stream().noneMatch(text::contains);
    }
}
