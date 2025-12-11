package com.jipjung.project.controller.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 판결 요청 DTO
 */
@Schema(description = "판결 요청")
public record JudgmentRequest(

    @Schema(description = "대화 ID (analyze에서 받은 값)", example = "501")
    @NotNull(message = "대화 ID는 필수입니다")
    Long conversationId,

    @Schema(description = "선택한 변명 ID", example = "STRESS")
    @NotNull(message = "변명 선택은 필수입니다")
    String selectedExcuseId,

    @Schema(description = "직접 입력 변명 (500자 이내)", example = "")
    @Size(max = 500, message = "직접 입력 변명은 500자 이하여야 합니다")
    String customExcuse

) {}
