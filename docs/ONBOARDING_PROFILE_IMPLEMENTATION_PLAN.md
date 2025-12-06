# Dashboard, Onboarding & Profile API 구현 계획

## 개요

대시보드 조회(`GET /api/users/dashboard`), 온보딩(`POST /api/users/onboarding`), 프로필 수정(`POST /api/users/profile`) API를 구현합니다.

> [!IMPORTANT]
> **대시보드 API는 이미 구현 완료** 상태입니다.
> **온보딩 API와 프로필 수정 API를 신규 구현**하는 데 초점을 맞춥니다.

---

## 현재 구현 상태 분석

| API | 상태 | 비고 |
|-----|------|------|
| `GET /api/users/dashboard` | ✅ 완료 | `DashboardController`, `DashboardService`, `DashboardResponse` |
| `POST /api/users/onboarding` | ❌ 신규 | 온보딩 정보 저장 + DSR 계산 + 레제 반응 |
| `POST /api/users/profile` | ❌ 신규 | 프로필 수정 |

---

## 설계 결정 사항

### 1. 금액 단위: **원 단위**로 통일

> [!NOTE]
> REST_API.md에는 "만원 단위"로 표기되어 있으나, 다음 이유로 **원 단위**를 사용합니다:
> - DSR 계산 시 정밀도 유지
> - 프론트엔드에서 포맷팅하여 표시 (예: `60,000,000원` → `6,000만원`)
> - DB 저장값과 API 입출력 단위 일치로 혼란 방지
>
> **Swagger 문서와 에러 메시지에 "원 단위"를 명시**합니다.

### 2. DSR 등급 체계: **3단계로 통일**

| 등급 | DSR 범위 | 라벨 | 색상 |
|------|----------|------|------|
| SAFE | 0% ~ 30% 미만 | 안전 | GREEN |
| CAUTION | 30% ~ 50% 미만 | 주의 | YELLOW |
| DANGER | 50% 이상 | 위험 | RED |

> [!WARNING]
> **DashboardResponse.java 수정 필요**: 현재 5단계(VERY_SAFE~DANGER)를 3단계로 변경해야 온보딩과 대시보드 UX가 일치합니다.

### 3. Profile 수정 필드

REST_API.md 명세의 요청 필드 테이블(name/birthYear 포함)과 JSON 예시(nickname/annualIncome/existingLoanMonthly)가 불일치합니다.
**JSON 예시 기준**으로 구현하되, 추후 명세 정리 시 반영합니다.
- `nickname`: 닉네임 (2-20자)
- `annualIncome`: 연소득 (원 단위)
- `existingLoanMonthly`: 월 기존 대출 상환액 (원 단위)

---

## Proposed Changes

### 데이터베이스 레이어

#### [MODIFY] new-schema.sql (기본 초기화 스키마)

실제 사용 중인 기본 스키마(`new-schema.sql`)에 `user_preferred_area` 테이블을 추가합니다. 다른 초기화 스크립트(`schema-h2.sql`/`schema-mysql.sql`)를 병행 사용한다면 동일 정의를 반영합니다.

```sql
-- ----------------------------------------------------------------------------
-- 3.14 user_preferred_area - 선호 지역 테이블
-- ----------------------------------------------------------------------------
DROP TABLE IF EXISTS user_preferred_area;

CREATE TABLE user_preferred_area (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    area_name VARCHAR(50) NOT NULL COMMENT '선호 지역명 (강남구, 서초구 등)',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    FOREIGN KEY (user_id) REFERENCES `user`(user_id) ON DELETE CASCADE,
    INDEX idx_user_area (user_id)
);
```

#### [MODIFY] schema-h2.sql (H2용)

`user_preferred_area` 테이블 추가 (기존 테이블 DROP 후 CREATE 섹션에 추가):

