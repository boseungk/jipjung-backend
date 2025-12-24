package com.jipjung.project.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jipjung.project.controller.dto.request.ApartmentSearchRequest;
import com.jipjung.project.controller.dto.request.FavoriteRequest;
import com.jipjung.project.controller.dto.response.*;
import com.jipjung.project.global.exception.DuplicateResourceException;
import com.jipjung.project.global.exception.ErrorCode;
import com.jipjung.project.global.exception.ResourceNotFoundException;
import com.jipjung.project.service.ApartmentService;
import com.jipjung.project.support.WithMockCustomUser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willDoNothing;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * ApartmentController 단위 테스트 (BDD 스타일)
 * 
 * @WebMvcTest를 사용한 슬라이스 테스트
 * ApartmentService는 @MockBean으로 Mock 처리
 */
@WebMvcTest(ApartmentController.class)
@DisplayName("ApartmentController 단위 테스트")
class ApartmentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ApartmentService apartmentService;

    @Autowired
    private ObjectMapper objectMapper;

    // ================================
    // 테스트 데이터 생성 헬퍼 메서드
    // ================================
    private ApartmentListPageResponse createMockPageResponse(int count) {
        List<ApartmentListResponse> apartments = count > 0 
            ? List.of(
                new ApartmentListResponse(
                    "11410-61", "금천현대", "홍제동", "연희로", 
                    2015, BigDecimal.valueOf(37.568904), BigDecimal.valueOf(126.934123),
                    12345L, java.time.LocalDate.now(), 45000L, new BigDecimal("84.5"), "5"
                )
            )
            : Collections.emptyList();
        return ApartmentListPageResponse.of(apartments, count, 0, 10);
    }

    private ApartmentDetailResponse createMockDetailResponse() {
        return new ApartmentDetailResponse(
            "11410-61", "금천현대", "홍제동", "서대문구 홍제동 123-45",
            "연희로", "10", "5", 2015,
            BigDecimal.valueOf(37.568904), BigDecimal.valueOf(126.934123),
            List.of(
                new ApartmentDetailResponse.DealInfo(
                    12345L, "A", "5", java.time.LocalDate.now(), 45000L, new BigDecimal("84.5")
                )
            )
        );
    }

    private FavoriteResponse createMockFavoriteResponse() {
        return new FavoriteResponse(
            42L, "11410-61", "금천현대", "홍제동", "연희로",
            2015, BigDecimal.valueOf(37.568904), BigDecimal.valueOf(126.934123),
            LocalDateTime.now()
        );
    }

    // ================================
    // 1. 아파트 목록 조회
    // ================================
    @Nested
    @DisplayName("GET /api/apartments - 아파트 목록 조회")
    class SearchApartments {

        @Test
        @DisplayName("Given: 아파트 데이터 존재 / When: 검색 조건 없이 요청 / Then: 200 OK + 페이징된 목록 반환")
        @WithMockCustomUser
        void givenApartmentsExist_whenSearchWithNoCondition_thenReturnPagedList() throws Exception {
            // Given
            ApartmentListPageResponse mockResponse = createMockPageResponse(1);
            given(apartmentService.searchApartments(any(ApartmentSearchRequest.class)))
                .willReturn(mockResponse);

            // When & Then
            mockMvc.perform(get("/api/apartments")
                    .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.apartments").isArray())
                .andExpect(jsonPath("$.data.totalCount").value(1))
                .andExpect(jsonPath("$.data.page").value(0));
        }

        @Test
        @DisplayName("Given: 아파트 데이터 존재 / When: 아파트명으로 검색 / Then: 200 OK + 필터링된 목록 반환")
        @WithMockCustomUser
        void givenApartmentsExist_whenSearchByAptName_thenReturnFilteredList() throws Exception {
            // Given
            ApartmentListPageResponse mockResponse = createMockPageResponse(1);
            given(apartmentService.searchApartments(any(ApartmentSearchRequest.class)))
                .willReturn(mockResponse);

            // When & Then
            mockMvc.perform(get("/api/apartments")
                    .param("aptNm", "금천")
                    .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.apartments[0].aptNm").value("금천현대"));
        }

        @Test
        @DisplayName("Given: 조건에 맞는 아파트 없음 / When: 검색 요청 / Then: 200 OK + 빈 목록 반환")
        @WithMockCustomUser
        void givenNoMatchingApartments_whenSearch_thenReturnEmptyList() throws Exception {
            // Given
            ApartmentListPageResponse emptyResponse = createMockPageResponse(0);
            given(apartmentService.searchApartments(any(ApartmentSearchRequest.class)))
                .willReturn(emptyResponse);

            // When & Then
            mockMvc.perform(get("/api/apartments")
                    .param("aptNm", "존재하지않는아파트")
                    .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.apartments").isEmpty())
                .andExpect(jsonPath("$.data.totalCount").value(0));
        }
    }

    // ================================
    // 2. 아파트 상세 조회
    // ================================
    @Nested
    @DisplayName("GET /api/apartments/{aptSeq} - 아파트 상세 조회")
    class GetApartmentDetail {

        @Test
        @DisplayName("Given: 존재하는 아파트 / When: aptSeq로 조회 / Then: 200 OK + 상세정보 반환")
        @WithMockCustomUser
        void givenValidAptSeq_whenGetDetail_thenReturnApartmentDetail() throws Exception {
            // Given
            String aptSeq = "11410-61";
            ApartmentDetailResponse mockResponse = createMockDetailResponse();
            given(apartmentService.getApartmentDetail(aptSeq)).willReturn(mockResponse);

            // When & Then
            mockMvc.perform(get("/api/apartments/{aptSeq}", aptSeq)
                    .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.aptSeq").value(aptSeq))
                .andExpect(jsonPath("$.data.aptNm").value("금천현대"))
                .andExpect(jsonPath("$.data.deals").isArray());
        }

        @Test
        @DisplayName("Given: 존재하지 않는 아파트 / When: aptSeq로 조회 / Then: 404 Not Found")
        @WithMockCustomUser
        void givenInvalidAptSeq_whenGetDetail_thenReturn404() throws Exception {
            // Given
            String invalidAptSeq = "invalid-99";
            given(apartmentService.getApartmentDetail(invalidAptSeq))
                .willThrow(new ResourceNotFoundException(ErrorCode.APARTMENT_NOT_FOUND));

            // When & Then
            mockMvc.perform(get("/api/apartments/{aptSeq}", invalidAptSeq)
                    .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isNotFound());
        }
    }

    // ================================
    // 3. 관심 아파트 등록
    // ================================
    @Nested
    @DisplayName("POST /api/apartments/favorites - 관심 아파트 등록")
    class AddFavorite {

        @Test
        @DisplayName("Given: 인증된 사용자 + 유효한 아파트 / When: 등록 요청 / Then: 201 Created")
        @WithMockCustomUser(userId = 1L)
        void givenAuthenticatedUser_whenAddFavorite_thenReturn201() throws Exception {
            // Given
            FavoriteRequest request = new FavoriteRequest("11410-61");
            FavoriteResponse mockResponse = createMockFavoriteResponse();
            given(apartmentService.addFavorite(eq(1L), any(FavoriteRequest.class)))
                .willReturn(mockResponse);

            // When & Then
            mockMvc.perform(post("/api/apartments/favorites")
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.data.aptSeq").value("11410-61"));
        }

        @Test
        @DisplayName("Given: 미인증 사용자 / When: 등록 요청 / Then: 401 Unauthorized")
        void givenUnauthenticated_whenAddFavorite_thenReturn401() throws Exception {
            // Given
            FavoriteRequest request = new FavoriteRequest("11410-61");

            // When & Then (미인증 시 Spring Security가 401 반환)
            mockMvc.perform(post("/api/apartments/favorites")
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("Given: 인증된 사용자 + aptSeq 비어있음 / When: 등록 요청 / Then: 400 Bad Request")
        @WithMockCustomUser
        void givenEmptyAptSeq_whenAddFavorite_thenReturn400() throws Exception {
            // Given
            FavoriteRequest request = new FavoriteRequest("");

            // When & Then (@NotBlank 검증 실패로 400 반환)
            mockMvc.perform(post("/api/apartments/favorites")
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Given: 인증된 사용자 + 존재하지 않는 아파트 / When: 등록 요청 / Then: 404 Not Found")
        @WithMockCustomUser(userId = 1L)
        void givenNonExistentApartment_whenAddFavorite_thenReturn404() throws Exception {
            // Given
            FavoriteRequest request = new FavoriteRequest("invalid-99");
            given(apartmentService.addFavorite(eq(1L), any(FavoriteRequest.class)))
                .willThrow(new ResourceNotFoundException(ErrorCode.APARTMENT_NOT_FOUND));

            // When & Then
            mockMvc.perform(post("/api/apartments/favorites")
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("Given: 인증된 사용자 + 이미 등록된 아파트 / When: 등록 요청 / Then: 409 Conflict")
        @WithMockCustomUser(userId = 1L)
        void givenAlreadyFavorited_whenAddFavorite_thenReturn409() throws Exception {
            // Given
            FavoriteRequest request = new FavoriteRequest("11410-61");
            given(apartmentService.addFavorite(eq(1L), any(FavoriteRequest.class)))
                .willThrow(new DuplicateResourceException(ErrorCode.DUPLICATE_FAVORITE));

            // When & Then
            mockMvc.perform(post("/api/apartments/favorites")
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isConflict());
        }
    }

    // ================================
    // 4. 내 관심 아파트 목록 조회
    // ================================
    @Nested
    @DisplayName("GET /api/apartments/favorites - 내 관심 아파트 목록")
    class GetMyFavorites {

        @Test
        @DisplayName("Given: 인증된 사용자 + 관심 아파트 존재 / When: 목록 조회 / Then: 200 OK + 목록 반환")
        @WithMockCustomUser(userId = 1L)
        void givenAuthenticatedUser_whenGetFavorites_thenReturnList() throws Exception {
            // Given
            List<FavoriteResponse> mockFavorites = List.of(createMockFavoriteResponse());
            given(apartmentService.getMyFavorites(1L)).willReturn(mockFavorites);

            // When & Then
            mockMvc.perform(get("/api/apartments/favorites")
                    .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].aptSeq").value("11410-61"));
        }

        @Test
        @DisplayName("Given: 인증된 사용자 + 관심 아파트 없음 / When: 목록 조회 / Then: 200 OK + 빈 목록")
        @WithMockCustomUser(userId = 1L)
        void givenNoFavorites_whenGetFavorites_thenReturnEmptyList() throws Exception {
            // Given
            given(apartmentService.getMyFavorites(1L)).willReturn(Collections.emptyList());

            // When & Then
            mockMvc.perform(get("/api/apartments/favorites")
                    .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isEmpty());
        }

        @Test
        @DisplayName("Given: 미인증 사용자 / When: 목록 조회 / Then: 401 Unauthorized")
        void givenUnauthenticated_whenGetFavorites_thenReturn401() throws Exception {
            // When & Then
            mockMvc.perform(get("/api/apartments/favorites")
                    .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isUnauthorized());
        }
    }

    // ================================
    // 5. 관심 아파트 삭제
    // ================================
    @Nested
    @DisplayName("DELETE /api/apartments/favorites/{id} - 관심 아파트 삭제")
    class DeleteFavorite {

        @Test
        @DisplayName("Given: 인증된 사용자 + 본인의 관심 아파트 / When: 삭제 요청 / Then: 200 OK")
        @WithMockCustomUser(userId = 1L)
        void givenOwnFavorite_whenDelete_thenReturn200() throws Exception {
            // Given
            Long favoriteId = 42L;
            willDoNothing().given(apartmentService).deleteFavorite(1L, favoriteId);

            // When & Then
            mockMvc.perform(delete("/api/apartments/favorites/{id}", favoriteId)
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        }

        @Test
        @DisplayName("Given: 미인증 사용자 / When: 삭제 요청 / Then: 401 Unauthorized")
        void givenUnauthenticated_whenDelete_thenReturn401() throws Exception {
            // When & Then
            mockMvc.perform(delete("/api/apartments/favorites/{id}", 42L)
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("Given: 인증된 사용자 + 존재하지 않는 ID / When: 삭제 요청 / Then: 404 Not Found")
        @WithMockCustomUser(userId = 1L)
        void givenNonExistentId_whenDelete_thenReturn404() throws Exception {
            // Given
            Long nonExistentId = 99999L;
            willThrow(new ResourceNotFoundException(ErrorCode.FAVORITE_NOT_FOUND))
                .given(apartmentService).deleteFavorite(1L, nonExistentId);

            // When & Then
            mockMvc.perform(delete("/api/apartments/favorites/{id}", nonExistentId)
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("Given: 인증된 사용자 + 타인의 관심 아파트 / When: 삭제 요청 / Then: 400 Bad Request")
        @WithMockCustomUser(userId = 1L)
        void givenOtherUserFavorite_whenDelete_thenReturn400() throws Exception {
            // Given
            Long otherUserFavoriteId = 100L;
            willThrow(new IllegalArgumentException("본인의 관심 아파트만 삭제할 수 있습니다"))
                .given(apartmentService).deleteFavorite(1L, otherUserFavoriteId);

            // When & Then
            mockMvc.perform(delete("/api/apartments/favorites/{id}", otherUserFavoriteId)
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isBadRequest());
        }
    }

    // ================================
    // 6. 지역 좌표 조회
    // ================================
    @Nested
    @DisplayName("GET /api/apartments/regions/{regionName}/coordinates - 지역 좌표 조회")
    class GetRegionCoordinates {

        @Test
        @DisplayName("Given: 아파트가 있는 지역 / When: 좌표 조회 / Then: 200 OK + 평균 좌표 반환")
        @WithMockCustomUser
        void givenRegionWithApartments_whenGetCoordinates_thenReturnAverageCoordinates() throws Exception {
            // Given
            String regionName = "강남구";
            RegionCoordinatesResponse mockResponse = new RegionCoordinatesResponse(
                regionName, 37.4979, 127.0276
            );
            given(apartmentService.getRegionCoordinates(regionName)).willReturn(mockResponse);

            // When & Then
            mockMvc.perform(get("/api/apartments/regions/{regionName}/coordinates", regionName)
                    .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.region").value(regionName))
                .andExpect(jsonPath("$.data.latitude").value(37.4979))
                .andExpect(jsonPath("$.data.longitude").value(127.0276));
        }

        @Test
        @DisplayName("Given: 아파트가 없는 지역 / When: 좌표 조회 / Then: 200 OK + 기본 좌표(서울시청) 반환")
        @WithMockCustomUser
        void givenRegionWithoutApartments_whenGetCoordinates_thenReturnDefaultCoordinates() throws Exception {
            // Given
            String regionName = "테스트구";
            RegionCoordinatesResponse defaultResponse = RegionCoordinatesResponse.defaultCoordinates(regionName);
            given(apartmentService.getRegionCoordinates(regionName)).willReturn(defaultResponse);

            // When & Then
            mockMvc.perform(get("/api/apartments/regions/{regionName}/coordinates", regionName)
                    .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.region").value(regionName))
                .andExpect(jsonPath("$.data.latitude").value(37.5665))
                .andExpect(jsonPath("$.data.longitude").value(126.978));
        }
    }
}
