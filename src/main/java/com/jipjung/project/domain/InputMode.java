package com.jipjung.project.domain;

/**
 * 지출 분석 입력 모드
 * <p>
 * <ul>
 *   <li>IMAGE: 영수증 사진 업로드 → AI가 정보 추출</li>
 *   <li>MANUAL: 수기로 직접 입력</li>
 * </ul>
 */
public enum InputMode {
    IMAGE,   // 영수증 입력
    MANUAL   // 수기 입력
}
