package com.jipjung.project.external.kakao;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.Collections;
import java.util.List;

/**
 * Kakao Local API address search response DTO
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class KakaoAddressResponse {
    private List<Document> documents = Collections.emptyList();

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Document {
        private String addressName;
        private String x; // longitude
        private String y; // latitude
    }
}