```sql
-- ----------------------------------------------------------------------------
-- 3.14 user_preferred_area - 선호 지역 테이블
-- ----------------------------------------------------------------------------
DROP TABLE IF EXISTS user_preferred_area;

CREATE TABLE user_preferred_area (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    area_name VARCHAR(50) NOT NULL COMMENT '선호 지역명 (강남구, 서초구 등)',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    FOREIGN KEY (user_id) REFERENCES `user`(user_id) ON DELETE CASCADE,
    INDEX idx_user_area (user_id)
) COMMENT='선호 지역 테이블';
```

#### [MODIFY] schema-mysql.sql (MySQL용)

동일한 테이블 정의 추가 (MySQL 문법에 맞게):

```sql
-- ----------------------------------------------------------------------------
-- 3.14 user_preferred_area - 선호 지역 테이블
-- ----------------------------------------------------------------------------
DROP TABLE IF EXISTS user_preferred_area;

CREATE TABLE user_preferred_area (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    area_name VARCHAR(50) NOT NULL COMMENT '선호 지역명 (강남구, 서초구 등)',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    FOREIGN KEY (user_id) REFERENCES `user`(user_id) ON DELETE CASCADE,
    INDEX idx_user_area (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='선호 지역 테이블';
```

---

### Repository 레이어

#### [MODIFY] UserMapper.java

온보딩 및 프로필 업데이트를 위한 메서드 추가:

```java
/**
 * 온보딩 정보 업데이트
 * - birthYear, annualIncome, existingLoanMonthly, onboardingCompleted=true 설정
 */
int updateOnboarding(
    @Param("userId") Long userId,
    @Param("birthYear") Integer birthYear,
    @Param("annualIncome") Long annualIncome,
    @Param("existingLoanMonthly") Long existingLoanMonthly
);

/**
 * 프로필 정보 업데이트
 */
int updateProfile(
    @Param("userId") Long userId,
    @Param("nickname") String nickname,
    @Param("annualIncome") Long annualIncome,
    @Param("existingLoanMonthly") Long existingLoanMonthly
);
```

---

#### [MODIFY] UserMapper.xml

UPDATE 쿼리 추가:

```xml
<!-- 온보딩 정보 업데이트 -->
<update id="updateOnboarding">
    UPDATE `user`
    SET birth_year = #{birthYear},
        annual_income = #{annualIncome},
        existing_loan_monthly = #{existingLoanMonthly},
        onboarding_completed = true,
        updated_at = CURRENT_TIMESTAMP
    WHERE user_id = #{userId}
      AND is_deleted = false
</update>

<!-- 프로필 정보 업데이트 -->
<update id="updateProfile">
    UPDATE `user`
    SET nickname = #{nickname},
        annual_income = #{annualIncome},
        existing_loan_monthly = #{existingLoanMonthly},
        updated_at = CURRENT_TIMESTAMP
    WHERE user_id = #{userId}
      AND is_deleted = false
</update>
```

---

#### [NEW] UserPreferredAreaMapper.java

선호 지역 관리 Mapper:

```java
package com.jipjung.project.repository;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface UserPreferredAreaMapper {

    /**
     * 사용자의 선호 지역 일괄 삽입
     */
    int insertAll(@Param("userId") Long userId, @Param("areas") List<String> areas);

    /**
     * 사용자의 선호 지역 전체 삭제
     */
    int deleteByUserId(@Param("userId") Long userId);

    /**
     * 사용자의 선호 지역 목록 조회
     */
    List<String> findByUserId(@Param("userId") Long userId);
}
```

---

#### [NEW] UserPreferredAreaMapper.xml

```xml
<?xml version="1.0" encoding="UTF-8" ?>
<!DOCTYPE mapper
        PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN"
        "http://mybatis.org/dtd/mybatis-3-mapper.dtd">

<mapper namespace="com.jipjung.project.repository.UserPreferredAreaMapper">

    <!-- 선호 지역 일괄 삽입 -->
    <insert id="insertAll">
        INSERT INTO user_preferred_area (user_id, area_name)
        VALUES
        <foreach collection="areas" item="area" separator=",">
            (#{userId}, #{area})
        </foreach>
    </insert>

    <!-- 사용자의 선호 지역 전체 삭제 -->
    <delete id="deleteByUserId">
        DELETE FROM user_preferred_area
        WHERE user_id = #{userId}
    </delete>

    <!-- 사용자의 선호 지역 목록 조회 -->
    <select id="findByUserId" resultType="String">
        SELECT area_name
        FROM user_preferred_area
        WHERE user_id = #{userId}
        ORDER BY id
    </select>

</mapper>
```

