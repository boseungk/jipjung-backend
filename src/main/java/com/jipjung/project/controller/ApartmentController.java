package com.jipjung.project.controller;

import com.jipjung.project.config.exception.ApiResponse;
import com.jipjung.project.controller.dto.request.ApartmentSearchRequest;
import com.jipjung.project.controller.dto.request.FavoriteRequest;
import com.jipjung.project.controller.dto.response.ApartmentResponse;
import com.jipjung.project.controller.dto.response.FavoriteResponse;
import com.jipjung.project.service.ApartmentService;
import com.jipjung.project.service.CustomUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Tag(name = "아파트", description = "아파트 실거래가 조회 및 관심 아파트 관리 API")
@RestController
@RequestMapping("/api/apartments")
@RequiredArgsConstructor
public class ApartmentController {

    private final ApartmentService apartmentService;

    @Operation(
            summary = "아파트 실거래가 목록 조회",
            description = "검색 조건에 따라 아파트 실거래가 목록을 조회합니다.\n\n" +
                    "**검색 조건**: 법정동, 아파트명, 거래일, 거래금액 등으로 필터링 가능\n\n" +
                    "**페이징**: page와 size 파라미터로 페이징 처리"
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "조회 성공",
                    content = @Content(schema = @Schema(implementation = Map.class))
            )
    })
    @GetMapping
    public ApiResponse<Map<String, Object>> searchApartments(
            @Parameter(description = "검색 조건") ApartmentSearchRequest request) {
        Map<String, Object> result = apartmentService.searchApartments(request);
        return ApiResponse.success(result);
    }

    @Operation(
            summary = "아파트 실거래가 상세 조회",
            description = "특정 아파트 실거래가의 상세 정보를 조회합니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "조회 성공",
                    content = @Content(schema = @Schema(implementation = ApartmentResponse.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "아파트 실거래가 정보를 찾을 수 없음"
            )
    })
    @GetMapping("/{id}")
    public ApiResponse<ApartmentResponse> getApartmentById(
            @Parameter(description = "아파트 실거래가 ID") @PathVariable Long id) {
        ApartmentResponse response = apartmentService.getApartmentById(id);
        return ApiResponse.success(response);
    }

    @Operation(
            summary = "관심 아파트 등록",
            description = "아파트를 관심 목록에 추가합니다.\n\n" +
                    "**인증 필요**: JWT 토큰 필요 (Authorization 헤더에 Bearer 토큰)",
            security = @SecurityRequirement(name = "Bearer Authentication")
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "201",
                    description = "등록 성공",
                    content = @Content(schema = @Schema(implementation = FavoriteResponse.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "이미 등록된 관심 아파트 또는 유효하지 않은 요청"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "인증 실패"
            )
    })
    @PostMapping("/favorites")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<FavoriteResponse> addFavorite(
            @Valid @RequestBody FavoriteRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        Long userId = userDetails.getUser().getId();
        FavoriteResponse response = apartmentService.addFavorite(userId, request);
        return ApiResponse.success(response);
    }

    @Operation(
            summary = "내 관심 아파트 목록 조회",
            description = "현재 로그인한 사용자의 관심 아파트 목록을 조회합니다.\n\n" +
                    "**인증 필요**: JWT 토큰 필요",
            security = @SecurityRequirement(name = "Bearer Authentication")
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "조회 성공",
                    content = @Content(schema = @Schema(implementation = List.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "인증 실패"
            )
    })
    @GetMapping("/favorites")
    public ApiResponse<List<FavoriteResponse>> getMyFavorites(
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        Long userId = userDetails.getUser().getId();
        List<FavoriteResponse> favorites = apartmentService.getMyFavorites(userId);
        return ApiResponse.success(favorites);
    }

    @Operation(
            summary = "관심 아파트 삭제",
            description = "관심 아파트 목록에서 삭제합니다.\n\n" +
                    "**인증 필요**: JWT 토큰 필요\n\n" +
                    "**권한**: 본인의 관심 아파트만 삭제 가능",
            security = @SecurityRequirement(name = "Bearer Authentication")
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "삭제 성공"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "본인의 관심 아파트가 아님"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "인증 실패"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "관심 아파트를 찾을 수 없음"
            )
    })
    @DeleteMapping("/favorites/{id}")
    public ApiResponse<Void> deleteFavorite(
            @Parameter(description = "관심 아파트 ID") @PathVariable Long id,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        Long userId = userDetails.getUser().getId();
        apartmentService.deleteFavorite(userId, id);
        return ApiResponse.success();
    }
}
