# JipJung Backend Testing Implementation Plan (BDD 스타일)

## Overview
Comprehensive testing plan focusing on **Phase 0-2**: Test infrastructure, security (JWT/authentication), and core business logic. Target: **70%+ code coverage** for critical components.

**Test Style**: **BDD (Behavior-Driven Development)** ✨
- Given-When-Then 패턴
- @DisplayName 한글 설명
- Mockito BDDMockito 사용
- AssertJ 활용

**Priorities**: JWT security, signup logic, apartment favorites, MyBatis queries
**Estimated Effort**: 12-15 hours
**Total Test Classes**: ~11 classes, ~130 test cases

### BDD 스타일 핵심 특징
```java
@Test
@DisplayName("이메일이 중복되면 회원가입이 실패한다")
void 이메일이_중복되면_회원가입이_실패한다() {
    // Given (주어진 상황)
    given(userMapper.existsByEmail(anyString())).willReturn(true);

    // When (특정 행동)
    SignupRequest request = new SignupRequest("test@email.com", "닉네임", "Test123!@");

    // Then (결과 검증)
    assertThatThrownBy(() -> authService.signup(request))
        .isInstanceOf(DuplicateEmailException.class);
    then(userMapper).should(never()).insertUser(any());
}
```

---

## Phase 0: Test Infrastructure Setup ⚡ CRITICAL

**Must complete first** - All other phases depend on this foundation.

### Files to Create

#### 1. Test Configuration
**`src/test/resources/application-test.properties`**
```properties
spring.profiles.active=test
spring.datasource.url=jdbc:h2:mem:testdb;MODE=MySQL
spring.datasource.username=sa
spring.datasource.password=

mybatis.mapper-locations=classpath:mapper/**/*.xml
mybatis.type-aliases-package=com.jipjung.project.domain
mybatis.configuration.map-underscore-to-camel-case=true

jwt.secret=TestSecretKey1234567890123456789012345
jwt.access-token-expiration=3600000

logging.level.com.jipjung.project=DEBUG

spring.sql.init.mode=always
spring.sql.init.schema-locations=classpath:schema-h2.sql
spring.sql.init.data-locations=classpath:data-h2.sql
```

#### 2. Test Base Classes

**`src/test/java/com/jipjung/project/support/TestFixtures.java`**
- Factory methods for test data (User, Apartment, SignupRequest)
- Valid/invalid request builders
- JWT token helpers

**`src/test/java/com/jipjung/project/support/IntegrationTestBase.java`**
```java
@SpringBootTest
@ActiveProfiles("test")
@Transactional
public abstract class IntegrationTestBase {
    @Autowired protected UserMapper userMapper;
    @Autowired protected ApartmentMapper apartmentMapper;
    @Autowired protected FavoriteApartmentMapper favoriteApartmentMapper;
    @Autowired protected PasswordEncoder passwordEncoder;

    protected User createTestUser(String email) { /* ... */ }
    protected String createValidJwtToken(String email) { /* ... */ }
}
```

**`src/test/java/com/jipjung/project/support/ControllerTestBase.java`**
```java
@WebMvcTest
@Import(SecurityTestConfig.class)
public abstract class ControllerTestBase {
    @Autowired protected MockMvc mockMvc;
    @Autowired protected ObjectMapper objectMapper;

    protected ResultActions performPostRequest(String url, Object body) { /* ... */ }
    protected ResultActions performAuthenticatedRequest(String url, String token) { /* ... */ }
}
```

---

## Phase 1: Security & Authentication Layer 🔐 HIGH PRIORITY

### 1.1 JwtProviderTest.java ⭐ CRITICAL
**Location**: `src/test/java/com/jipjung/project/config/jwt/JwtProviderTest.java`
**Type**: Unit Test (BDD 스타일)
**Dependency**: `/src/main/java/com/jipjung/project/config/jwt/JwtProvider.java`

**Test Cases** (22 tests):

**토큰 생성 (Token Creation)** (5 tests)
- `유효한_이메일로_액세스_토큰을_생성할_수_있다()` / `shouldCreateAccessTokenWhenEmailIsValid()`
- `이메일이_null이면_토큰_생성에_실패한다()` / `shouldFailToCreateTokenWhenEmailIsNull()`
- `이메일이_빈_문자열이면_토큰_생성에_실패한다()` / `shouldFailToCreateTokenWhenEmailIsEmpty()`
- `동일한_이메일로_생성한_토큰은_매번_다르다()` / `shouldGenerateUniqueTokensForSameEmail()`
- `생성된_토큰에는_이메일_클레임이_포함된다()` / `shouldContainEmailClaimInGeneratedToken()`

**토큰 추출 (Token Extraction)** (4 tests)
- `Bearer_토큰에서_토큰_값을_추출할_수_있다()` / `shouldExtractTokenFromBearerHeader()`
- `Bearer_접두사가_없으면_null을_반환한다()` / `shouldReturnNullWhenBearerPrefixIsMissing()`
- `헤더가_null이면_null을_반환한다()` / `shouldReturnNullWhenHeaderIsNull()`
- `헤더가_빈_문자열이면_null을_반환한다()` / `shouldReturnNullWhenHeaderIsEmpty()`

**토큰 검증 (Token Validation)** (8 tests)
- `유효한_토큰은_검증에_성공한다()` / `shouldValidateSuccessfullyWhenTokenIsValid()`
- `만료된_토큰은_검증에_실패한다()` / `shouldFailToValidateWhenTokenIsExpired()`
- `서명이_올바르지_않으면_검증에_실패한다()` / `shouldFailToValidateWhenSignatureIsInvalid()`
- `형식이_올바르지_않으면_검증에_실패한다()` / `shouldFailToValidateWhenTokenIsMalformed()`
- `토큰이_null이면_검증에_실패한다()` / `shouldFailToValidateWhenTokenIsNull()`
- `토큰이_빈_문자열이면_검증에_실패한다()` / `shouldFailToValidateWhenTokenIsEmpty()`
- `페이로드가_변조되면_검증에_실패한다()` / `shouldFailToValidateWhenPayloadIsModified()`
- `다른_시크릿_키로_생성된_토큰은_검증에_실패한다()` / `shouldFailToValidateWhenSecretKeyIsDifferent()`

