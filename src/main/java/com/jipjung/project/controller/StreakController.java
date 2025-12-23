package com.jipjung.project.controller;

import com.jipjung.project.controller.dto.request.MilestoneClaimRequest;
import com.jipjung.project.controller.dto.response.GoalExpProgressResponse;
import com.jipjung.project.controller.dto.response.MilestoneRewardResponse;
import com.jipjung.project.global.response.ApiResponse;
import com.jipjung.project.service.CustomUserDetails;
import com.jipjung.project.service.CollectionService;
import com.jipjung.project.service.StreakService;
import com.jipjung.project.service.StreakService.MilestoneInfo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 스트릭 컨트롤러
 * <p>
 * 연속 저축 마일스톤 보상 관리 API를 제공합니다.
 * <ul>
 *   <li>GET  /api/streak/reward - 수령 가능한 마일스톤 조회</li>
 *   <li>POST /api/streak/reward - 마일스톤 보상 수령</li>
 *   <li>GET  /api/streak/milestones - 전체 마일스톤 상태 조회</li>
 * </ul>
 */
@Tag(name = "스트릭", description = "연속 저축 마일스톤 보상 API")
@RestController
@RequestMapping("/api/streak")
@RequiredArgsConstructor
public class StreakController {

    private final StreakService streakService;
    private final CollectionService collectionService;

    /**
     * 수령 가능한 마일스톤 보상 조회
     * <p>
     * 현재 스트릭 일수 이상이면서 아직 수령하지 않은 마일스톤 목록을 반환합니다.
     */
    @Operation(
            summary = "수령 가능한 마일스톤 조회",
            description = "현재 연속 저축 일수 기준으로 수령 가능한 마일스톤 보상 목록을 조회합니다."
    )
    @GetMapping("/reward")
    public ResponseEntity<ApiResponse<List<MilestoneInfo>>> getClaimableRewards(
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        List<MilestoneInfo> milestones = streakService.getClaimableMilestones(userDetails.getId());
        return ApiResponse.success(milestones);
    }

    /**
     * 마일스톤 보상 수령
     * <p>
     * 7/14/21/28일 연속 활동 마일스톤 달성 시 보너스 EXP를 지급받습니다.
     * 각 마일스톤은 사용자당 1회만 수령 가능합니다.
     */
    @Operation(
            summary = "마일스톤 보상 수령",
            description = "7/14/21/28일 연속 활동 마일스톤 보상을 수령합니다. 각 마일스톤은 1회만 수령 가능합니다."
    )
    @PostMapping("/reward")
    public ResponseEntity<ApiResponse<MilestoneRewardResponse>> claimReward(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody MilestoneClaimRequest request
    ) {
        StreakService.MilestoneRewardResult result =
                streakService.claimMilestoneReward(userDetails.getId(), request.milestoneDays());

        CollectionService.GoalProgress goalProgress = collectionService.getGoalProgress(userDetails.getId());
        return ApiResponse.success(
                MilestoneRewardResponse.from(result, GoalExpProgressResponse.from(goalProgress))
        );
    }

    /**
     * 전체 마일스톤 상태 조회
     * <p>
     * 모든 마일스톤의 수령 여부, 수령 가능 여부를 포함한 상태를 반환합니다.
     */
    @Operation(
            summary = "전체 마일스톤 상태 조회",
            description = "7/14/21/28일 마일스톤의 수령 여부, 수령 가능 여부 등 전체 상태를 조회합니다."
    )
    @GetMapping("/milestones")
    public ResponseEntity<ApiResponse<List<MilestoneInfo>>> getAllMilestones(
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        List<MilestoneInfo> milestones = streakService.getAllMilestones(userDetails.getId());
        return ApiResponse.success(milestones);
    }
}
