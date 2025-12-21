package com.jipjung.project.external.molit;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 국토부 실거래가 API 거래 데이터 응답 DTO
 * XML item 요소에 매핑되며, 파싱 헬퍼 메서드 포함
 */
@Data
@XmlAccessorType(XmlAccessType.FIELD)
public class MolitDealResponse {

    // === API 응답 필드 (XML 매핑) ===
    private String sggCd;              // 시군구코드
    private String umdNm;              // 법정동명
    private String aptNm;              // 단지명
    private String jibun;              // 지번
    private String excluUseAr;         // 전용면적
    private String dealYear;           // 계약년도
    private String dealMonth;          // 계약월
    private String dealDay;            // 계약일
    private String dealAmount;         // 거래금액 (공백/쉼표 포함)
    private String floor;              // 층
    private String buildYear;          // 건축년도
    private String aptDong;            // 아파트 동명
    private String cdealType;          // 해제여부 (비어있으면 정상 거래)
    private String cdealDay;           // 해제사유발생일
    private String dealingGbn;         // 거래유형 (중개/직거래)
    private String estateAgentSggNm;   // 중개사소재지(시군구)
    private String rgstDate;           // 등기일자
    private String slerGbn;            // 매도자 구분
    private String buyerGbn;           // 매수자 구분
    private String landLeaseholdGbn;   // 토지임대부 여부

    // === 파싱 헬퍼 메서드 ===

    /**
     * 건축년도 (Integer)
     */
    public Integer getBuildYearInt() {
        return parseInteger(buildYear);
    }

    /**
     * 거래년도 (Integer)
     */
    public Integer getDealYearInt() {
        return parseInteger(dealYear);
    }

    /**
     * 거래월 (Integer)
     */
    public Integer getDealMonthInt() {
        return parseInteger(dealMonth);
    }

    /**
     * 거래일 (Integer)
     */
    public Integer getDealDayInt() {
        return parseInteger(dealDay);
    }

    /**
     * 전용면적 (BigDecimal)
     */
    public BigDecimal getExcluUseArDecimal() {
        if (excluUseAr == null || excluUseAr.trim().isEmpty()) {
            return null;
        }
        return new BigDecimal(excluUseAr.trim());
    }

    /**
     * 거래금액 정규화 (공백/쉼표 제거)
     * " 185,000" → "185000"
     */
    public String getDealAmountNormalized() {
        if (dealAmount == null) {
            return null;
        }
        return dealAmount.trim().replace(",", "");
    }

    /**
     * 거래금액 (만원 단위 Long)
     */
    public Long getDealAmountManwon() {
        String normalized = getDealAmountNormalized();
        if (normalized == null || normalized.isEmpty()) {
            return null;
        }
        return Long.parseLong(normalized);
    }

    /**
     * 거래금액 (원 단위 Long)
     */
    public Long getDealAmountWon() {
        Long manwon = getDealAmountManwon();
        return manwon != null ? manwon * 10_000L : null;
    }

    /**
     * 해제된 거래인지 확인
     * cdealType이 비어있지 않으면 해제 거래
     */
    public boolean isCanceledDeal() {
        return cdealType != null && !cdealType.trim().isEmpty();
    }

    /**
     * 문자열 → Integer 변환 (null-safe)
     */
    private Integer parseInteger(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return Integer.parseInt(value.trim());
    }
}