**이메일 추출 (Email Extraction)** (5 tests)
- `유효한_토큰에서_이메일을_추출할_수_있다()` / `shouldExtractEmailFromValidToken()`
- `만료된_토큰에서는_null을_반환한다()` / `shouldReturnNullWhenTokenIsExpired()`
- `유효하지_않은_토큰에서는_null을_반환한다()` / `shouldReturnNullWhenTokenIsInvalid()`
- `이메일_클레임이_없으면_null을_반환한다()` / `shouldReturnNullWhenEmailClaimIsMissing()`
- `토큰이_null이면_null을_반환한다()` / `shouldReturnNullWhenTokenIsNull()`

**Mock Strategy**: None (pure logic test)
**Key Focus**: 서명 변조, 토큰 만료, 형식 오류 검증

---

### 1.2 JwtAuthenticationFilterTest.java ⭐ CRITICAL
**Location**: `src/test/java/com/jipjung/project/config/jwt/filter/JwtAuthenticationFilterTest.java`
**Type**: Unit Test
**Dependency**: `/src/main/java/com/jipjung/project/config/jwt/filter/JwtAuthenticationFilter.java`

**Test Cases** (13 tests):

**Filter Bypass** (3 tests)
- `doFilterInternal_AuthPathRequest_SkipsAuthentication()`
- `doFilterInternal_SignupRequest_SkipsAuthentication()`
- `doFilterInternal_LoginRequest_SkipsAuthentication()`

**Token Processing** (6 tests)
- `doFilterInternal_ValidToken_SetsAuthentication()`
- `doFilterInternal_ValidToken_LoadsUserDetails()`
- `doFilterInternal_ExpiredToken_DoesNotSetAuthentication()`
- `doFilterInternal_InvalidToken_DoesNotSetAuthentication()`
- `doFilterInternal_NoAuthorizationHeader_DoesNotSetAuthentication()`
- `doFilterInternal_MalformedAuthorizationHeader_DoesNotSetAuthentication()`

**Security Context** (4 tests)
- `doFilterInternal_ValidToken_SecurityContextContainsAuthentication()`
- `doFilterInternal_InvalidToken_SecurityContextRemainsEmpty()`
- `doFilterInternal_UserNotFound_DoesNotSetAuthentication()`
- `doFilterInternal_ConcurrentRequests_IsolatesSecurityContext()`

**Mock Strategy**:
- Mock: `JwtProvider`, `LoginService`, `HttpServletRequest`, `HttpServletResponse`, `FilterChain`
- Verify: `filterChain.doFilter()` called, `SecurityContextHolder` state

---

### 1.3 CustomJsonUsernamePasswordAuthenticationFilterTest.java
**Location**: `src/test/java/com/jipjung/project/config/jwt/filter/CustomJsonUsernamePasswordAuthenticationFilterTest.java`
**Type**: Unit Test

**Test Cases** (10 tests):

**Request Parsing** (6 tests)
- `attemptAuthentication_ValidJsonRequest_ParsesCredentials()`
- `attemptAuthentication_InvalidJson_ThrowsAuthenticationException()`
- `attemptAuthentication_MissingEmail_ThrowsAuthenticationException()`
- `attemptAuthentication_MissingPassword_ThrowsAuthenticationException()`
- `attemptAuthentication_EmptyCredentials_ThrowsAuthenticationException()`
- `attemptAuthentication_NonJsonContentType_ThrowsException()`

**Authentication Process** (4 tests)
- `attemptAuthentication_ValidCredentials_CallsAuthenticationManager()`
- `attemptAuthentication_InvalidCredentials_ThrowsException()`
- `attemptAuthentication_NullRequest_ThrowsException()`
- `attemptAuthentication_LargePayload_HandlesGracefully()`

**Mock Strategy**:
- Mock: `AuthenticationManager`, `ObjectMapper`, `HttpServletRequest`, `HttpServletResponse`
- Verify: `authenticationManager.authenticate()` called with correct credentials

---

### 1.4 LoginHandlersTest.java
**Location**: `src/test/java/com/jipjung/project/config/jwt/handler/LoginHandlersTest.java`
**Type**: Unit Test

**Test Cases** (9 tests):

**LoginSuccessHandler** (4 tests)
- `onAuthenticationSuccess_ValidUser_ReturnsTokenInHeader()`
- `onAuthenticationSuccess_ValidUser_Returns200Status()`
- `onAuthenticationSuccess_ValidUser_ReturnsUserInfoInBody()`
- `onAuthenticationSuccess_NullAuthentication_HandlesGracefully()`

**LoginFailureHandler** (5 tests)
- `onAuthenticationFailure_InvalidCredentials_Returns401()`
- `onAuthenticationFailure_InvalidCredentials_ReturnsErrorMessage()`
- `onAuthenticationFailure_UserNotFound_Returns401()`
- `onAuthenticationFailure_DisabledUser_ReturnsAppropriateError()`
- `onAuthenticationFailure_LockedUser_ReturnsAppropriateError()`

---

## Phase 2: Service Layer - Business Logic 💼 HIGH PRIORITY

### 2.1 AuthServiceTest.java ⭐ CRITICAL
**Location**: `src/test/java/com/jipjung/project/service/AuthServiceTest.java`
**Type**: Unit Test (BDD 스타일)
**Dependency**: `/src/main/java/com/jipjung/project/service/AuthService.java`

**Test Cases** (16 tests):

**회원가입 성공 (Signup Success)** (3 tests)
- `유효한_요청으로_회원가입할_수_있다()` / `shouldSignupSuccessfullyWhenRequestIsValid()`
  - @DisplayName: "유효한 요청으로 회원가입할 수 있다"
- `회원가입_시_비밀번호가_암호화된다()` / `shouldEncodePasswordWhenSigningUp()`
  - @DisplayName: "회원가입 시 비밀번호가 암호화된다"
- `회원가입_성공_시_이메일과_닉네임을_반환한다()` / `shouldReturnEmailAndNicknameWhenSignupSucceeds()`
  - @DisplayName: "회원가입 성공 시 이메일과 닉네임을 반환한다"

**이메일 검증 (Email Validation)** (4 tests)
- `이메일이_중복되면_회원가입이_실패한다()` / `shouldFailToSignupWhenEmailIsDuplicated()`
  - @DisplayName: "이메일이 중복되면 회원가입이 실패한다"
- `대소문자를_구분하지_않고_중복을_검증한다()` / `shouldDetectDuplicateEmailCaseInsensitively()`
  - @DisplayName: "대소문자를 구분하지 않고 중복을 검증한다"
- `이메일의_공백은_제거된다()` / `shouldTrimWhitespaceFromEmail()`
  - @DisplayName: "이메일의 공백은 제거된다"
