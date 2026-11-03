package com.lotflow.controller;

import com.lotflow.common.CursorPageVO;
import com.lotflow.security.CurrentUser;
import com.lotflow.service.PersonalWorkspaceService;
import com.lotflow.vo.DisputeDetailVO;
import com.lotflow.vo.DisputeVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 买卖家查看本人争议、证据和资金状态的只读接口。 */
@RestController
@RequestMapping("/api/v1/disputes")
@RequiredArgsConstructor
public class DisputeController {

    private final PersonalWorkspaceService personalWorkspaceService;
    private final CurrentUser currentUser;

    @GetMapping
    public CursorPageVO<DisputeVO> list(@RequestParam(required = false) String cursor,
                                        @RequestParam(defaultValue = "20") int limit) {
        return personalWorkspaceService.disputes(currentUser.id(), false, cursor, limit);
    }

    @GetMapping("/{orderId}")
    public DisputeDetailVO detail(@PathVariable long orderId) {
        return personalWorkspaceService.dispute(currentUser.id(), false, orderId);
    }
}
