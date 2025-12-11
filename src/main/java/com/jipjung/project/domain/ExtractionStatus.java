package com.jipjung.project.domain;

/**
 * 영수증 이미지 추출 상태
 * <p>
 * <ul>
 *   <li>COMPLETE: 모든 필드(금액, 가게명, 카테고리, 결제일) 추출 성공</li>
 *   <li>PARTIAL: 일부 필드만 추출 성공</li>
 *   <li>FAILED: 추출 완전 실패</li>
 * </ul>
 */
public enum ExtractionStatus {
    COMPLETE,  // 전체 추출 성공
    PARTIAL,   // 부분 추출
    FAILED     // 추출 실패
}