- `연속된_회원가입_시_첫_번째만_성공한다()` / `shouldSucceedOnlyFirstWhenSignupSequentially()`
  - @DisplayName: "동일 이메일로 연속 회원가입 시 첫 번째만 성공한다"

**비밀번호 보안 (Password Security)** (5 tests)
- `유효한_비밀번호는_암호화되어_저장된다()` / `shouldStorePasswordEncodedWhenValid()`
  - @DisplayName: "유효한 비밀번호는 암호화되어 저장된다"
- `암호화된_비밀번호는_평문과_다르다()` / `shouldDifferFromPlaintextWhenPasswordIsEncoded()`
  - @DisplayName: "암호화된 비밀번호는 평문과 다르다"
- `동일한_비밀번호도_사용자마다_다른_해시를_가진다()` / `shouldHaveDifferentHashForSamePasswordAcrossUsers()`
  - @DisplayName: "동일한 비밀번호도 사용자마다 다른 해시를 가진다"
- `비밀번호는_로그나_응답에_노출되지_않는다()` / `shouldNotExposePasswordInLogsOrResponse()`
  - @DisplayName: "비밀번호는 로그나 응답에 노출되지 않는다"
- `약한_비밀번호는_검증_단계에서_거부된다()` / `shouldRejectWeakPasswordAtValidation()`
  - @DisplayName: "약한 비밀번호는 검증 단계에서 거부된다"

**권한 할당 (Role Assignment)** (2 tests)
- `신규_회원은_USER_권한을_부여받는다()` / `shouldAssignUserRoleToNewMember()`
  - @DisplayName: "신규 회원은 USER 권한을 부여받는다"
- `신규_회원은_활성_상태로_생성된다()` / `shouldCreateNewMemberAsActive()`
  - @DisplayName: "신규 회원은 활성 상태로 생성된다"

**트랜잭션 처리 (Transaction Behavior)** (2 tests)
- `데이터베이스_오류_시_롤백된다()` / `shouldRollbackWhenDatabaseErrorOccurs()`
  - @DisplayName: "데이터베이스 오류 시 트랜잭션이 롤백된다"
- `부분_실패_시_사용자가_생성되지_않는다()` / `shouldNotCreateUserWhenPartialFailureOccurs()`
  - @DisplayName: "부분 실패 시 사용자가 생성되지 않는다"

**Mock Strategy**:
- Mock: `UserMapper`, `PasswordEncoder` (BDDMockito 사용)
- Verify: `then(userMapper).should().insertUser()` 호출 확인
- ArgumentCaptor: User 객체 캡처하여 암호화된 비밀번호, 권한 검증

---

### 2.2 ApartmentServiceTest.java ⭐ CRITICAL
**Location**: `src/test/java/com/jipjung/project/service/ApartmentServiceTest.java`
**Type**: Unit Test (BDD 스타일)
**Dependency**: `/src/main/java/com/jipjung/project/service/ApartmentService.java`

**Test Cases** (28 tests):

**아파트 검색 (Apartment Search)** (7 tests)
- `필터_없이_모든_아파트를_조회할_수_있다()` / `shouldReturnAllApartmentsWhenNoFilterApplied()`
  - @DisplayName: "필터 없이 모든 아파트를 조회할 수 있다"
- `아파트명으로_필터링할_수_있다()` / `shouldFilterByApartmentNameCorrectly()`
- `읍면동명으로_필터링할_수_있다()` / `shouldFilterByUmdNameCorrectly()`
- `페이징_처리하여_조회할_수_있다()` / `shouldReturnCorrectPageWhenPaginationApplied()`
- `거래일_범위로_필터링할_수_있다()` / `shouldFilterByDealDateRangeCorrectly()`
- `거래금액_범위로_필터링할_수_있다()` / `shouldFilterByDealAmountRangeCorrectly()`
- `검색_결과가_없으면_빈_리스트를_반환한다()` / `shouldReturnEmptyListWhenNoResultFound()`

**아파트 상세 조회 (Apartment Detail)** (3 tests)
- `유효한_아파트_코드로_상세정보를_조회할_수_있다()` / `shouldReturnDetailWithDealsWhenAptSeqIsValid()`
  - @DisplayName: "유효한 아파트 코드로 상세정보와 거래내역을 조회할 수 있다"
- `존재하지_않는_아파트_조회_시_예외가_발생한다()` / `shouldThrowExceptionWhenApartmentNotFound()`
  - @DisplayName: "존재하지 않는 아파트 조회 시 예외가 발생한다"
- `아파트_코드가_null이면_예외가_발생한다()` / `shouldThrowExceptionWhenAptSeqIsNull()`

**관심 아파트 등록 (Add Favorite)** (8 tests) ⚡
- `유효한_요청으로_관심_아파트를_등록할_수_있다()` / `shouldAddFavoriteWhenRequestIsValid()`
  - @DisplayName: "유효한 요청으로 관심 아파트를 등록할 수 있다"
- `존재하지_않는_아파트는_관심_등록할_수_없다()` / `shouldFailToAddFavoriteWhenApartmentNotExists()`
  - @DisplayName: "존재하지 않는 아파트는 관심 등록할 수 없다"
- `이미_등록한_아파트는_중복_등록할_수_없다()` / `shouldFailToAddFavoriteWhenAlreadyAdded()` ⚡
  - @DisplayName: "이미 등록한 아파트는 중복 등록할 수 없다"
- `등록_성공_시_관심_아파트_정보를_반환한다()` / `shouldReturnFavoriteInfoWhenAdditionSucceeds()`
- `사용자_ID가_null이면_예외가_발생한다()` / `shouldThrowExceptionWhenUserIdIsNull()`
- `아파트_코드가_null이면_예외가_발생한다()` / `shouldThrowExceptionWhenAptSeqIsNull()`
- `동시에_중복_등록_요청_시_하나만_성공한다()` / `shouldSucceedOnlyOnceWhenConcurrentDuplicateRequests()`
  - @DisplayName: "동시에 중복 등록 요청 시 하나만 성공한다"
- `등록_후_내_관심_목록에서_조회할_수_있다()` / `shouldBeRetrievableAfterAddition()`

**관심 아파트 조회 (Get Favorites)** (4 tests)
- `내_관심_아파트_목록을_조회할_수_있다()` / `shouldReturnAllMyFavoritesWhenRequested()`
  - @DisplayName: "내 관심 아파트 목록을 조회할 수 있다"