---

### DTO 레이어 (Request)

#### [NEW] OnboardingRequest.java

온보딩 요청 DTO (**preferredAreas 세부 검증 포함**):

```java
package com.jipjung.project.controller.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

import java.util.List;
import java.util.stream.Collectors;

@Schema(description = "온보딩 정보 저장 요청")
public record OnboardingRequest(
        @Schema(description = "출생년도", example = "1995", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "출생년도는 필수입니다")
        @Min(value = 1900, message = "출생년도는 1900 이상이어야 합니다")
        @Max(value = 2010, message = "출생년도는 2010 이하여야 합니다")
        Integer birthYear,

        @Schema(description = "연소득 (원 단위)", example = "50000000", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "연소득은 필수입니다")
        @Min(value = 0, message = "연소득은 0 이상이어야 합니다")
        Long annualIncome,

        @Schema(description = "월 기존 대출 상환액 (원 단위)", example = "500000", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "월 기존 대출 상환액은 필수입니다")
        @Min(value = 0, message = "월 기존 대출 상환액은 0 이상이어야 합니다")
        Long existingLoanMonthly,

        @Schema(description = "선호 지역 배열 (각 항목 50자 이내, 최대 10개, 중복 불가)", 
                example = "[\"강남구\", \"서초구\", \"송파구\"]", 
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotEmpty(message = "선호 지역은 최소 1개 이상이어야 합니다")
        @Size(max = 10, message = "선호 지역은 최대 10개까지 선택 가능합니다")
        List<String> preferredAreas
) {
    /**
     * preferredAreas를 정제하여 반환
     * - trim 처리
     * - 빈 문자열 제거
     * - 중복 제거
     * - 50자 초과 항목 제거
     */
    public List<String> getSanitizedPreferredAreas() {
        if (preferredAreas == null) {
            return List.of();
        }
        return preferredAreas.stream()
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .filter(s -> s.length() <= 50)
                .distinct()
                .collect(Collectors.toList());
    }
}
```

---

#### [NEW] ProfileUpdateRequest.java

프로필 수정 요청 DTO:

```java
package com.jipjung.project.controller.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(description = "프로필 수정 요청")
public record ProfileUpdateRequest(
        @Schema(description = "닉네임 (2-20자)", example = "건축왕2세", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "닉네임은 필수입니다")
        @Size(min = 2, max = 20, message = "닉네임은 2자 이상 20자 이하여야 합니다")
        String nickname,

        @Schema(description = "연소득 (원 단위)", example = "60000000", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "연소득은 필수입니다")
        @Min(value = 0, message = "연소득은 0 이상이어야 합니다")
        Long annualIncome,

        @Schema(description = "월 기존 대출 상환액 (원 단위)", example = "400000", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "월 기존 대출 상환액은 필수입니다")
        @Min(value = 0, message = "월 기존 대출 상환액은 0 이상이어야 합니다")
        Long existingLoanMonthly
) {}
```

---

### DTO 레이어 (Response)

#### [NEW] OnboardingResponse.java

온보딩 응답 DTO (**3단계 DSR 등급**):

