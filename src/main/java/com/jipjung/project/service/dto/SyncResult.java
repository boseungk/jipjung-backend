package com.jipjung.project.service.dto;

/**
 * 동기화 결과 DTO
 */
public record SyncResult(
        String lawdCd,
        String dealYmd,
        int syncedCount,
        int skippedCount,
        String message
) {
    /**
     * 성공 결과 생성
     */
    public static SyncResult success(String lawdCd, String dealYmd, int syncedCount, int skippedCount) {
        return new SyncResult(lawdCd, dealYmd, syncedCount, skippedCount, "동기화 완료");
    }

    /**
     * 스킵 결과 생성 (이미 동기화됨)
     */
    public static SyncResult skipped(String lawdCd, String dealYmd, String reason) {
        return new SyncResult(lawdCd, dealYmd, 0, 0, reason);
    }

    /**
     * 에러 결과 생성
     */
    public static SyncResult error(String lawdCd, String dealYmd, String errorMessage) {
        return new SyncResult(lawdCd, dealYmd, 0, 0, "오류: " + errorMessage);
    }
}
