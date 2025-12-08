package com.jipjung.project.controller.dto.response;

import com.jipjung.project.domain.User;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "온보딩 정보 저장 응답")
public record OnboardingResponse(
        @Schema(description = "사용자 정보") UserInfo user,
        @Schema(description = "DSR 분석 결과") DsrResult dsrResult
) {
    /**
     * 팩토리 메서드
     */
    public static OnboardingResponse from(User user, DsrResult dsrResult) {
        return new OnboardingResponse(
                UserInfo.from(user),
                dsrResult
        );
    }

    // ============================
    // Nested Records
    // ============================

    @Schema(description = "사용자 정보")
    public record UserInfo(
            @Schema(description = "사용자 ID") Long id,
            @Schema(description = "닉네임") String nickname,
            @Schema(description = "온보딩 완료 여부") boolean onboardingCompleted
    ) {
        public static UserInfo from(User user) {
            return new UserInfo(
                    user.getId(),
                    user.getNickname(),
                    true  // 온보딩 완료 후 항상 true
            );
        }
    }

    @Schema(description = "DSR 분석 결과")
    public record DsrResult(
        @Schema(description = "DSR 비율 (%)", example = "12.0") double dsrRatio,
        @Schema(description = "등급 (SAFE, CAUTION, DANGER)", example = "SAFE") String grade,
        @Schema(description = "예상 최대 대출 가능액 (원)", example = "400000000") long maxLoanAmount
    ) { }
}