- `관심_아파트가_없으면_빈_리스트를_반환한다()` / `shouldReturnEmptyListWhenNoFavoritesExist()`
- `다른_사용자의_관심_아파트는_조회되지_않는다()` / `shouldIsolateResultsBetweenDifferentUsers()`
  - @DisplayName: "다른 사용자의 관심 아파트는 조회되지 않는다"
- `사용자_ID가_null이면_예외가_발생한다()` / `shouldThrowExceptionWhenUserIdIsNull()`

**관심 아파트 삭제 (Delete Favorite)** (6 tests) ⚡
- `본인의_관심_아파트를_삭제할_수_있다()` / `shouldDeleteFavoriteWhenOwnerRequests()`
  - @DisplayName: "본인의 관심 아파트를 삭제할 수 있다"
- `존재하지_않는_관심_아파트_삭제_시_예외가_발생한다()` / `shouldThrowExceptionWhenFavoriteNotFound()`
- `다른_사용자의_관심_아파트는_삭제할_수_없다()` / `shouldFailToDeleteWhenNotOwner()` ⚡
  - @DisplayName: "다른 사용자의 관심 아파트는 삭제할 수 없다 (소유권 검증)"
- `관심_아파트_ID가_null이면_예외가_발생한다()` / `shouldThrowExceptionWhenFavoriteIdIsNull()`
- `이미_삭제된_관심_아파트_재삭제_시_예외가_발생한다()` / `shouldThrowExceptionWhenAlreadyDeleted()`
- `삭제_전_소유권을_반드시_검증한다()` / `shouldVerifyOwnershipBeforeDeletion()` ⚡
  - @DisplayName: "삭제 전 소유권을 반드시 검증한다 (보안)"

**Mock Strategy**:
- Mock: `ApartmentMapper`, `FavoriteApartmentMapper` (BDDMockito 사용)
- Verify: `then(mapper).should()` 메서드 호출 검증
- ArgumentCaptor: FavoriteApartment 캡처하여 userId, aptSeq 검증

**Key Focus**: 소유권 검증 (line 125-129), 중복 방지 (line 116-120)

---

### 2.3 LoginServiceTest.java
**Location**: `src/test/java/com/jipjung/project/service/LoginServiceTest.java`
**Type**: Unit Test

**Test Cases** (9 tests):

**Load User** (5 tests)
- `loadUserByUsername_ExistingUser_ReturnsUserDetails()`
- `loadUserByUsername_NonExistentUser_ThrowsUsernameNotFoundException()`
- `loadUserByUsername_NullEmail_ThrowsException()`
- `loadUserByUsername_EmptyEmail_ThrowsUsernameNotFoundException()`
- `loadUserByUsername_CaseInsensitiveEmail_FindsUser()`

**UserDetails Properties** (4 tests)
- `loadUserByUsername_ActiveUser_IsEnabled()`
- `loadUserByUsername_InactiveUser_IsDisabled()`
- `loadUserByUsername_ReturnsCorrectAuthorities()`
- `loadUserByUsername_ReturnsCorrectEmailAsUsername()`

---

### 2.4 MyBatis Mapper Tests (User Priority) ⭐

#### UserMapperTest.java
**Location**: `src/test/java/com/jipjung/project/repository/UserMapperTest.java`
**Type**: MyBatis Integration Test
**Annotation**: `@MybatisTest`

**Test Cases** (12 tests):

**Insert** (3 tests)
- `insertUser_ValidUser_ReturnsGeneratedId()`
- `insertUser_ValidUser_PersistsAllFields()`
- `insertUser_DuplicateEmail_ThrowsException()`

**Find By Email** (5 tests)
- `findByEmail_ExistingUser_ReturnsUser()`
- `findByEmail_NonExistentEmail_ReturnsEmpty()`
- `findByEmail_CaseInsensitiveEmail_FindsUser()`
- `findByEmail_WithWhitespace_HandlesCorrectly()`
- `findByEmail_NullEmail_ReturnsEmpty()`

**Exists By Email** (4 tests)
- `existsByEmail_ExistingEmail_ReturnsTrue()`
- `existsByEmail_NonExistentEmail_ReturnsFalse()`
- `existsByEmail_CaseInsensitiveEmail_ReturnsTrue()`
- `existsByEmail_NullEmail_ReturnsFalse()`

**Mock Strategy**: None (real H2 database)

---

#### ApartmentMapperTest.java
**Location**: `src/test/java/com/jipjung/project/repository/ApartmentMapperTest.java`
**Type**: MyBatis Integration Test

**Test Cases** (16 tests):

**Find With Deals** (4 tests)
- `findByAptSeqWithDeals_ValidAptSeq_ReturnsApartmentWithDeals()`
- `findByAptSeqWithDeals_NonExistentAptSeq_ReturnsEmpty()`
- `findByAptSeqWithDeals_ApartmentWithNoDeals_ReturnsApartmentWithEmptyDeals()`
- `findByAptSeqWithDeals_DealsOrderedByDateDesc_Verified()`

**Find All With Latest Deal** (6 tests)
- `findAllWithLatestDeal_NoFilters_ReturnsAll()`
- `findAllWithLatestDeal_WithAptNameFilter_FiltersCorrectly()`
- `findAllWithLatestDeal_WithUmdNameFilter_FiltersCorrectly()`
- `findAllWithLatestDeal_WithPagination_ReturnsCorrectPage()`
- `findAllWithLatestDeal_EachApartmentHasOnlyLatestDeal_Verified()`
- `findAllWithLatestDeal_SortsByDealDateDesc_Verified()`

**Count** (3 tests)
- `count_NoFilters_ReturnsTotal()`
- `count_WithFilters_ReturnsFilteredCount()`
- `count_EmptyDatabase_ReturnsZero()`

**Exists** (3 tests)
- `existsByAptSeq_ExistingAptSeq_ReturnsTrue()`
- `existsByAptSeq_NonExistentAptSeq_ReturnsFalse()`
- `existsByAptSeq_NullAptSeq_ReturnsFalse()`

---

#### FavoriteApartmentMapperTest.java
**Location**: `src/test/java/com/jipjung/project/repository/FavoriteApartmentMapperTest.java`
**Type**: MyBatis Integration Test

**Test Cases** (17 tests):

**Insert** (3 tests)
- `insert_ValidFavorite_ReturnsGeneratedId()`
- `insert_ValidFavorite_PersistsAllFields()`
- `insert_DuplicateUserApartment_ThrowsException()`

**Find By User Id** (4 tests)
- `findByUserId_UserWithFavorites_ReturnsAll()`
- `findByUserId_UserWithNoFavorites_ReturnsEmptyList()`
- `findByUserId_DifferentUsers_IsolatedResults()`
- `findByUserId_NullUserId_ReturnsEmptyList()`

