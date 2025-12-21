package com.jipjung.project.external.molit;

import jakarta.xml.bind.annotation.*;
import lombok.Data;

import java.util.Collections;
import java.util.List;

/**
 * 국토부 실거래가 API XML 응답 래퍼
 * JAXB를 사용하여 XML을 Java 객체로 언마샬링
 */
@Data
@XmlRootElement(name = "response")
@XmlAccessorType(XmlAccessType.FIELD)
public class MolitApiResponse {

    private Header header;
    private Body body;

    @Data
    @XmlAccessorType(XmlAccessType.FIELD)
    public static class Header {
        private String resultCode;
        private String resultMsg;
    }

    @Data
    @XmlAccessorType(XmlAccessType.FIELD)
    public static class Body {
        private Items items;
        private int totalCount;
        private int numOfRows;
        private int pageNo;
    }

    @Data
    @XmlAccessorType(XmlAccessType.FIELD)
    public static class Items {
        @XmlElement(name = "item")
        private List<MolitDealResponse> item;
    }

    /**
     * 거래 항목 리스트 반환 (null-safe)
     */
    public List<MolitDealResponse> getItems() {
        if (body == null || body.items == null || body.items.item == null) {
            return Collections.emptyList();
        }
        return body.items.item;
    }

    /**
     * API 호출 성공 여부 (resultCode = "00" 또는 "000")
     */
    public boolean isSuccess() {
        if (header == null || header.resultCode == null) {
            return false;
        }
        String code = header.resultCode.trim();
        return "00".equals(code) || "000".equals(code);
    }

    /**
     * 에러 메시지 반환
     */
    public String getErrorMessage() {
        if (header == null) {
            return "응답 헤더가 없습니다";
        }
        return String.format("[%s] %s", header.resultCode, header.resultMsg);
    }
}
