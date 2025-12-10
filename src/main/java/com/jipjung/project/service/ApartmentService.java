package com.jipjung.project.service;

import com.jipjung.project.global.exception.DuplicateResourceException;
import com.jipjung.project.global.exception.ErrorCode;
import com.jipjung.project.global.exception.ResourceNotFoundException;
import com.jipjung.project.controller.dto.request.ApartmentSearchRequest;
import com.jipjung.project.controller.dto.request.FavoriteRequest;
import com.jipjung.project.controller.dto.response.ApartmentDetailResponse;
import com.jipjung.project.controller.dto.response.ApartmentListPageResponse;
import com.jipjung.project.controller.dto.response.ApartmentListResponse;
import com.jipjung.project.controller.dto.response.FavoriteResponse;
import com.jipjung.project.controller.dto.response.RegionCoordinatesResponse;
import com.jipjung.project.domain.Apartment;
import com.jipjung.project.domain.FavoriteApartment;
import com.jipjung.project.repository.ApartmentMapper;
import com.jipjung.project.repository.FavoriteApartmentMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ApartmentService {

    private final ApartmentMapper apartmentMapper;
    private final FavoriteApartmentMapper favoriteApartmentMapper;

    /**
     * 아파트 목록 조회 (검색 및 페이징)
     * 각 아파트의 최신 실거래 1건 포함
     */
    @Transactional(readOnly = true)
    public ApartmentListPageResponse searchApartments(ApartmentSearchRequest request) {
        List<Apartment> apartments = apartmentMapper.findAllWithLatestDeal(request);
        int totalCount = apartmentMapper.count(request);

        List<ApartmentListResponse> responses = apartments.stream()
                .map(apt -> ApartmentListResponse.from(apt, apt.getLatestDeal()))
                .toList();

        return ApartmentListPageResponse.of(responses, totalCount, request.page(), request.size());
    }

    /**
     * 아파트 상세 조회
     * 해당 아파트의 모든 실거래 이력 포함
     */
    @Transactional(readOnly = true)
    public ApartmentDetailResponse getApartmentDetail(String aptSeq) {
        Apartment apartment = apartmentMapper.findByAptSeqWithDeals(aptSeq)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.APARTMENT_NOT_FOUND));

        return ApartmentDetailResponse.from(apartment, apartment.getDeals());
    }

    /**
     * 관심 아파트 등록
     */
    @Transactional
    public FavoriteResponse addFavorite(Long userId, FavoriteRequest request) {
        validateApartmentExists(request.aptSeq());
        validateFavoriteNotDuplicate(userId, request.aptSeq());

        FavoriteApartment favorite = createFavorite(userId, request);
        favoriteApartmentMapper.insert(favorite);

        FavoriteApartment savedFavorite = favoriteApartmentMapper.findById(favorite.getId())
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.FAVORITE_NOT_FOUND));

        return FavoriteResponse.from(savedFavorite);
    }

    private FavoriteApartment createFavorite(Long userId, FavoriteRequest request) {
        return FavoriteApartment.builder()
                .userId(userId)
                .aptSeq(request.aptSeq())
                .build();
    }

    /**
     * 내 관심 아파트 목록 조회
     */
    @Transactional(readOnly = true)
    public List<FavoriteResponse> getMyFavorites(Long userId) {
        return favoriteApartmentMapper.findByUserId(userId).stream()
                .map(FavoriteResponse::from)
                .toList();
    }

    /**
     * 관심 아파트 삭제
     */
    @Transactional
    public void deleteFavorite(Long userId, Long favoriteId) {
        FavoriteApartment favorite = favoriteApartmentMapper.findById(favoriteId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.FAVORITE_NOT_FOUND));

        validateFavoriteOwnership(favorite, userId);

        favoriteApartmentMapper.deleteById(favoriteId);
    }

    /**
     * 아파트 존재 여부 검증
     */
    private void validateApartmentExists(String aptSeq) {
        if (!apartmentMapper.existsByAptSeq(aptSeq)) {
            throw new ResourceNotFoundException(ErrorCode.APARTMENT_NOT_FOUND);
        }
    }

    /**
     * 관심 아파트 중복 검증
     */
    private void validateFavoriteNotDuplicate(Long userId, String aptSeq) {
        if (favoriteApartmentMapper.existsByUserIdAndAptSeq(userId, aptSeq)) {
            throw new DuplicateResourceException(ErrorCode.DUPLICATE_FAVORITE);
        }
    }

    /**
     * 관심 아파트 소유권 검증
     */
    private void validateFavoriteOwnership(FavoriteApartment favorite, Long userId) {
        if (!favorite.getUserId().equals(userId)) {
            throw new IllegalArgumentException("본인의 관심 아파트만 삭제할 수 있습니다");
        }
    }

    /**
     * 지역명으로 좌표 조회
     * 해당 지역의 아파트 평균 좌표를 반환합니다.
     * 
     * @param regionName 지역명 (예: 강남구, 서초구)
     * @return 지역 좌표 (없으면 서울시청 기본 좌표)
     */
    @Transactional(readOnly = true)
    public RegionCoordinatesResponse getRegionCoordinates(String regionName) {
        String normalizedRegion = normalizeToGugun(regionName);
        RegionCoordinatesResponse coords = apartmentMapper.findAverageCoordinatesByRegion(normalizedRegion);
        
        // 좌표가 없거나 null인 경우 기본값 반환
        if (coords == null || coords.latitude() == null || coords.longitude() == null) {
            return RegionCoordinatesResponse.defaultCoordinates(normalizedRegion);
        }
        
        return coords;
    }

    /**
     * 입력 문자열에서 구/군명을 추출해 정규화
     * - "서울특별시 강남구" -> "강남구"
     * - 공백 기준 마지막 토큰을 사용
     */
    private String normalizeToGugun(String regionName) {
        if (regionName == null) {
            throw new IllegalArgumentException("지역명을 입력해주세요");
        }
        String normalized = regionName.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("지역명을 입력해주세요");
        }
        String[] tokens = normalized.split("\\s+");
        return tokens[tokens.length - 1]; // 마지막 토큰을 구/군명으로 사용
    }
}
