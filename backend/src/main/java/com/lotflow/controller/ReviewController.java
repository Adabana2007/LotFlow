package com.lotflow.controller;

import com.lotflow.dto.ReviewDecisionDTO;
import com.lotflow.security.CurrentUser;
import com.lotflow.service.ReviewService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 人工审核接口。 */
@RestController
@RequestMapping("/api/v1/review-tasks")
public class ReviewController {

    private final ReviewService reviewService;
    private final CurrentUser currentUser;

    public ReviewController(ReviewService reviewService, CurrentUser currentUser) {
        this.reviewService = reviewService;
        this.currentUser = currentUser;
    }

    @PostMapping("/{taskId}/decisions")
    public ResponseEntity<Void> decide(@PathVariable long taskId,
                                       @Valid @RequestBody ReviewDecisionDTO request) {
        // 审核决定是人工最终处置：Service 校验审核员角色、任务状态并写不可缺省的审核记录。
        reviewService.decide(currentUser.id(), taskId, request);
        return ResponseEntity.noContent().build();
    }
}
