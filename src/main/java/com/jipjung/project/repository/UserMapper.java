package com.jipjung.project.repository;

import com.jipjung.project.domain.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.util.Optional;

/**
 * 사용자 Mapper
 */
@Mapper
public interface UserMapper {

    /**
     * ID로 활성 사용자 조회 (is_deleted=false)
     */
    User findById(@Param("userId") Long userId);

    /**
     * ID로 활성 사용자 조회 + row lock (FOR UPDATE)
     * <p>
     * 스트릭/EXP 등 동시 갱신 구간에서 사용자 단위 직렬화를 위해 사용합니다.
     */
    User findByIdForUpdate(@Param("userId") Long userId);

    Optional<User> findByEmail(@Param("email") String email);

    int insertUser(User user);

    boolean existsByEmail(@Param("email") String email);

    /**
     * 온보딩 정보 업데이트
     * - birthYear, annualIncome, existingLoanMonthly, currentAssets, onboardingCompleted=true 설정
     */
    int updateOnboarding(
            @Param("userId") Long userId,
            @Param("birthYear") Integer birthYear,
            @Param("annualIncome") Long annualIncome,
            @Param("existingLoanMonthly") Long existingLoanMonthly,
            @Param("currentAssets") Long currentAssets
    );

    /**
     * 프로필 정보 업데이트
     */
    int updateProfile(
            @Param("userId") Long userId,
            @Param("nickname") String nickname,
            @Param("annualIncome") Long annualIncome,
            @Param("existingLoanMonthly") Long existingLoanMonthly
    );

    // =========================================================================
    // Phase 2: DSR 캐시 관리
    // =========================================================================

    /**
     * DSR 캐시 업데이트 (PRO 시뮬레이션 후)
     */
    @Update("""
        UPDATE `user`
        SET dsr_mode = #{dsrMode},
            cached_max_loan_amount = #{cachedMaxLoanAmount},
            last_dsr_calculation_at = CURRENT_TIMESTAMP
        WHERE user_id = #{userId}
    """)
    int updateDsrCache(
            @Param("userId") Long userId,
            @Param("dsrMode") String dsrMode,
            @Param("cachedMaxLoanAmount") Long cachedMaxLoanAmount
    );

    /**
     * 경험치 추가
     */
    @Update("UPDATE `user` SET current_exp = current_exp + #{expToAdd} WHERE user_id = #{userId}")
    int addExp(@Param("userId") Long userId, @Param("expToAdd") int expToAdd);

    /**
     * 레벨 업데이트
     */
    @Update("UPDATE `user` SET current_level = #{level} WHERE user_id = #{userId}")
    int updateLevel(@Param("userId") Long userId, @Param("level") int level);

    /**
     * DSR 캐시 무효화 (목표/프로필 변경 시)
     */
    @Update("UPDATE `user` SET cached_max_loan_amount = NULL, dsr_mode = 'LITE' WHERE user_id = #{userId}")
    int invalidateDsrCache(@Param("userId") Long userId);

    /**
     * PRO 시뮬레이션 입력값으로 프로필(연소득, 월 상환액) 업데이트
     */
    @Update("""
        UPDATE `user`
        SET annual_income = #{annualIncome},
            existing_loan_monthly = #{existingLoanMonthly}
        WHERE user_id = #{userId}
    """)
    int updateFinancialInfo(
            @Param("userId") Long userId,
            @Param("annualIncome") Long annualIncome,
            @Param("existingLoanMonthly") Long existingLoanMonthly
    );

    /**
     * 사용자 Soft Delete (is_deleted = true, is_active = false)
     */
    @Update("""
        UPDATE `user`
        SET is_deleted = true,
            is_active = false,
            updated_at = CURRENT_TIMESTAMP
        WHERE user_id = #{userId}
          AND is_deleted = false
    """)
    int softDeleteUser(@Param("userId") Long userId);

    /**
     * 사용자의 선택 테마 업데이트
     * <p>
     * 드림홈 설정 시 사용자가 선택한 테마 ID를 저장.
     *
     * @param userId  사용자 ID
     * @param themeId 테마 ID
     * @return 업데이트 영향 행 수
     */
    @Update("""
        UPDATE `user`
        SET selected_theme_id = #{themeId},
            updated_at = CURRENT_TIMESTAMP
        WHERE user_id = #{userId}
          AND is_deleted = false
    """)
    int updateSelectedTheme(@Param("userId") Long userId, @Param("themeId") Integer themeId);

    /**
     * 스트릭 정보 업데이트
     * <p>
     * 연속 저축 일수, 최대 기록, 마지막 참여일을 갱신합니다.
     *
     * @param userId         사용자 ID
     * @param streakCount    현재 연속일수
     * @param maxStreak      최대 연속일수
     * @param lastStreakDate 마지막 스트릭 참여일
     * @return 업데이트 영향 행 수
     */
    @Update("""
        UPDATE `user`
        SET streak_count = #{streakCount},
            max_streak = #{maxStreak},
            last_streak_date = #{lastStreakDate},
            updated_at = CURRENT_TIMESTAMP
        WHERE user_id = #{userId}
          AND is_deleted = false
    """)
    int updateStreak(
            @Param("userId") Long userId,
            @Param("streakCount") int streakCount,
            @Param("maxStreak") int maxStreak,
            @Param("lastStreakDate") java.time.LocalDate lastStreakDate
    );
}
