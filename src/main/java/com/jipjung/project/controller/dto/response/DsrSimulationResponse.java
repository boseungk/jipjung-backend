package com.jipjung.project.controller.dto.response;

import com.jipjung.project.dsr.DsrResult;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * DSR 시뮬레이션 응답 DTO (PRO 모드)
 * <p>
 * 시뮬레이션 결과와 함께 적용된 정책 정보, 팁, 게임 갱신 정보 제공.
 */
@Schema(description = "DSR 시뮬레이션 응답")
public record DsrSimulationResponse(

        @Schema(description = "현재 DSR (%)", example = "15.5")
        double currentDsrPercent,

        @Schema(description = "최대 한도(스트레스 금리 기준)까지 대출 시 DSR (%)", example = "39.8")
        double dsrAfterMaxLoanPercent,

        @Schema(description = "DSR 등급 (SAFE/WARNING/RESTRICTED)", example = "SAFE")
        String userGrade,

        @Schema(description = "최대 대출 가능액 (원)", example = "420000000")
        long maxLoanAmount,

        @Schema(description = "적용된 정책 정보")
        AppliedPolicy appliedPolicy,

        @Schema(description = "시뮬레이션 팁")
        String simulationTip,

        @Schema(description = "게임 갱신 정보 (목표 설정 시에만 제공)")
        GameUpdate gameUpdate
) {

    // =========================================================================
    // Nested Records
    // =========================================================================

    /**
     * 적용된 정책 상세
     */
    @Schema(description = "적용된 정책")
    public record AppliedPolicy(
            @Schema(description = "적용된 스트레스 가산금리 (%)", example = "1.2")
            double stressDsrRate,

            @Schema(description = "적용된 장래소득 인정 배율 (1.0 = 인정 없음)", example = "1.131")
            double youthIncomeMultiplier
    ) {}

    /**
     * 게임 갱신 정보 (Phase 2)
     * <p>
     * Spec 기준: reducedGap, expGained만 반환
     */
    @Schema(description = "게임 갱신 정보")
    public record GameUpdate(
            @Schema(description = "줄어든 목표 저축액 (원)", example = "50000000")
            long reducedGap,

            @Schema(description = "획득 경험치", example = "500")
            int expGained
    ) {}

    // =========================================================================
    // Factory Methods
    // =========================================================================

    /**
     * 팩토리 메서드: DsrResult + 부가 정보로 응답 생성 (gameUpdate 없음)
     *
     * @param result          DSR 계산 결과
     * @param stressRate      적용된 스트레스 금리
     * @param youthMultiplier 적용된 장래소득 배율
     * @param tip             맞춤 팁 메시지
     * @return 응답 DTO
     */
    public static DsrSimulationResponse from(
            DsrResult result,
            double stressRate,
            double youthMultiplier,
            String tip
    ) {
        return from(result, stressRate, youthMultiplier, tip, null);
    }

    /**
     * 팩토리 메서드: DsrResult + 부가 정보 + GameUpdate로 응답 생성
     *
     * @param result          DSR 계산 결과
     * @param stressRate      적용된 스트레스 금리
     * @param youthMultiplier 적용된 장래소득 배율
     * @param tip             맞춤 팁 메시지
     * @param gameUpdate      게임 갱신 정보 (nullable)
     * @return 응답 DTO
     */
    public static DsrSimulationResponse from(
            DsrResult result,
            double stressRate,
            double youthMultiplier,
            String tip,
            GameUpdate gameUpdate
    ) {
        return new DsrSimulationResponse(
                result.currentDsrPercent(),
                result.dsrAfterMaxLoanPercent(),
                result.grade(),
                result.maxLoanAmount(),
                new AppliedPolicy(stressRate, youthMultiplier),
                tip,
                gameUpdate
        );
    }
}