```java
package com.jipjung.project.controller.dto.response;

import com.jipjung.project.domain.User;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "온보딩 정보 저장 응답")
public record OnboardingResponse(
        @Schema(description = "사용자 정보") UserInfo user,
        @Schema(description = "DSR 분석 결과") DsrResult dsrResult
) {
    /**
     * 팩토리 메서드
     */
    public static OnboardingResponse from(User user, DsrResult dsrResult) {
        return new OnboardingResponse(
                UserInfo.from(user),
                dsrResult
        );
    }

    // ============================
    // Nested Records
    // ============================

    @Schema(description = "사용자 정보")
    public record UserInfo(
            @Schema(description = "사용자 ID") Long id,
            @Schema(description = "닉네임") String nickname,
            @Schema(description = "온보딩 완료 여부") boolean onboardingCompleted
    ) {
        public static UserInfo from(User user) {
            return new UserInfo(
                    user.getId(),
                    user.getNickname(),
                    true  // 온보딩 완료 후 항상 true
            );
        }
    }

    @Schema(description = "DSR 분석 결과")
    public record DsrResult(
            @Schema(description = "DSR 비율 (%)", example = "12.0") double dsrRatio,
            @Schema(description = "등급 (SAFE, CAUTION, DANGER)", example = "SAFE") String grade,
            @Schema(description = "예상 최대 대출 가능액 (원)", example = "400000000") long maxLoanAmount
    ) {
        /**
         * DSR 계산 팩토리 메서드 (3단계 등급)
         * 
         * @param monthlyIncome 월 소득 (원)
         * @param existingLoanMonthly 기존 월 대출 상환액 (원)
         * @return DSR 분석 결과
         */
        public static DsrResult calculate(long monthlyIncome, long existingLoanMonthly) {
            // DSR 비율 계산 (기존 대출 상환액 / 월 소득 * 100)
            double dsrRatio = monthlyIncome > 0 
                    ? Math.round((existingLoanMonthly * 1000.0) / monthlyIncome) / 10.0 
                    : 0.0;

            // 3단계 등급 결정
            String grade;
            if (dsrRatio < 30) {
                grade = "SAFE";       // 0% ~ 30% 미만
            } else if (dsrRatio < 50) {
                grade = "CAUTION";    // 30% ~ 50% 미만
            } else {
                grade = "DANGER";     // 50% 이상
            }

            // 추가 대출 가능 상환액 (월 소득의 40% - 기존 상환액)
            long availableMonthlyRepayment = Math.max(0, (long)(monthlyIncome * 0.4) - existingLoanMonthly);
            // 대출 가능액 추정 (연이율 4%, 30년 상환 가정)
            // 간단 계산: 월 상환액 * 12 * 30 * 0.7 (이자 감안 보수적 추정)
            long maxLoanAmount = availableMonthlyRepayment * 12 * 30 * 7 / 10;

            return new DsrResult(dsrRatio, grade, maxLoanAmount);
        }
    }

}
```

---

#### [NEW] ProfileUpdateResponse.java

프로필 수정 응답 DTO:

```java
package com.jipjung.project.controller.dto.response;

import com.jipjung.project.domain.User;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "프로필 수정 응답")
public record ProfileUpdateResponse(
        @Schema(description = "수정된 사용자 정보")
        UserInfo user
) {
    public static ProfileUpdateResponse from(User user) {
        return new ProfileUpdateResponse(UserInfo.from(user));
    }

    @Schema(description = "사용자 정보")
    public record UserInfo(
            @Schema(description = "사용자 ID") Long id,
            @Schema(description = "이메일") String email,
            @Schema(description = "닉네임") String nickname,
            @Schema(description = "연소득 (원)") Long annualIncome,
            @Schema(description = "월 기존 대출 상환액 (원)") Long existingLoanMonthly,
            @Schema(description = "수정 일시") LocalDateTime updatedAt
    ) {
        public static UserInfo from(User user) {
            return new UserInfo(
                    user.getId(),
                    user.getEmail(),
                    user.getNickname(),
                    user.getAnnualIncome(),
                    user.getExistingLoanMonthly(),
                    user.getUpdatedAt()
            );
        }
    }
}
```

---

### Service 레이어

#### [NEW] UserService.java

사용자 관련 비즈니스 로직 (**정제된 preferredAreas 사용**):

