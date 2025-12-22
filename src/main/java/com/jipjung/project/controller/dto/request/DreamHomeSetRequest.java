package com.jipjung.project.controller.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonSetter;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

/**
 * 드림홈 설정 요청 DTO
 * <p>
 * V2 리팩토링: aptSeq가 nullable로 변경되어 매물 없이 순수 저축 목표 설정 가능
 * 
 * <ul>
 *   <li>aptSeq = null: 매물 연결 없이 저축 목표만 설정</li>
 *   <li>aptSeq = "": 빈 문자열은 null로 처리 (연결 해제)</li>
 *   <li>aptSeq = "12345": 해당 매물 연결 또는 변경</li>
 * </ul>
 */
@Schema(description = "드림홈 설정 요청")
@Getter
@Setter
public class DreamHomeSetRequest {

    @Schema(description = "아파트 고유 ID (선택, null이면 매물 연결 없음)", example = "11410-61")
    @Setter(AccessLevel.NONE)
    private String aptSeq;

    @Schema(description = "저축 목표 금액 (원 단위)", example = "100000000", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "저축 목표 금액은 필수입니다")
    @Min(value = 1, message = "저축 목표 금액은 1원 이상이어야 합니다")
    private Long targetAmount;

    @Schema(description = "목표 달성일 (YYYY-MM-DD)", example = "2028-12-31", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "목표 달성일은 필수입니다")
    @Future(message = "목표 달성일은 미래 날짜여야 합니다")
    private LocalDate targetDate;

    @Schema(description = "월 목표 저축액 (원 단위, 선택)", example = "2500000")
    @Min(value = 0, message = "월 목표 저축액은 0 이상이어야 합니다")
    private Long monthlyGoal;

    @Schema(description = "선택한 테마 ID (선택, 양수만 허용)", example = "1")
    @Positive(message = "테마 ID는 양수여야 합니다")
    private Integer themeId;

    @Schema(description = "사용자 정의 집 이름 (선택, 없으면 아파트명 사용)", example = "우리 가족의 첫 집")
    @Size(max = 50, message = "집 이름은 50자 이하여야 합니다")
    private String houseName;

    @Schema(hidden = true)
    @JsonIgnore
    @Setter(AccessLevel.NONE)
    private boolean aptSeqProvided;

    /**
     * aptSeq가 요청에 포함되었는지 여부
     */
    public boolean hasAptSeqField() {
        return aptSeqProvided;
    }

    /**
     * aptSeq가 유효한 값인지 확인
     * <p>
     * 빈 문자열은 null과 동일하게 처리 (매물 연결 해제)
     */
    public boolean hasValidAptSeq() {
        return aptSeq != null && !aptSeq.isBlank();
    }

    /**
     * 정규화된 aptSeq 반환
     * <p>
     * 빈 문자열을 null로 변환하여 일관성 유지
     */
    public String normalizedAptSeq() {
        return hasValidAptSeq() ? aptSeq.trim() : null;
    }

    /**
     * aptSeq setter - 요청에 필드가 들어왔는지 추적
     */
    @JsonSetter("aptSeq")
    public void setAptSeq(String aptSeq) {
        this.aptSeq = aptSeq;
        this.aptSeqProvided = true;
    }
}
