package com.jipjung.project.service;

import com.jipjung.project.controller.dto.request.OnboardingRequest;
import com.jipjung.project.controller.dto.request.ProfileUpdateRequest;
import com.jipjung.project.controller.dto.response.OnboardingResponse;
import com.jipjung.project.controller.dto.response.ProfileUpdateResponse;
import com.jipjung.project.domain.User;
import com.jipjung.project.dsr.DsrResult;
import com.jipjung.project.global.exception.ErrorCode;
import com.jipjung.project.global.exception.InvalidPasswordException;
import com.jipjung.project.global.exception.ResourceNotFoundException;
import com.jipjung.project.repository.UserMapper;
import com.jipjung.project.repository.UserPreferredAreaMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

/**
 * 사용자 서비스
 * - 온보딩 및 프로필 관리 로직
 * - Phase 2: DSR 캐시 무효화 정책 포함
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserMapper userMapper;
    private final UserPreferredAreaMapper userPreferredAreaMapper;
    private final DsrService dsrService;
    private final PasswordEncoder passwordEncoder;

    /**
     * 온보딩 정보 저장
     */
    @Transactional
    public OnboardingResponse saveOnboarding(Long userId, OnboardingRequest request) {
        // 1. 사용자 존재 여부 확인
        User user = findUserOrThrow(userId);

        // 2. 온보딩 정보 업데이트 (currentAssets 포함)
        int updatedRows = userMapper.updateOnboarding(
                userId,
                request.birthYear(),
                request.annualIncome(),
                request.existingLoanMonthly(),
                request.currentAssets()  // Phase 2: 현재 자산 추가
        );

        if (updatedRows == 0) {
            throw new ResourceNotFoundException(ErrorCode.USER_NOT_FOUND);
        }

        // 3. 선호 지역 저장 (정제 후 삽입: trim, 빈값/길이초과/중복 제거)
        List<String> sanitizedAreas = sanitizePreferredAreas(request);
        userPreferredAreaMapper.deleteByUserId(userId);
        userPreferredAreaMapper.insertAll(userId, sanitizedAreas);

        // 4. 업데이트된 사용자 조회
        User updatedUser = userMapper.findById(userId);

        // 5. DSR 계산 (DsrService로 위임)
        DsrResult dsrResult = dsrService.calculateLiteDsr(updatedUser);

        // 6. DTO 변환 (LITE 모드용 - 등급 매핑)
        OnboardingResponse.DsrResult liteResult = new OnboardingResponse.DsrResult(
                dsrResult.currentDsrPercent(),
                mapGradeForOnboarding(dsrResult.grade()),
                dsrResult.maxLoanAmount()
        );

        log.info("Onboarding completed. userId: {}, dsrRatio: {}%, grade: {}, currentAssets: {}, preferredAreas: {}",
                userId, dsrResult.currentDsrPercent(), dsrResult.grade(), request.currentAssets(), sanitizedAreas.size());

        return OnboardingResponse.from(updatedUser, liteResult, sanitizedAreas);
    }

    /**
     * 프로필 수정
     * <p>
     * Phase 2: 연소득/부채 변경 시 DSR 캐시 무효화
     */
    @Transactional
    public ProfileUpdateResponse updateProfile(Long userId, ProfileUpdateRequest request) {
        // 1. 사용자 존재 여부 확인
        User user = findUserOrThrow(userId);

        // 2. Phase 2: 연소득 또는 부채가 변경된 경우 DSR 캐시 무효화
        boolean incomeChanged = !Objects.equals(user.getAnnualIncome(), request.annualIncome());
        boolean debtChanged = !Objects.equals(user.getExistingLoanMonthly(), request.existingLoanMonthly());

        if (incomeChanged || debtChanged) {
            userMapper.invalidateDsrCache(userId);
            log.info("DSR cache invalidated due to profile change. userId: {}, incomeChanged: {}, debtChanged: {}",
                    userId, incomeChanged, debtChanged);
        }

        // 3. 프로필 업데이트 수행
        int updatedRows = userMapper.updateProfile(
                userId,
                request.nickname(),
                request.annualIncome(),
                request.existingLoanMonthly()
        );

        if (updatedRows == 0) {
            throw new ResourceNotFoundException(ErrorCode.USER_NOT_FOUND);
        }

        // 4. 업데이트된 사용자 정보 조회
        User updatedUser = userMapper.findById(userId);

        log.info("Profile updated. userId: {}, nickname: {}", userId, request.nickname());

        return ProfileUpdateResponse.from(updatedUser);
    }

    private User findUserOrThrow(Long userId) {
        User user = userMapper.findById(userId);
        if (user == null) {
            throw new ResourceNotFoundException(ErrorCode.USER_NOT_FOUND);
        }
        return user;
    }

    private List<String> sanitizePreferredAreas(OnboardingRequest request) {
        List<String> sanitizedAreas = request.getSanitizedPreferredAreas().stream()
                .map(this::extractGugunName)
                .filter(area -> area != null && !area.isBlank())
                .toList();
        if (sanitizedAreas.isEmpty()) {
            throw new IllegalArgumentException("선호 지역은 최소 1개 이상이어야 합니다");
        }
        return sanitizedAreas;
    }

    /**
     * DsrResult 등급을 온보딩 응답 포맷으로 변환
     * <p>
     * SAFE → SAFE, WARNING → CAUTION, RESTRICTED → DANGER
     */
    private String mapGradeForOnboarding(String grade) {
        return switch (grade) {
            case DsrResult.GRADE_SAFE -> "SAFE";
            case DsrResult.GRADE_WARNING -> "CAUTION";
            case DsrResult.GRADE_RESTRICTED -> "DANGER";
            default -> grade;
        };
    }

    /**
     * 입력 문자열에서 구/군명을 추출 (공백 기준 마지막 토큰)
     */
    private String extractGugunName(String regionName) {
        if (regionName == null) {
            return null;
        }
        String normalized = regionName.trim();
        if (normalized.isEmpty()) {
            return normalized;
        }
        String[] tokens = normalized.split("\\s+");
        return tokens[tokens.length - 1];
    }

    /**
     * 회원탈퇴 (Soft Delete)
     * 
     * @param email 탈퇴할 사용자 이메일
     * @param password 비밀번호 확인용
     */
    @Transactional
    public void deleteAccount(String email, String password) {
        User user = userMapper.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.USER_NOT_FOUND));
        
        // 비밀번호 확인
        if (!passwordEncoder.matches(password, user.getPassword())) {
            throw new InvalidPasswordException();
        }
        
        // Soft Delete
        int deletedRows = userMapper.softDeleteUser(user.getId());
        if (deletedRows == 0) {
            throw new ResourceNotFoundException(ErrorCode.USER_NOT_FOUND);
        }
        
        log.info("Account deleted (soft). userId: {}, email: {}", user.getId(), email);
    }
}