```java
package com.jipjung.project.service;

import com.jipjung.project.controller.dto.request.OnboardingRequest;
import com.jipjung.project.controller.dto.request.ProfileUpdateRequest;
import com.jipjung.project.controller.dto.response.OnboardingResponse;
import com.jipjung.project.controller.dto.response.OnboardingResponse.DsrResult;
import com.jipjung.project.controller.dto.response.ProfileUpdateResponse;
import com.jipjung.project.domain.User;
import com.jipjung.project.global.exception.ErrorCode;
import com.jipjung.project.global.exception.ResourceNotFoundException;
import com.jipjung.project.repository.UserMapper;
import com.jipjung.project.repository.UserPreferredAreaMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserMapper userMapper;
    private final UserPreferredAreaMapper userPreferredAreaMapper;

    /**
     * 온보딩 정보 저장
     */
    @Transactional
    public OnboardingResponse saveOnboarding(Long userId, OnboardingRequest request) {
        // 1. 사용자 존재 여부 확인
        User user = findUserOrThrow(userId);

        // 2. 온보딩 정보 업데이트
        int updatedRows = userMapper.updateOnboarding(
                userId,
                request.birthYear(),
                request.annualIncome(),
                request.existingLoanMonthly()
        );

        if (updatedRows == 0) {
            throw new ResourceNotFoundException(ErrorCode.USER_NOT_FOUND);
        }

        // 3. 선호 지역 저장 (정제 후 삽입: trim, 빈값/길이초과/중복 제거)
        userPreferredAreaMapper.deleteByUserId(userId);
        List<String> sanitizedAreas = request.getSanitizedPreferredAreas();
        if (!sanitizedAreas.isEmpty()) {
            userPreferredAreaMapper.insertAll(userId, sanitizedAreas);
        }

        // 4. 업데이트된 사용자 조회
        User updatedUser = userMapper.findById(userId);

        // 5. DSR 계산
        long monthlyIncome = updatedUser.getMonthlyIncome();
        long existingLoanMonthly = request.existingLoanMonthly();
        DsrResult dsrResult = DsrResult.calculate(monthlyIncome, existingLoanMonthly);

        log.info("Onboarding completed. userId: {}, dsrRatio: {}%, grade: {}, preferredAreas: {}",
                userId, dsrResult.dsrRatio(), dsrResult.grade(), sanitizedAreas.size());

        return OnboardingResponse.from(updatedUser, dsrResult);
    }

    /**
     * 프로필 수정
     */
    @Transactional
    public ProfileUpdateResponse updateProfile(Long userId, ProfileUpdateRequest request) {
        // 1. 사용자 존재 여부 확인
        findUserOrThrow(userId);

        // 2. 프로필 업데이트 수행
        int updatedRows = userMapper.updateProfile(
                userId,
                request.nickname(),
                request.annualIncome(),
                request.existingLoanMonthly()
        );

        if (updatedRows == 0) {
            throw new ResourceNotFoundException(ErrorCode.USER_NOT_FOUND);
        }

        // 3. 업데이트된 사용자 정보 조회
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
}
```

---

### Controller 레이어

#### [NEW] UserController.java

사용자 API 컨트롤러 (**ApiResponse.success() 패턴 사용**):

