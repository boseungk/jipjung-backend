package com.jipjung.project.domain;

import lombok.*;
import java.time.LocalDateTime;

/**
 * DSR 계산 이력 도메인
 * <p>
 * PRO 시뮬레이션 결과를 저장하여 대시보드에서 PRO 결과를 복원할 때 사용.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DsrCalculationHistory {
    private Long id;
    private Long userId;
    private String inputJson;       // DsrInput JSON
    private String resultJson;      // DsrResult JSON
    private String dsrMode;         // "LITE" or "PRO"
    private Long maxLoanAmount;     // 최대 대출 가능액
    private LocalDateTime createdAt;
}
