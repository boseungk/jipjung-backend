package com.jipjung.project.repository;

import org.apache.ibatis.annotations.*;

import java.time.LocalDateTime;

/**
 * MOLIT API 동기화 이력 Mapper
 * 중복 API 호출 방지용
 */
@Mapper
public interface MolitSyncHistoryMapper {

    /**
     * 최근 동기화 여부 확인
     * 지정된 시간 이후에 동기화된 이력이 있는지 확인
     *
     * @param lawdCd  법정동코드
     * @param dealYmd 거래년월
     * @param cutoff  기준 시간 (이 시간 이후 동기화 이력이 있으면 true)
     * @return 최근 동기화 여부
     */
    @Select("""
        SELECT COUNT(*) > 0
        FROM molit_sync_history
        WHERE lawd_cd = #{lawdCd}
          AND deal_ymd = #{dealYmd}
          AND synced_at >= #{cutoff}
        """)
    boolean existsRecentSync(
            @Param("lawdCd") String lawdCd,
            @Param("dealYmd") String dealYmd,
            @Param("cutoff") LocalDateTime cutoff
    );

    /**
     * 동기화 이력 삽입 또는 갱신 (Upsert)
     *
     * @param lawdCd      법정동코드
     * @param dealYmd     거래년월
     * @param syncedCount 동기화된 건수
     * @return 영향받은 행 수
     */
    @Insert("""
        INSERT INTO molit_sync_history (lawd_cd, deal_ymd, synced_count)
        VALUES (#{lawdCd}, #{dealYmd}, #{syncedCount})
        ON DUPLICATE KEY UPDATE
            synced_count = VALUES(synced_count),
            synced_at = CURRENT_TIMESTAMP
        """)
    int insertOrUpdate(
            @Param("lawdCd") String lawdCd,
            @Param("dealYmd") String dealYmd,
            @Param("syncedCount") int syncedCount
    );

    /**
     * 특정 지역/년월의 마지막 동기화 시간 조회
     */
    @Select("""
        SELECT synced_at
        FROM molit_sync_history
        WHERE lawd_cd = #{lawdCd}
          AND deal_ymd = #{dealYmd}
        """)
    LocalDateTime findLastSyncedAt(
            @Param("lawdCd") String lawdCd,
            @Param("dealYmd") String dealYmd
    );
}
