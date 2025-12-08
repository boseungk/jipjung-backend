package com.jipjung.project.controller.dto.response;

import com.jipjung.project.domain.User;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "프로필 수정 응답")
public record ProfileUpdateResponse(
        @Schema(description = "수정된 사용자 정보")
        UserInfo user
) {
    public static ProfileUpdateResponse from(User user) {
        return new ProfileUpdateResponse(UserInfo.from(user));
    }

    @Schema(description = "사용자 정보")
    public record UserInfo(
            @Schema(description = "사용자 ID") Long id,
            @Schema(description = "이메일") String email,
            @Schema(description = "닉네임") String nickname,
            @Schema(description = "연소득 (원)") Long annualIncome,
            @Schema(description = "월 기존 대출 상환액 (원)") Long existingLoanMonthly,
            @Schema(description = "수정 일시") LocalDateTime updatedAt
    ) {
        public static UserInfo from(User user) {
            return new UserInfo(
                    user.getId(),
                    user.getEmail(),
                    user.getNickname(),
                    user.getAnnualIncome(),
                    user.getExistingLoanMonthly(),
                    user.getUpdatedAt()
            );
        }
    }
}
