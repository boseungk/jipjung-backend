package com.jipjung.project.service;

import com.jipjung.project.config.exception.DuplicateResourceException;
import com.jipjung.project.config.exception.ErrorCode;
import com.jipjung.project.config.exception.ResourceNotFoundException;
import com.jipjung.project.controller.dto.request.ApartmentSearchRequest;
import com.jipjung.project.controller.dto.request.FavoriteRequest;
import com.jipjung.project.controller.response.ApartmentDetailResponse;
import com.jipjung.project.controller.response.ApartmentListResponse;
import com.jipjung.project.controller.response.FavoriteResponse;
import com.jipjung.project.domain.Apartment;
import com.jipjung.project.domain.FavoriteApartment;
import com.jipjung.project.repository.ApartmentMapper;
import com.jipjung.project.repository.FavoriteApartmentMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ApartmentService {

    private final ApartmentMapper apartmentMapper;
    private final FavoriteApartmentMapper favoriteApartmentMapper;

    /**
     * 아파트 목록 조회 (검색 및 페이징)
     * 각 아파트의 최신 실거래 1건 포함
     */
    public Map<String, Object> searchApartments(ApartmentSearchRequest request) {
        ApartmentSearchRequest offsetRequest = createOffsetRequest(request);

        List<Apartment> apartments = apartmentMapper.findAllWithLatestDeal(offsetRequest);
        int totalCount = apartmentMapper.count(request);

        List<ApartmentListResponse> responses = apartments.stream()
                .map(apt -> ApartmentListResponse.from(apt, apt.getLatestDeal()))
                .toList();

        return createPageResponse(responses, totalCount, request.page(), request.size());
    }

    /**
     * 아파트 상세 조회
     * 해당 아파트의 모든 실거래 이력 포함
     */
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

        FavoriteApartment favorite = FavoriteApartment.builder()
                .userId(userId)
                .aptSeq(request.aptSeq())
                .build();

        favoriteApartmentMapper.insert(favorite);

        FavoriteApartment savedFavorite = favoriteApartmentMapper.findById(favorite.getId())
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.FAVORITE_NOT_FOUND));

        return FavoriteResponse.from(savedFavorite);
    }

    /**
     * 내 관심 아파트 목록 조회
     */
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
     * offset 요청 객체 생성
     */
    private ApartmentSearchRequest createOffsetRequest(ApartmentSearchRequest request) {
        int offset = request.page() * request.size();
        return new ApartmentSearchRequest(
                request.aptNm(),
                request.umdNm(),
                request.dealDateFrom(),
                request.dealDateTo(),
                request.minDealAmount(),
                request.maxDealAmount(),
                offset,
                request.size()
        );
    }

    /**
     * 페이징 응답 생성
     */
    private Map<String, Object> createPageResponse(List<ApartmentListResponse> data,
                                                    int totalCount, int page, int size) {
        Map<String, Object> response = new HashMap<>();
        response.put("apartments", data);
        response.put("totalCount", totalCount);
        response.put("page", page);
        response.put("size", size);
        response.put("totalPages", (int) Math.ceil((double) totalCount / size));
        return response;
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
}
