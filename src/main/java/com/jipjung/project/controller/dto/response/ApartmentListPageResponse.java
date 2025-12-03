package com.jipjung.project.controller.dto.response;

import java.util.List;

/**
 * 아파트 목록 조회 응답 (페이징 메타 포함)
 */
public record ApartmentListPageResponse(
        List<ApartmentListResponse> apartments,
        int totalCount,
        int page,
        int size,
        int totalPages
) {
    public static ApartmentListPageResponse of(List<ApartmentListResponse> apartments,
                                               int totalCount,
                                               int page,
                                               int size) {
        int totalPages = (int) Math.ceil((double) totalCount / size);
        return new ApartmentListPageResponse(apartments, totalCount, page, size, totalPages);
    }
}