```java
package com.jipjung.project.controller;

import com.jipjung.project.controller.dto.request.OnboardingRequest;
import com.jipjung.project.controller.dto.request.ProfileUpdateRequest;
import com.jipjung.project.controller.dto.response.OnboardingResponse;
import com.jipjung.project.controller.dto.response.ProfileUpdateResponse;
import com.jipjung.project.global.response.ApiResponse;
import com.jipjung.project.service.CustomUserDetails;
import com.jipjung.project.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Tag(name = "사용자", description = "온보딩 및 프로필 관리 API")
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @Operation(
            summary = "온보딩 정보 저장",
            description = """
                    사용자의 온보딩 정보를 저장합니다.
                    
                    **저장 정보:**
                    - 출생년도
                    - 연소득 (원 단위)
                    - 월 기존 대출 상환액 (원 단위)
                    - 선호 지역 (배열, 최대 10개, 각 50자 이내)
                    
                    **응답 정보:**
                    - 업데이트된 사용자 정보
                    - DSR 분석 결과 (SAFE/CAUTION/DANGER 3단계)
                    - 레제의 첫 반응 (기분, 대사)
                    """,
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "온보딩 정보 저장 완료",
                    content = @Content(schema = @Schema(implementation = OnboardingResponse.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "유효성 검증 실패"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "인증 필요"
            )
    })
    @PostMapping("/onboarding")
    public ResponseEntity<ApiResponse<OnboardingResponse>> saveOnboarding(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody OnboardingRequest request
    ) {
        OnboardingResponse response = userService.saveOnboarding(
                userDetails.getId(),
                request
        );
        return ApiResponse.success(response);
    }

    @Operation(
            summary = "프로필 수정",
            description = """
                    사용자 프로필 정보를 수정합니다.
                    
                    **수정 가능 필드:**
                    - 닉네임 (2-20자)
                    - 연소득 (원 단위)
                    - 월 기존 대출 상환액 (원 단위)
                    """,
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "수정 성공",
                    content = @Content(schema = @Schema(implementation = ProfileUpdateResponse.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "유효성 검증 실패"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "인증 필요"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "사용자를 찾을 수 없음"
            )
    })
    @PostMapping("/profile")
    public ResponseEntity<ApiResponse<ProfileUpdateResponse>> updateProfile(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody ProfileUpdateRequest request
    ) {
        ProfileUpdateResponse response = userService.updateProfile(
                userDetails.getId(),
                request
        );
        return ApiResponse.success(response);
    }
}
```

---

### 대시보드 DSR 등급 수정

#### [MODIFY] DashboardResponse.java

5단계 → 3단계로 변경:

```java
// 기존 코드 (262-289줄)
private enum DsrGrade {
    VERY_SAFE("매우 안전", "GREEN", 0, 20),
    SAFE("안전", "BLUE", 20, 30),
    MODERATE("보통", "YELLOW", 30, 40),
    CAUTION("주의", "ORANGE", 40, 50),
    DANGER("위험", "RED", 50, 100);
    // ...
}

// 변경 후 (3단계)
private enum DsrGrade {
    SAFE("안전", "GREEN", 0, 30),
    CAUTION("주의", "YELLOW", 30, 50),
    DANGER("위험", "RED", 50, 100);

    final String label;
    final String color;
    final int minPercent;
    final int maxPercent;

    DsrGrade(String label, String color, int minPercent, int maxPercent) {
        this.label = label;
        this.color = color;
        this.minPercent = minPercent;
        this.maxPercent = maxPercent;
    }

    static DsrGrade fromPercent(double percent) {
        for (DsrGrade grade : values()) {
            if (percent >= grade.minPercent && percent < grade.maxPercent) {
                return grade;
            }
        }
        return DANGER;
    }
}
```

---

## 파일 변경 요약

| 작업 | 파일 | 설명 |
|------|------|------|
| **[MODIFY]** | `new-schema.sql` | `user_preferred_area` 테이블 추가 |
| **[MODIFY]** | `schema-h2.sql` | `user_preferred_area` 테이블 추가 |
| **[MODIFY]** | `schema-mysql.sql` | `user_preferred_area` 테이블 추가 |
| **[MODIFY]** | `UserMapper.java` | `updateOnboarding()`, `updateProfile()` 추가 |
| **[MODIFY]** | `UserMapper.xml` | UPDATE 쿼리 2개 추가 |
| **[NEW]** | `UserPreferredAreaMapper.java` | 선호 지역 CRUD Mapper |
| **[NEW]** | `UserPreferredAreaMapper.xml` | 선호 지역 쿼리 |
| **[NEW]** | `OnboardingRequest.java` | 온보딩 요청 DTO (preferredAreas 정제 메서드 포함) |
| **[NEW]** | `ProfileUpdateRequest.java` | 프로필 수정 요청 DTO |
| **[NEW]** | `OnboardingResponse.java` | 온보딩 응답 DTO (3단계 DSR) |
| **[NEW]** | `ProfileUpdateResponse.java` | 프로필 수정 응답 DTO |
| **[NEW]** | `UserService.java` | 사용자 비즈니스 로직 서비스 |
| **[NEW]** | `UserController.java` | 사용자 API 컨트롤러 (ApiResponse.success 패턴) |
| **[MODIFY]** | `DashboardResponse.java` | DSR 등급 5단계 → 3단계 변경 |

