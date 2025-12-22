package com.jipjung.project.repository;

import com.jipjung.project.domain.DreamHome;
import com.jipjung.project.domain.DreamHomeStatus;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;

/**
 * 드림홈 Mapper
 */
@Mapper
public interface DreamHomeMapper {

    /**
     * 사용자의 활성 드림홈 조회
     * - status = 'ACTIVE', is_deleted = false
     * - 아파트 정보 JOIN 포함
     */
    DreamHome findActiveByUserId(@Param("userId") Long userId);

    /**
     * 대시보드용 드림홈 조회
     * - ACTIVE가 있으면 ACTIVE를 우선, 없으면 최근 COMPLETED를 반환
     * - is_deleted = false
     * - 아파트 정보 JOIN 포함
     */
    DreamHome findLatestForDashboardByUserId(@Param("userId") Long userId);

    /**
     * 드림홈 단건 조회
     * - is_deleted = false
     * - 아파트 정보 JOIN 포함
     */
    DreamHome findById(@Param("dreamHomeId") Long dreamHomeId);

    /**
     * 드림홈 생성
     */
    int insert(DreamHome dreamHome);

    /**
     * 드림홈 상세 정보 업데이트
     * - targetDate, monthlyGoal, houseName만 변경
     */
    int updateDreamHomeDetails(
            @Param("dreamHomeId") Long dreamHomeId,
            @Param("targetDate") LocalDate targetDate,
            @Param("monthlyGoal") Long monthlyGoal,
            @Param("houseName") String houseName
    );

    /**
     * 드림홈 상태 업데이트
     */
    int updateStatus(@Param("dreamHomeId") Long dreamHomeId, @Param("status") DreamHomeStatus status);

    /**
     * 현재 저축액 업데이트
     */
    int updateCurrentSavedAmount(@Param("dreamHomeId") Long dreamHomeId, @Param("currentSavedAmount") Long currentSavedAmount);

    /**
     * 매물 참조 업데이트 (저축 목표에 영향 없음)
     * <p>
     * V2 리팩토링: apt_seq만 독립적으로 변경 가능
     *
     * @param dreamHomeId 드림홈 ID
     * @param aptSeq      아파트 코드 (null이면 연결 해제)
     */
    int updateAptSeq(@Param("dreamHomeId") Long dreamHomeId, @Param("aptSeq") String aptSeq);

    /**
     * 저축 목표 금액 업데이트
     * <p>
     * V2 리팩토링: 저축 진행 전(currentSavedAmount=0)에만 호출되어야 함
     *
     * @param dreamHomeId  드림홈 ID
     * @param targetAmount 새 저축 목표 (원 단위)
     */
    int updateTargetAmount(@Param("dreamHomeId") Long dreamHomeId, @Param("targetAmount") Long targetAmount);
}
