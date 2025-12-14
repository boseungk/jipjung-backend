package com.jipjung.project.service;

/**
 * 경험치 기반 레벨 정책
 * <p>
 * 레벨/임계값 규칙을 여러 서비스에서 공통 사용하기 위한 헬퍼입니다.
 */
final class LevelPolicy {

    static final int MAX_LEVEL = 7;

    /**
     * 레벨별 필요 경험치 임계값
     * 인덱스 = 레벨 - 1
     */
    private static final int[] LEVEL_THRESHOLDS = {0, 100, 300, 600, 1000, 1500, 2100};

    private LevelPolicy() {}

    static int normalizeLevel(Integer level) {
        return level != null ? level : 1;
    }

    static int normalizeExp(Integer exp) {
        return exp != null ? exp : 0;
    }

    /**
     * 누적 경험치로부터 레벨 계산 (최대 레벨 포함)
     */
    static int calculateLevel(int exp) {
        for (int level = MAX_LEVEL; level >= 1; level--) {
            if (exp >= LEVEL_THRESHOLDS[level - 1]) {
                return level;
            }
        }
        return 1;
    }
}

