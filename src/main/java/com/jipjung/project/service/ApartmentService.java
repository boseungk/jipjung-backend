package com.jipjung.project.service;

import com.jipjung.project.controller.dto.request.ApartmentSearchRequest;
import com.jipjung.project.controller.dto.request.FavoriteRequest;
import com.jipjung.project.controller.response.ApartmentResponse;
import com.jipjung.project.controller.response.FavoriteResponse;
import com.jipjung.project.domain.ApartmentTransaction;
import com.jipjung.project.domain.FavoriteApartment;
import com.jipjung.project.repository.ApartmentMapper;
import com.jipjung.project.repository.FavoriteApartmentMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ApartmentService {

    private final ApartmentMapper apartmentMapper;
    private final FavoriteApartmentMapper favoriteApartmentMapper;

    /**
     * 아파트 실거래가 목록 조회 (검색 및 페이징)
     */
    public Map<String, Object> searchApartments(ApartmentSearchRequest request) {
        // 페이지 번호와 크기 (record의 compact constructor에서 기본값 처리됨)
        int page = request.page();
        int size = request.size();
        int offset = page * size;

        // offset을 page 필드로 사용하는 새 request 생성 (MyBatis LIMIT OFFSET용)
        ApartmentSearchRequest offsetRequest = new ApartmentSearchRequest(
                request.legalDong(),
                request.apartmentName(),
                request.dealDateFrom(),
                request.dealDateTo(),
                request.minDealAmount(),
                request.maxDealAmount(),
                offset,  // OFFSET 값
                size
        );

        List<ApartmentTransaction> apartments = apartmentMapper.findAll(offsetRequest);
        int totalCount = apartmentMapper.count(request);

        List<ApartmentResponse> responses = apartments.stream()
                .map(ApartmentResponse::from)
                .collect(Collectors.toList());

        Map<String, Object> result = new HashMap<>();
        result.put("apartments", responses);
        result.put("totalCount", totalCount);
        result.put("page", page);
        result.put("size", size);
        result.put("totalPages", (int) Math.ceil((double) totalCount / size));

        return result;
    }

    /**
     * 아파트 실거래가 상세 조회
     */
    public ApartmentResponse getApartmentById(Long id) {
        ApartmentTransaction apartment = apartmentMapper.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("아파트 실거래가 정보를 찾을 수 없습니다: " + id));
        return ApartmentResponse.from(apartment);
    }

    /**
     * 관심 아파트 등록
     */
    @Transactional
    public FavoriteResponse addFavorite(Long userId, FavoriteRequest request) {
        // 아파트 존재 여부 확인
        apartmentMapper.findById(request.apartmentTransactionId())
                .orElseThrow(() -> new IllegalArgumentException("아파트 실거래가 정보를 찾을 수 없습니다: " + request.apartmentTransactionId()));

        // 중복 체크
        if (favoriteApartmentMapper.existsByUserIdAndApartmentId(userId, request.apartmentTransactionId())) {
            throw new IllegalArgumentException("이미 관심 아파트로 등록되어 있습니다");
        }

        FavoriteApartment favorite = FavoriteApartment.builder()
                .userId(userId)
                .apartmentTransactionId(request.apartmentTransactionId())
                .build();

        favoriteApartmentMapper.insert(favorite);

        // 등록된 관심 아파트 조회 (아파트 정보 포함)
        FavoriteApartment savedFavorite = favoriteApartmentMapper.findById(favorite.getId())
                .orElseThrow(() -> new IllegalArgumentException("관심 아파트 등록에 실패했습니다"));

        return FavoriteResponse.from(savedFavorite);
    }

    /**
     * 내 관심 아파트 목록 조회
     */
    public List<FavoriteResponse> getMyFavorites(Long userId) {
        List<FavoriteApartment> favorites = favoriteApartmentMapper.findByUserId(userId);
        return favorites.stream()
                .map(FavoriteResponse::from)
                .collect(Collectors.toList());
    }

    /**
     * 관심 아파트 삭제
     */
    @Transactional
    public void deleteFavorite(Long userId, Long favoriteId) {
        FavoriteApartment favorite = favoriteApartmentMapper.findById(favoriteId)
                .orElseThrow(() -> new IllegalArgumentException("관심 아파트를 찾을 수 없습니다: " + favoriteId));

        // 본인 확인
        if (!favorite.getUserId().equals(userId)) {
            throw new IllegalArgumentException("본인의 관심 아파트만 삭제할 수 있습니다");
        }

        favoriteApartmentMapper.deleteById(favoriteId);
    }
}