**Find By Id** (3 tests)
- `findById_ExistingFavorite_ReturnsFavorite()`
- `findById_NonExistentId_ReturnsEmpty()`
- `findById_NullId_ReturnsEmpty()`

**Exists By User And Apartment** (4 tests)
- `existsByUserIdAndAptSeq_ExistingFavorite_ReturnsTrue()`
- `existsByUserIdAndAptSeq_NonExistentFavorite_ReturnsFalse()`
- `existsByUserIdAndAptSeq_DifferentUser_ReturnsFalse()`
- `existsByUserIdAndAptSeq_DifferentApartment_ReturnsFalse()`

**Delete** (3 tests)
- `deleteById_ExistingFavorite_DeletesSuccessfully()`
- `deleteById_NonExistentId_DoesNotThrowException()`
- `deleteById_AfterDeletion_NotFound()`

---

## Implementation Guidelines

### BDD Test Style

이 프로젝트는 **BDD (Behavior-Driven Development)** 스타일을 따릅니다.

### Test Naming Convention
```
자연스러운 설명형 메서드명 (한글 또는 영어)
```
**Examples**:
- `이메일이_중복되면_회원가입이_실패한다()`
- `유효한_요청으로_관심_아파트를_등록할_수_있다()`
- `만료된_토큰은_검증에_실패한다()`

또는 영어로:
- `shouldFailToSignupWhenEmailIsDuplicated()`
- `shouldAddFavoriteWhenRequestIsValid()`
- `shouldFailToValidateWhenTokenIsExpired()`

### @DisplayName 어노테이션 (필수)
모든 테스트에 한글 설명 추가:
```java
@Test
@DisplayName("이메일이 중복되면 회원가입이 실패한다")
void shouldFailToSignupWhenEmailIsDuplicated() { ... }
```

### Given-When-Then 패턴 (BDD)
```java
@Test
@DisplayName("유효한 요청으로 관심 아파트를 등록할 수 있다")
void shouldAddFavoriteWhenRequestIsValid() {
    // Given (주어진 상황: 테스트 데이터 준비)
    Long userId = 1L;
    FavoriteRequest request = new FavoriteRequest("11410-61");
    given(apartmentMapper.existsByAptSeq("11410-61")).willReturn(true);
    given(favoriteApartmentMapper.existsByUserIdAndAptSeq(userId, "11410-61")).willReturn(false);

    // When (특정 행동을 하면: 테스트 대상 메서드 실행)
    FavoriteResponse response = apartmentService.addFavorite(userId, request);

    // Then (결과를 검증: 기대값 확인)
    assertThat(response).isNotNull();
    assertThat(response.aptSeq()).isEqualTo("11410-61");
    then(favoriteApartmentMapper).should().insert(any(FavoriteApartment.class));
}
```

### Mockito BDD 구문 (필수)
| 기존 AAA | BDD 스타일 |
|----------|-----------|
| `when(mock.method()).thenReturn(value)` | `given(mock.method()).willReturn(value)` |
| `when(mock.method()).thenThrow(exception)` | `given(mock.method()).willThrow(exception)` |
| `verify(mock).method()` | `then(mock).should().method()` |
| `verify(mock, times(1)).method()` | `then(mock).should(times(1)).method()` |
| `verify(mock, never()).method()` | `then(mock).should(never()).method()` |

**Import 변경:**
```java
// BDD 스타일 imports
import static org.mockito.BDDMockito.*;
import static org.assertj.core.api.Assertions.*;
```

### AssertJ 사용 (권장)
JUnit 기본 assertions 대신 AssertJ 사용:

```java
// ❌ JUnit 스타일
assertNotNull(response);
assertEquals("11410-61", response.aptSeq());
assertTrue(list.isEmpty());

// ✅ AssertJ 스타일 (더 읽기 쉬움)
assertThat(response).isNotNull();
assertThat(response.aptSeq()).isEqualTo("11410-61");
assertThat(list).isEmpty();
```

### Exception Testing (BDD)
```java
@Test
@DisplayName("중복된 이메일로 회원가입하면 예외가 발생한다")
void shouldThrowExceptionWhenSignupWithDuplicateEmail() {
    // Given
    SignupRequest request = new SignupRequest("test@email.com", "닉네임", "Test123!@");
    given(userMapper.existsByEmail("test@email.com")).willReturn(true);

    // When & Then
    assertThatThrownBy(() -> authService.signup(request))
        .isInstanceOf(DuplicateEmailException.class)
        .hasMessageContaining("이미 존재하는 이메일");

    then(userMapper).should().existsByEmail("test@email.com");
    then(userMapper).should(never()).insertUser(any());
}
```

### Mock Strategy by Layer

| Layer | Strategy | Annotations | BDD Mock |
|-------|----------|-------------|----------|
| **Controllers** | Mock Services | `@WebMvcTest` + `@MockBean` | `given().willReturn()` |
| **Services** | Mock Repositories | `@ExtendWith(MockitoExtension.class)` + `@Mock` | `given().willReturn()` |
| **Repositories** | Real H2 Database | `@MybatisTest` | No mocking |
| **Security** | Mock Authentication | `@WithMockUser` or custom context | `given().willReturn()` |

---

## Critical Test Scenarios

### 1. JWT Token Security ⚡
- **Expired token rejection** → Must return false/null
- **Signature tampering** → Must detect and reject
- **Modified payload** → Must fail validation
- **Missing email claim** → Must handle gracefully

### 2. Password Security ⚡
- **Encoding verification** → Password must be encoded before storage
- **Regex edge cases** → Test all boundary conditions:
  - Exactly 8 chars: `Test123!` ✓
  - 7 chars: `Test12!` ✗
  - No special: `Test1234` ✗
  - No digit: `Testtest!` ✗
  - No letter: `12345678!` ✗

### 3. Ownership Validation ⚡
- **Favorite deletion** → User A cannot delete User B's favorite
- **Authorization check** → Must validate before deletion (line 125-129 in ApartmentService)

### 4. Duplicate Prevention ⚡
- **Email uniqueness** → Second signup with same email must fail with 409
- **Favorite uniqueness** → Cannot add same apartment twice for one user
- **Concurrent requests** → Race condition handling

---

## Execution Strategy