---

## 에러 처리

| 상황 | HTTP 상태 | ErrorCode | 메시지 |
|------|-----------|-----------|--------|
| 유효성 검증 실패 | 400 | `INVALID_INPUT_VALUE` | 필드별 상세 오류 메시지 |
| 인증 토큰 없음/만료 | 401 | `UNAUTHORIZED` | "인증이 필요합니다" |
| 사용자 찾을 수 없음 | 404 | `USER_NOT_FOUND` | "사용자를 찾을 수 없습니다" |

---

## Verification Plan

### 자동화 테스트

1. **빌드 검증**
   ```bash
   mvn clean compile
   ```

2. **Swagger UI 확인**
   - `http://localhost:8080/swagger-ui.html`
   - `/api/users/onboarding`, `/api/users/profile` 엔드포인트 확인
   - 금액 필드 설명이 "원 단위"로 표시되는지 확인

### 수동 검증 (Postman / curl)

1. **온보딩 성공 케이스**
   ```bash
   curl -X POST http://localhost:8080/api/users/onboarding \
     -H "Authorization: Bearer {accessToken}" \
     -H "Content-Type: application/json" \
     -d '{
       "birthYear": 1995,
       "annualIncome": 50000000,
       "existingLoanMonthly": 500000,
       "preferredAreas": ["강남구", "서초구", "송파구"]
     }'
   ```

2. **preferredAreas 정제 검증** (빈값/중복/공백 처리)
   ```bash
   curl -X POST http://localhost:8080/api/users/onboarding \
     -H "Authorization: Bearer {accessToken}" \
     -H "Content-Type: application/json" \
     -d '{
       "birthYear": 1995,
       "annualIncome": 50000000,
       "existingLoanMonthly": 500000,
       "preferredAreas": ["강남구", "  서초구  ", "", "강남구", "송파구"]
     }'
   ```
   → 저장되는 값: `["강남구", "서초구", "송파구"]` (중복/빈값 제거, trim 처리)

3. **프로필 수정 성공 케이스**
   ```bash
   curl -X POST http://localhost:8080/api/users/profile \
     -H "Authorization: Bearer {accessToken}" \
     -H "Content-Type: application/json" \
     -d '{
       "nickname": "건축왕2세",
       "annualIncome": 60000000,
       "existingLoanMonthly": 400000
     }'
   ```

4. **DSR 등급 일관성 검증**
   - 온보딩 완료 후 응답의 `dsrResult.grade`
   - 대시보드 조회 시 `dsr.gradeLabel`
   - 두 값이 동일한 3단계 체계(SAFE/CAUTION/DANGER)를 따르는지 확인

---

## 구현 순서

1. `schema-h2.sql`, `schema-mysql.sql` - `user_preferred_area` 테이블 추가
2. `UserMapper.java/xml` - 온보딩, 프로필 UPDATE 쿼리 추가
3. `UserPreferredAreaMapper.java/xml` - 선호 지역 CRUD 추가
4. `OnboardingRequest.java`, `ProfileUpdateRequest.java` - 요청 DTO 생성
5. `OnboardingResponse.java`, `ProfileUpdateResponse.java` - 응답 DTO 생성
6. `UserService.java` - 비즈니스 로직 서비스 생성
7. `UserController.java` - API 컨트롤러 생성
8. `DashboardResponse.java` - DSR 등급 3단계로 수정
9. 빌드 및 테스트 수행
