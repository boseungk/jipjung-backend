package com.jipjung.project.service;

import com.jipjung.project.domain.ActivityType;
import com.jipjung.project.domain.DailyActivity;
import com.jipjung.project.domain.StreakHistory;
import com.jipjung.project.domain.StreakMilestoneReward;
import com.jipjung.project.domain.User;
import com.jipjung.project.global.exception.BusinessException;
import com.jipjung.project.global.exception.ErrorCode;
import com.jipjung.project.repository.DailyActivityMapper;
import com.jipjung.project.repository.StreakHistoryMapper;
import com.jipjung.project.repository.StreakMilestoneRewardMapper;
import com.jipjung.project.repository.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 활동 기반 스트릭 서비스
 * <p>
 * 다양한 활동(대시보드 접속, AI 분석, AI 판결, 저축)을 통해 스트릭을 유지하고
 * 경험치를 획득하는 시스템을 관리합니다.
 * 
 * <h3>핵심 규칙</h3>
 * <ul>
 *   <li>하루에 1개 이상 활동 시 스트릭 유지</li>
 *   <li>모든 활동은 하루에 1회만 EXP 지급 (UNIQUE 제약)</li>
 *   <li>일일 EXP 상한 160 (마일스톤 보상 제외)</li>
 *   <li>마일스톤: 7/14/21/28일 연속 활동</li>
 * </ul>
 * 
 * @see ActivityType
 * @see DailyActivity
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StreakService {

    // =========================================================================
    // 상수 정의 - 비즈니스 규칙
    // =========================================================================
    
    /** 일일 EXP 상한 (마일스톤 보상 제외) */
    private static final int DAILY_EXP_CAP = 160;

    /**
     * 마일스톤별 보상 경험치
     * - 7일: 50 EXP (1주)
     * - 14일: 100 EXP (2주)
     * - 21일: 150 EXP (3주)
     * - 28일: 200 EXP (4주)
     */
    private static final Map<Integer, Integer> MILESTONE_REWARDS = Map.of(
            7, 50,
            14, 100,
            21, 150,
            28, 200
    );

    // =========================================================================
    // 의존성 주입
    // =========================================================================

    private final DailyActivityMapper dailyActivityMapper;
    private final StreakHistoryMapper streakHistoryMapper;
    private final StreakMilestoneRewardMapper milestoneRewardMapper;
    private final UserMapper userMapper;
    private final CollectionService collectionService;
    private final Clock clock;

    // =========================================================================
    // 공개 API 메서드
    // =========================================================================

    /**
     * 활동 기반 스트릭 참여
     * <p>
     * 다양한 활동 유형에 대해 스트릭과 EXP를 처리합니다.
     * 
     * <h4>멱등성 보장</h4>
     * UNIQUE 제약 위반 시 예외를 catch하여 alreadyParticipated를 반환합니다.
     * 
     * <h4>동시성 안전</h4>
     * try-catch로 동시 요청에도 안전하게 처리됩니다.
     *
     * @param userId 사용자 ID
     * @param activityType 활동 유형
     * @return 스트릭 결과 (획득 EXP, 레벨업 여부 등)
     */
    @Transactional
    public StreakResult participate(Long userId, ActivityType activityType) {
        LocalDate today = LocalDate.now(clock);  // KST 기준

        // 1. 이 활동 유형으로 오늘 이미 참여했는지 확인 (빠른 체크)
        if (alreadyParticipatedToday(userId, today, activityType)) {
            log.debug("User {} already participated today with activity {}", userId, activityType);
            return StreakResult.alreadyParticipated(activityType);
        }

        // 2. 사용자 row lock으로 사용자 단위 직렬화 (일일 캡/스트릭/레벨 동시성 보호)
        User user = userMapper.findByIdForUpdate(userId);

        // 3. 오늘 총 획득 EXP 확인 (상한 체크)
        EarnedExp expResult = calculateEarnedExp(userId, today, activityType);

        // 4. 활동 기록 저장 (UNIQUE 제약 위반 시 alreadyParticipated 반환)
        if (!insertDailyActivity(userId, today, activityType, expResult.earnedExp())) {
            return StreakResult.alreadyParticipated(activityType);
        }

        // 5. 오늘 첫 활동이면 스트릭 증가/기록
        StreakCounts streakCounts =
                updateStreakIfFirstActivity(user, userId, today, expResult.earnedExp(), activityType);

        // 6. EXP 적용 및 레벨업 체크
        boolean isLevelUp = applyExpAndLevelUpIfNeeded(userId, user, expResult.earnedExp());

        log.debug("Activity recorded: userId={}, activity={}, exp=+{}, capRemaining={}",
                userId, activityType, expResult.earnedExp(), expResult.capRemaining());

        if (expResult.earnedExp() > 0) {
            collectionService.checkAndUpdateCompletionByExp(userId);
        }

        return new StreakResult(
                streakCounts.currentStreak(),
                streakCounts.maxStreak(),
                expResult.earnedExp(),
                isLevelUp,
                false,
                activityType
        );
    }

    /**
     * 마일스톤 보상 수령
     * <p>
     * 7/14/21/28일 연속 활동 마일스톤 달성 시 보너스 EXP를 지급합니다.
     * 각 마일스톤은 사용자당 1회만 수령 가능합니다.
     *
     * @param userId        사용자 ID
     * @param milestoneDays 마일스톤 일수 (7, 14, 21, 28)
     * @return 마일스톤 보상 결과
     * @throws BusinessException 조건 미충족 또는 중복 수령 시
     */
    @Transactional
    public MilestoneRewardResult claimMilestoneReward(Long userId, int milestoneDays) {
        validateMilestone(milestoneDays);

        // 사용자 단위 직렬화로 EXP/레벨 동시성 보호
        User user = userMapper.findByIdForUpdate(userId);
        int currentStreak = nullToZero(user.getStreakCount());

        // 스트릭이 마일스톤 이상인지 검증
        if (currentStreak < milestoneDays) {
            throw new BusinessException(ErrorCode.STREAK_REWARD_NOT_ELIGIBLE);
        }

        // 중복 수령 방지
        if (milestoneRewardMapper.existsByUserAndMilestone(userId, milestoneDays)) {
            throw new BusinessException(ErrorCode.STREAK_REWARD_ALREADY_CLAIMED);
        }

        // 보상 지급 (마일스톤 보상은 일일 상한에 포함되지 않음)
        int expReward = MILESTONE_REWARDS.get(milestoneDays);
        StreakMilestoneReward reward = createReward(userId, milestoneDays, expReward, currentStreak);
        try {
            milestoneRewardMapper.insert(reward);
        } catch (DuplicateKeyException e) {
            throw new BusinessException(ErrorCode.STREAK_REWARD_ALREADY_CLAIMED);
        }

        // 경험치 적용 및 레벨업 체크 (마일스톤 보상은 일일 상한에 포함되지 않음)
        boolean isLevelUp = applyExpAndLevelUpIfNeeded(userId, user, expReward);

        log.info("Milestone reward claimed: userId={}, milestone={}일, exp=+{}",
                userId, milestoneDays, expReward);

        collectionService.checkAndUpdateCompletionByExp(userId);

        return new MilestoneRewardResult(milestoneDays, expReward, isLevelUp, currentStreak);
    }

    private StreakMilestoneReward createReward(Long userId, int milestoneDays, int expReward, int currentStreak) {
        return StreakMilestoneReward.builder()
                .userId(userId)
                .milestoneDays(milestoneDays)
                .expReward(expReward)
                .streakCountAtClaim(currentStreak)
                .build();
    }

    /**
     * 수령 가능한 마일스톤 목록 조회
     */
    @Transactional(readOnly = true)
    public List<MilestoneInfo> getClaimableMilestones(Long userId) {
        User user = userMapper.findById(userId);
        int currentStreak = nullToZero(user.getStreakCount());
        Set<Integer> claimedDays = findClaimedMilestoneDays(userId);

        return MILESTONE_REWARDS.entrySet().stream()
                .filter(entry -> currentStreak >= entry.getKey())
                .filter(entry -> !claimedDays.contains(entry.getKey()))
                .sorted(Map.Entry.comparingByKey())  // 정렬 보장
                .map(entry -> new MilestoneInfo(entry.getKey(), entry.getValue(), true, false, currentStreak))
                .toList();
    }

    /**
     * 모든 마일스톤 정보 조회 (수령 여부 포함)
     */
    @Transactional(readOnly = true)
    public List<MilestoneInfo> getAllMilestones(Long userId) {
        User user = userMapper.findById(userId);
        int currentStreak = nullToZero(user.getStreakCount());
        Set<Integer> claimedDays = findClaimedMilestoneDays(userId);

        return MILESTONE_REWARDS.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> {
                    int days = entry.getKey();
                    int exp = entry.getValue();
                    boolean claimed = claimedDays.contains(days);
                    boolean claimable = !claimed && currentStreak >= days;
                    return new MilestoneInfo(days, exp, claimable, claimed, currentStreak);
                })
                .toList();
    }

    // =========================================================================
    // 비공개 헬퍼 메서드
    // =========================================================================

    /**
     * streak_history에 기록 (기존 UI 호환용)
     * <p>
     * 오늘 첫 활동 시에만 호출됩니다.
     */
    private void insertStreakHistory(Long userId, LocalDate today, int expEarned) {
        try {
            StreakHistory history = StreakHistory.builder()
                    .userId(userId)
                    .streakDate(today)
                    .expEarned(expEarned)
                    .build();
            streakHistoryMapper.insert(history);
        } catch (DuplicateKeyException e) {
            // 이미 존재하면 무시 (정상 케이스)
            log.debug("StreakHistory already exists for userId={}, date={}", userId, today);
        }
    }

    /**
     * 오늘 획득 가능한 EXP 계산 (일일 상한 적용)
     */
    private EarnedExp calculateEarnedExp(Long userId, LocalDate today, ActivityType activityType) {
        int todayTotalExp = dailyActivityMapper.sumExpByUserIdAndDate(userId, today);
        int remainingCap = Math.max(0, DAILY_EXP_CAP - todayTotalExp);
        int earnedExp = Math.min(activityType.getBaseExp(), remainingCap);
        return new EarnedExp(earnedExp, remainingCap - earnedExp);
    }

    private boolean alreadyParticipatedToday(Long userId, LocalDate today, ActivityType activityType) {
        return dailyActivityMapper.existsByUserIdAndDateAndType(userId, today, activityType.name());
    }

    private boolean insertDailyActivity(
            Long userId,
            LocalDate today,
            ActivityType activityType,
            int earnedExp
    ) {
        DailyActivity activity = DailyActivity.of(userId, today, activityType, earnedExp);
        try {
            dailyActivityMapper.insert(activity);
            return true;
        } catch (DuplicateKeyException e) {
            log.debug("Duplicate activity detected for userId={}, activity={}", userId, activityType);
            return false;
        }
    }

    private boolean isFirstActivityToday(Long userId, LocalDate today) {
        return dailyActivityMapper.countByUserIdAndDate(userId, today) == 1;
    }

    private StreakCounts updateStreakIfFirstActivity(
            User user,
            Long userId,
            LocalDate today,
            int earnedExp,
            ActivityType activityType
    ) {
        int currentStreak = nullToZero(user.getStreakCount());
        int maxStreak = nullToZero(user.getMaxStreak());

        if (!isFirstActivityToday(userId, today)) {
            return new StreakCounts(currentStreak, maxStreak);
        }

        int newStreakCount = calculateNewStreakCount(user, today);
        int newMaxStreak = Math.max(newStreakCount, maxStreak);
        userMapper.updateStreak(userId, newStreakCount, newMaxStreak, today);

        insertStreakHistory(userId, today, earnedExp);

        log.info("Streak updated: userId={}, streak={}, activity={}",
                userId, newStreakCount, activityType);

        return new StreakCounts(newStreakCount, newMaxStreak);
    }

    /**
     * 새로운 스트릭 일수 계산
     * <p>
     * 어제 활동했으면 연속, 그 외에는 1로 리셋
     */
    private int calculateNewStreakCount(User user, LocalDate today) {
        LocalDate lastStreakDate = user.getLastStreakDate();

        if (lastStreakDate == null) {
            return 1;
        }

        // 어제 참여했으면 연속
        if (lastStreakDate.equals(today.minusDays(1))) {
            return nullToZero(user.getStreakCount()) + 1;
        }

        // 오늘 이미 참여 (다른 활동으로)
        if (lastStreakDate.equals(today)) {
            return nullToZero(user.getStreakCount());
        }

        // 하루 이상 빠졌으면 리셋
        return 1;
    }

    private boolean applyExpAndLevelUpIfNeeded(Long userId, User user, int expToAdd) {
        if (expToAdd <= 0) {
            return false;
        }

        int oldLevel = LevelPolicy.normalizeLevel(user.getCurrentLevel());
        int oldExp = LevelPolicy.normalizeExp(user.getCurrentExp());

        int newExp = oldExp + expToAdd;
        int newLevel = LevelPolicy.calculateLevel(newExp);

        userMapper.addExp(userId, expToAdd);

        if (newLevel > oldLevel) {
            userMapper.updateLevel(userId, newLevel);
            log.info("Level up: userId={}, {} -> {}", userId, oldLevel, newLevel);
            return true;
        }

        return false;
    }

    /**
     * 유효한 마일스톤인지 검증
     */
    private void validateMilestone(int milestoneDays) {
        if (!MILESTONE_REWARDS.containsKey(milestoneDays)) {
            throw new BusinessException(ErrorCode.STREAK_REWARD_NOT_ELIGIBLE);
        }
    }

    private Set<Integer> findClaimedMilestoneDays(Long userId) {
        return milestoneRewardMapper.findByUserId(userId).stream()
                .map(StreakMilestoneReward::getMilestoneDays)
                .collect(Collectors.toSet());
    }

    // =========================================================================
    // 유틸리티 메서드
    // =========================================================================

    private int nullToZero(Integer value) {
        return value != null ? value : 0;
    }


    private record EarnedExp(int earnedExp, int capRemaining) {}

    private record StreakCounts(int currentStreak, int maxStreak) {}

    // =========================================================================
    // 결과 레코드 (Immutable DTO)
    // =========================================================================

    /**
     * 스트릭 참여 결과
     * 
     * @param currentStreak 현재 연속일수
     * @param maxStreak 최대 연속일수
     * @param expEarned 획득 경험치 (상한 적용 후)
     * @param isLevelUp 레벨업 여부
     * @param alreadyParticipated 이미 참여했는지 여부
     * @param activityType 활동 유형
     */
    public record StreakResult(
            int currentStreak,
            int maxStreak,
            int expEarned,
            boolean isLevelUp,
            boolean alreadyParticipated,
            ActivityType activityType
    ) {
        public static StreakResult alreadyParticipated(ActivityType activityType) {
            return new StreakResult(0, 0, 0, false, true, activityType);
        }
    }

    /**
     * 마일스톤 보상 수령 결과
     */
    public record MilestoneRewardResult(
            int milestoneDays,
            int expReward,
            boolean isLevelUp,
            int streakAtClaim
    ) {}

    /**
     * 마일스톤 정보
     */
    public record MilestoneInfo(
            int days,
            int expReward,
            boolean claimable,
            boolean claimed,
            int currentStreak
    ) {}
}