### Order of Implementation
1. ✅ **Phase 0**: Test infrastructure (base classes, fixtures) - **Blocking**
2. ✅ **Phase 1**: Security tests (JWT, filters, handlers) - **Can start after Phase 0**
3. ✅ **Phase 2**: Service tests + Mapper tests - **Can start after Phase 0**

### Running Tests
```bash
# Run all tests
./mvnw test

# Run specific test class
./mvnw test -Dtest=JwtProviderTest

# Run all service tests
./mvnw test -Dtest="*ServiceTest"

# Run all mapper tests
./mvnw test -Dtest="*MapperTest"

# Run with coverage
./mvnw test jacoco:report
```

### Coverage Goals
| Component | Target | Priority |
|-----------|--------|----------|
| **JwtProvider** | 90%+ | Critical |
| **AuthService** | 85%+ | Critical |
| **ApartmentService** | 85%+ | Critical |
| **Filters** | 80%+ | High |
| **Mappers** | 75%+ | High |

---

## Definition of Done (Per Phase)

### Phase 0: Infrastructure
- [ ] `application-test.properties` created and working
- [ ] `TestFixtures.java` with factory methods
- [ ] `IntegrationTestBase.java` with common setup
- [ ] `ControllerTestBase.java` with MockMvc helpers
- [ ] Can run sample test successfully

### Phase 1: Security
- [ ] 54 security tests passing
- [ ] All JWT scenarios covered
- [ ] Filter chain tested
- [ ] No skipped tests

### Phase 2: Business Logic & Data
- [ ] 53 service tests passing
- [ ] 45 mapper tests passing
- [ ] All business rules validated
- [ ] Transaction behavior verified

---

## Critical Files Reference

### Main Code (To Test)
```
/src/main/java/com/jipjung/project/
├── config/jwt/
│   ├── JwtProvider.java                                    [22 tests]
│   ├── filter/JwtAuthenticationFilter.java                [13 tests]
│   └── filter/CustomJsonUsernamePasswordAuthenticationFilter.java  [10 tests]
├── service/
│   ├── AuthService.java                                   [16 tests]
│   ├── ApartmentService.java                              [28 tests]
│   └── LoginService.java                                  [9 tests]
└── repository/
    ├── UserMapper.java                                    [12 tests]
    ├── ApartmentMapper.java                               [16 tests]
    └── FavoriteApartmentMapper.java                       [17 tests]
```

### Test Code (To Create)
```
/src/test/java/com/jipjung/project/
├── support/
│   ├── TestFixtures.java              [Phase 0]
│   ├── IntegrationTestBase.java       [Phase 0]
│   └── ControllerTestBase.java        [Phase 0]
├── config/jwt/
│   ├── JwtProviderTest.java           [Phase 1]
│   └── filter/
│       ├── JwtAuthenticationFilterTest.java                [Phase 1]
│       └── CustomJsonUsernamePasswordAuthenticationFilterTest.java  [Phase 1]
├── service/
│   ├── AuthServiceTest.java           [Phase 2]
│   ├── ApartmentServiceTest.java      [Phase 2]
│   └── LoginServiceTest.java          [Phase 2]
└── repository/
    ├── UserMapperTest.java            [Phase 2]
    ├── ApartmentMapperTest.java       [Phase 2]
    └── FavoriteApartmentMapperTest.java  [Phase 2]
```

---

## Success Metrics

**Phase 0**: Infrastructure working, sample test passes
**Phase 1**: 54 security tests passing, JWT fully validated
**Phase 2**: 98 tests passing (53 service + 45 mapper), 70%+ coverage achieved

**Total**: ~152 tests across 11 test classes

---

## BDD 실제 코드 예제

### 예제 1: 회원가입 테스트 (AuthServiceTest)

```java
@ExtendWith(MockitoExtension.class)
@DisplayName("회원가입 서비스 테스트")
class AuthServiceTest {

    @Mock
    private UserMapper userMapper;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private AuthService authService;

    @Test
    @DisplayName("유효한 요청으로 회원가입할 수 있다")
    void 유효한_요청으로_회원가입할_수_있다() {
        // Given (주어진 상황: 중복 이메일 없음, 비밀번호 암호화 준비)
        SignupRequest request = new SignupRequest("test@email.com", "홍길동", "Test123!@");
        given(userMapper.existsByEmail("test@email.com")).willReturn(false);
        given(passwordEncoder.encode("Test123!@")).willReturn("$2a$10$encoded...");

        // When (특정 행동: 회원가입 실행)
        SignupResponse response = authService.signup(request);

        // Then (결과 검증)
        assertThat(response).isNotNull();
        assertThat(response.email()).isEqualTo("test@email.com");
        assertThat(response.nickname()).isEqualTo("홍길동");

        // 메서드 호출 검증
        then(userMapper).should().existsByEmail("test@email.com");
        then(passwordEncoder).should().encode("Test123!@");
        then(userMapper).should().insertUser(argThat(user ->
            user.getEmail().equals("test@email.com") &&
            user.getRole() == UserRole.USER &&
            !user.getPassword().equals("Test123!@") // 평문이 아님
        ));
    }

    @Test
    @DisplayName("이메일이 중복되면 회원가입이 실패한다")
    void 이메일이_중복되면_회원가입이_실패한다() {
        // Given
        SignupRequest request = new SignupRequest("duplicate@email.com", "홍길동", "Test123!@");
        given(userMapper.existsByEmail("duplicate@email.com")).willReturn(true);

        // When & Then (예외 발생 검증)
        assertThatThrownBy(() -> authService.signup(request))
            .isInstanceOf(DuplicateEmailException.class)
            .hasMessageContaining("이미 존재하는 이메일");

        // 호출되지 않아야 하는 메서드 검증
        then(passwordEncoder).should(never()).encode(anyString());
        then(userMapper).should(never()).insertUser(any(User.class));
    }
}
```

### 예제 2: 관심 아파트 테스트 (ApartmentServiceTest)

