package com.jipjung.project.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 사용자 도메인 엔티티
 * - 인증/권한 정보
 * - 게임화 관련 정보 (레벨, 경험치, 스트릭)
 * - 금융 정보 (연소득, 기존대출)
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    // 기본 정보
    private Long id;
    private String email;
    private String password;
    private String name;
    private String nickname;
    private String phone;
    private String profileImageUrl;

    // OAuth 관련
    private String socialProvider;
    private String socialId;

    // 권한 및 상태
    private UserRole role;
    private Boolean isActive;
    private Boolean isDeleted;

    // 온보딩 정보
    private Boolean onboardingCompleted;
    private Integer birthYear;

    // 금융 정보
    private Long annualIncome;
    private Long existingLoanMonthly;
    private Long currentAssets;               // 온보딩 시 입력한 현재 자산

    // DSR 상태 (Phase 2)
    private String dsrMode;                   // "LITE" or "PRO"
    private LocalDateTime lastDsrCalculationAt;
    private Long cachedMaxLoanAmount;         // PRO 모드 대출 한도 캐시

    // 게임화 정보
    private Integer currentLevel;
    private Integer currentExp;
    private Integer streakCount;
    private LocalDate lastStreakDate;
    private Integer maxStreak;
    private Integer selectedThemeId;

    // 타임스탬프
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /**
     * 월 소득 계산 (연소득 / 12)
     */
    public Long getMonthlyIncome() {
        if (annualIncome == null || annualIncome == 0) {
            return 0L;
        }
        return annualIncome / 12;
    }

    /**
     * 소득 정보 존재 여부
     */
    public boolean hasIncomeInfo() {
        return annualIncome != null && annualIncome > 0;
    }
}