```java
@ExtendWith(MockitoExtension.class)
@DisplayName("아파트 서비스 테스트")
class ApartmentServiceTest {

    @Mock
    private ApartmentMapper apartmentMapper;

    @Mock
    private FavoriteApartmentMapper favoriteApartmentMapper;

    @InjectMocks
    private ApartmentService apartmentService;

    @Test
    @DisplayName("유효한 요청으로 관심 아파트를 등록할 수 있다")
    void 유효한_요청으로_관심_아파트를_등록할_수_있다() {
        // Given
        Long userId = 1L;
        String aptSeq = "11410-61";
        FavoriteRequest request = new FavoriteRequest(aptSeq);

        given(apartmentMapper.existsByAptSeq(aptSeq)).willReturn(true);
        given(favoriteApartmentMapper.existsByUserIdAndAptSeq(userId, aptSeq)).willReturn(false);

        FavoriteApartment savedFavorite = FavoriteApartment.builder()
            .id(100L)
            .userId(userId)
            .aptSeq(aptSeq)
            .apartment(createTestApartment())
            .build();
        given(favoriteApartmentMapper.findById(any())).willReturn(Optional.of(savedFavorite));

        // When
        FavoriteResponse response = apartmentService.addFavorite(userId, request);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.aptSeq()).isEqualTo(aptSeq);
        assertThat(response.aptNm()).isEqualTo("테스트아파트");

        then(apartmentMapper).should().existsByAptSeq(aptSeq);
        then(favoriteApartmentMapper).should().existsByUserIdAndAptSeq(userId, aptSeq);
        then(favoriteApartmentMapper).should().insert(argThat(fav ->
            fav.getUserId().equals(userId) && fav.getAptSeq().equals(aptSeq)
        ));
    }

    @Test
    @DisplayName("다른 사용자의 관심 아파트는 삭제할 수 없다")
    void 다른_사용자의_관심_아파트는_삭제할_수_없다() {
        // Given (User A가 등록한 관심 아파트를 User B가 삭제 시도)
        Long ownerUserId = 1L;
        Long otherUserId = 2L;
        Long favoriteId = 100L;

        FavoriteApartment favorite = FavoriteApartment.builder()
            .id(favoriteId)
            .userId(ownerUserId) // 소유자는 User A
            .aptSeq("11410-61")
            .build();

        given(favoriteApartmentMapper.findById(favoriteId)).willReturn(Optional.of(favorite));

        // When & Then (소유권 검증 실패로 예외 발생)
        assertThatThrownBy(() -> apartmentService.deleteFavorite(otherUserId, favoriteId))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("본인의 관심 아파트만 삭제");

        // 삭제 메서드가 호출되지 않았는지 검증
        then(favoriteApartmentMapper).should(never()).deleteById(favoriteId);
    }

    private Apartment createTestApartment() {
        return Apartment.builder()
            .aptSeq("11410-61")
            .aptNm("테스트아파트")
            .umdNm("테스트동")
            .build();
    }
}
```

### 예제 3: JWT 토큰 테스트 (JwtProviderTest)

```java
@DisplayName("JWT 토큰 제공자 테스트")
class JwtProviderTest {

    @InjectMocks
    private JwtProvider jwtProvider;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(jwtProvider, "secret", "TestSecretKey1234567890123456789012345");
        ReflectionTestUtils.setField(jwtProvider, "accessTokenExpiration", 3600000L);
    }

    @Test
    @DisplayName("유효한 이메일로 액세스 토큰을 생성할 수 있다")
    void 유효한_이메일로_액세스_토큰을_생성할_수_있다() {
        // Given
        String email = "test@example.com";

        // When
        String token = jwtProvider.createAccessToken(email);

        // Then
        assertThat(token).isNotNull();
        assertThat(token).isNotEmpty();
        assertThat(jwtProvider.validateToken(token)).isTrue();
        assertThat(jwtProvider.getEmailFromToken(token)).isEqualTo(email);
    }

    @Test
    @DisplayName("만료된 토큰은 검증에 실패한다")
    void 만료된_토큰은_검증에_실패한다() throws InterruptedException {
        // Given (매우 짧은 만료 시간으로 토큰 생성)
        ReflectionTestUtils.setField(jwtProvider, "accessTokenExpiration", 1L);
        String token = jwtProvider.createAccessToken("test@example.com");

        Thread.sleep(10); // 토큰 만료 대기

        // When
        boolean isValid = jwtProvider.validateToken(token);
        String email = jwtProvider.getEmailFromToken(token);

        // Then
        assertThat(isValid).isFalse();
        assertThat(email).isNull();
    }

    @Test
    @DisplayName("서명이 올바르지 않으면 검증에 실패한다")
    void 서명이_올바르지_않으면_검증에_실패한다() {
        // Given
        String validToken = jwtProvider.createAccessToken("test@example.com");
        String tamperedToken = validToken.substring(0, validToken.length() - 10) + "XXXXXXXXXX";

        // When
        boolean isValid = jwtProvider.validateToken(tamperedToken);

        // Then
        assertThat(isValid).isFalse();
    }
}
```

### BDD 스타일 Import 문

```java
// BDD 스타일 imports
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

// BDDMockito static imports
import static org.mockito.BDDMockito.*;
import static org.mockito.ArgumentMatchers.*;

// AssertJ static imports
import static org.assertj.core.api.Assertions.*;
```

---

---

## 구현에 필요한 참고 정보

### 필수 의존성 (pom.xml에 이미 포함됨)

```xml
<dependencies>
    <!-- Spring Boot Test Starter (JUnit 5, Mockito, AssertJ 포함) -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-test</artifactId>
        <scope>test</scope>
    </dependency>

    <!-- MyBatis Spring Boot Test -->
    <dependency>
        <groupId>org.mybatis.spring.boot</groupId>
        <artifactId>mybatis-spring-boot-starter-test</artifactId>
        <version>3.0.5</version>
        <scope>test</scope>
    </dependency>

    <!-- Spring Security Test -->
    <dependency>
        <groupId>org.springframework.security</groupId>
        <artifactId>spring-security-test</artifactId>
        <scope>test</scope>
    </dependency>
</dependencies>
```

**주요 라이브러리**:
- JUnit 5 (Jupiter) - `@Test`, `@DisplayName`, `@BeforeEach`
- Mockito - BDDMockito 포함 (`given()`, `then()`)
- AssertJ - `assertThat()`, `assertThatThrownBy()`
- Spring Test - `@SpringBootTest`, `@WebMvcTest`, `MockMvc`
- MyBatis Test - `@MybatisTest`

### 도메인 모델 구조

#### User
```java
@Getter @Builder
public class User {
    private Long id;
    private String email;
    private String nickname;
    private String password;  // 암호화된 비밀번호
    private UserRole role;    // USER, ADMIN
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

public enum UserRole { USER, ADMIN }
```

#### Apartment
```java
@Getter @Builder
public class Apartment {
    private String aptSeq;          // PK
    private String aptNm;           // 아파트명
    private String umdNm;           // 읍면동명
    private String roadNm;          // 도로명
    private Integer buildYear;      // 건축년도
    private BigDecimal latitude;    // 위도
    private BigDecimal longitude;   // 경도
    // ... 기타 필드

    // 조회 시 조인 데이터
    private ApartmentDeal latestDeal;    // 최신 거래
    private List<ApartmentDeal> deals;   // 전체 거래 목록
}
```

#### FavoriteApartment
```java
@Getter @Builder
public class FavoriteApartment {
    private Long id;                // PK
    private Long userId;            // FK
    private String aptSeq;          // FK
    private LocalDateTime createdAt;

    // 조인 데이터
    private Apartment apartment;
}
```

### Request DTOs (Java Records)

```java
// 회원가입
public record SignupRequest(
    @NotBlank @Email String email,
    @NotBlank @Size(min=2, max=20) String nickname,
    @NotBlank @Pattern(regexp="^(?=.*[A-Za-z])(?=.*\\d)(?=.*[@$!%*#?&])[A-Za-z\\d@$!%*#?&]{8,}$")
    String password
) {}

// 관심 아파트 등록
public record FavoriteRequest(
    @NotBlank String aptSeq
) {}

// 아파트 검색
public record ApartmentSearchRequest(
    String aptNm, String umdNm,
    String dealDateFrom, String dealDateTo,
    Long minDealAmount, Long maxDealAmount,
    Integer page, Integer size
) {
    // Compact constructor에서 기본값 설정
    public ApartmentSearchRequest {
        page = (page != null && page >= 0) ? page : 0;
        size = (size != null && size > 0 && size <= 100) ? size : 10;
    }
}
```

### Response DTOs (Java Records with factory)

```java
public record SignupResponse(String email, String nickname) {}

public record FavoriteResponse(
    Long id, String aptSeq, String aptNm, String umdNm,
    String roadNm, Integer buildYear, LocalDateTime createdAt
) {
    public static FavoriteResponse from(FavoriteApartment favorite) {
        Apartment apt = favorite.getApartment();
        return new FavoriteResponse(
            favorite.getId(), favorite.getAptSeq(),
            apt.getAptNm(), apt.getUmdNm(), apt.getRoadNm(),
            apt.getBuildYear(), favorite.getCreatedAt()
        );
    }
}
```

### Exception 클래스들

```java
// src/main/java/com/jipjung/project/global/exception/

public class DuplicateEmailException extends RuntimeException {
    public DuplicateEmailException(String message) {
        super(message);
    }
}

public class ResourceNotFoundException extends RuntimeException {
    private final ErrorCode errorCode;
    public ResourceNotFoundException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }
}

public class DuplicateResourceException extends RuntimeException {
    private final ErrorCode errorCode;
    public DuplicateResourceException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }
}

public enum ErrorCode {
    DUPLICATE_EMAIL(409, "이미 존재하는 이메일입니다"),
    APARTMENT_NOT_FOUND(404, "아파트를 찾을 수 없습니다"),
    FAVORITE_NOT_FOUND(404, "관심 아파트를 찾을 수 없습니다"),
    DUPLICATE_FAVORITE(409, "이미 등록된 관심 아파트입니다"),
    // ...
}
```

### Service 메서드 시그니처

```java
// AuthService
public SignupResponse signup(SignupRequest request)  // @Transactional

// ApartmentService
public ApartmentListPageResponse searchApartments(ApartmentSearchRequest request)  // @Transactional(readOnly=true)
public ApartmentDetailResponse getApartmentDetail(String aptSeq)
public FavoriteResponse addFavorite(Long userId, FavoriteRequest request)  // @Transactional
public List<FavoriteResponse> getMyFavorites(Long userId)
public void deleteFavorite(Long userId, Long favoriteId)  // @Transactional

// LoginService (implements UserDetailsService)
public UserDetails loadUserByUsername(String email)  // throws UsernameNotFoundException
```

### Mapper 메서드 시그니처

```java
// UserMapper
Optional<User> findByEmail(String email);
int insertUser(User user);
boolean existsByEmail(String email);

// ApartmentMapper
List<Apartment> findAllWithLatestDeal(ApartmentSearchRequest request);
Optional<Apartment> findByAptSeqWithDeals(String aptSeq);
boolean existsByAptSeq(String aptSeq);
int count(ApartmentSearchRequest request);

// FavoriteApartmentMapper
int insert(FavoriteApartment favorite);
List<FavoriteApartment> findByUserId(Long userId);
Optional<FavoriteApartment> findById(Long id);
boolean existsByUserIdAndAptSeq(Long userId, String aptSeq);
int deleteById(Long id);
```

### 프로젝트 경로

```
src/
├── main/java/com/jipjung/project/
│   ├── config/
│   │   ├── SecurityConfig.java
│   │   └── jwt/
│   │       ├── JwtProvider.java
│   │       ├── filter/
│   │       │   ├── JwtAuthenticationFilter.java
│   │       │   └── CustomJsonUsernamePasswordAuthenticationFilter.java
│   │       └── handler/
│   │           ├── LoginSuccessHandler.java
│   │           └── LoginFailureHandler.java
│   ├── controller/
│   │   ├── AuthController.java
│   │   ├── ApartmentController.java
│   │   └── dto/
│   │       ├── request/
│   │       └── response/
│   ├── service/
│   │   ├── AuthService.java
│   │   ├── ApartmentService.java
│   │   └── LoginService.java
│   ├── repository/
│   │   ├── UserMapper.java
│   │   ├── ApartmentMapper.java
│   │   └── FavoriteApartmentMapper.java
│   ├── domain/
│   │   ├── User.java
│   │   ├── UserRole.java
│   │   ├── Apartment.java
│   │   └── FavoriteApartment.java
│   └── global/exception/
│       ├── DuplicateEmailException.java
│       ├── ResourceNotFoundException.java
│       ├── DuplicateResourceException.java
│       ├── ErrorCode.java
│       └── GlobalExceptionHandler.java
└── test/
    ├── java/com/jipjung/project/
    │   ├── support/          # Phase 0에서 생성
    │   ├── config/jwt/       # Phase 1에서 생성
    │   ├── service/          # Phase 2에서 생성
    │   └── repository/       # Phase 2에서 생성
    └── resources/
        └── application-test.properties  # ✅ 완료
```

---

## Next Steps After Plan Approval

1. Create Phase 0 infrastructure files
2. Implement JwtProviderTest (highest priority)
3. Implement AuthServiceTest (critical business logic)
4. Implement ApartmentServiceTest (complex validations)
5. Implement remaining Phase 1 & 2 tests
6. Run full test suite and verify coverage

**이 문서만으로 구현 시작 가능합니다!**