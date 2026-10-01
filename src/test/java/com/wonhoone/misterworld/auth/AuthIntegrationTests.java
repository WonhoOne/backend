package com.wonhoone.misterworld.auth;

import com.wonhoone.misterworld.api.dto.*;
import com.wonhoone.misterworld.domain.UserRole;
import com.wonhoone.misterworld.infrastructure.persistence.entity.UserAccountJpaEntity;
import com.wonhoone.misterworld.infrastructure.persistence.repository.UserAccountJpaRepository;
import com.wonhoone.misterworld.security.AuthenticatedUser;
import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.*;
import java.time.Instant;
import java.util.Date;
import java.util.Map;
import jakarta.servlet.Filter;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.*;
import org.springframework.boot.test.context.*;
import org.springframework.context.annotation.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@ActiveProfiles("test")
@Import(AuthIntegrationTests.ProbeConfig.class)
class AuthIntegrationTests {
    @Autowired WebApplicationContext context;
    @Autowired @Qualifier("springSecurityFilterChain") Filter securityFilter;
    @Autowired UserAccountJpaRepository accounts;
    @Autowired PasswordEncoder passwords;
    @Autowired ObjectMapper json;
    @Value("${security.jwt.secret}") String secret;
    MockMvc http;

    @BeforeEach void prepare() {
        accounts.deleteAll();
        http = MockMvcBuilders.webAppContextSetup(context).addFilters(securityFilter).build();
    }

    @Test void customerCanSignUpWithRequiredProfile() throws Exception {
        signup(" Customer01 ", "example-password").andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber()).andExpect(jsonPath("$.name").value("Customer"));
        var saved = accounts.findByLoginId("customer01").orElseThrow();
        assertThat(passwords.matches("example-password", saved.getPasswordHash())).isTrue();
        assertThat(saved.getAddress()).isEqualTo("Seoul");
        assertThat(saved.getContact()).isEqualTo("010-0000-0000");
    }

    @Test void publicSignupAlwaysCreatesCustomer() throws Exception {
        signup("customer", "password").andExpect(jsonPath("$.role").value("CUSTOMER"));
        assertThat(accounts.findByLoginId("customer").orElseThrow().getRole()).isEqualTo(UserRole.CUSTOMER);
    }

    @Test void signupDoesNotAcceptRoleInput() throws Exception {
        http.perform(post("/api/v1/auth/signup").contentType("application/json")
                .content("""
                {"loginId":"employee","password":"password","name":"A","address":"B","contact":"C","role":"EMPLOYEE"}
                """)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
        assertThat(accounts.count()).isZero();
    }

    @Test void signupDoesNotReturnAccessToken() throws Exception {
        signup("customer", "password").andExpect(jsonPath("$.accessToken").doesNotExist())
                .andExpect(jsonPath("$.tokenType").doesNotExist());
    }

    @Test void duplicateLoginIdReturnsConflict() throws Exception {
        signup("customer", "password").andExpect(status().isCreated());
        signup("customer", "password").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("LOGIN_ID_ALREADY_EXISTS"))
                .andExpect(jsonPath("$.fieldErrors").isEmpty());
    }

    @Test void concurrentSignupCreatesOneCustomerAndReturnsConflictForTheOther() throws Exception {
        var start = new java.util.concurrent.CountDownLatch(1);
        try (var workers = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            java.util.concurrent.Callable<Integer> attempt = () -> {
                start.await();
                return signup("concurrent-customer", "password").andReturn().getResponse().getStatus();
            };
            var first = workers.submit(attempt);
            var second = workers.submit(attempt);
            start.countDown();
            assertThat(java.util.List.of(first.get(15, java.util.concurrent.TimeUnit.SECONDS),
                    second.get(15, java.util.concurrent.TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(201, 409);
        }
        assertThat(accounts.count()).isEqualTo(1);
    }

    @Test void requiredProfileFieldsRespectTheirDatabaseStorageBounds() throws Exception {
        for (var entry : Map.of("name", 255, "address", 500, "contact", 255).entrySet()) {
            var body = new java.util.HashMap<String, String>(Map.of("loginId", "customer", "password", "password",
                    "name", "Customer", "address", "Seoul", "contact", "010"));
            body.put(entry.getKey(), "x".repeat(entry.getValue() + 1));
            http.perform(post("/api/v1/auth/signup").contentType("application/json").content(json.writeValueAsString(body)))
                    .andExpect(status().isUnprocessableContent())
                    .andExpect(jsonPath("$.fieldErrors[0].field").value(entry.getKey()))
                    .andExpect(jsonPath("$.fieldErrors[0].code").value("OUT_OF_RANGE"));
        }
        assertThat(accounts.count()).isZero();
    }

    @Test void loginIdIsCanonicalizedCaseInsensitively() throws Exception {
        signup(" CUSTOMER01 ", "password").andExpect(status().isCreated());
        signup("Customer01", "password").andExpect(status().isConflict());
        login("  Customer01 ", "password").andExpect(status().isOk());
    }

    @Test void blankRequiredFieldReturnsValidationError() throws Exception {
        signup(" ", "password").andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("loginId"))
                .andExpect(jsonPath("$.fieldErrors[0].code").value("REQUIRED"));
    }

    @Test void missingRequiredFieldsReturnValidationErrors() throws Exception {
        http.perform(post("/api/v1/auth/signup").contentType("application/json").content("{}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.fieldErrors.length()").value(5));
    }

    @Test void beanValidationReturns422CommonError() throws Exception {
        signup("x".repeat(256), "password").andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors[0].code").value("OUT_OF_RANGE"));
    }

    @Test void loginIdExpansionCannotExceedTheDatabaseStorageBound() throws Exception {
        signup("İ".repeat(128), "password").andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        assertThat(accounts.count()).isZero();
    }

    @Test void multibytePasswordCannotBeSilentlyTruncatedByBcrypt() throws Exception {
        signup("customer", "가".repeat(25)).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("password"));
        signup("customer", "가".repeat(24)).andExpect(status().isCreated());
        login("customer", "가".repeat(24)).andExpect(status().isOk());
    }

    @Test void customerCanLoginWithCorrectPassword() throws Exception {
        signup("customer", "password");
        login("customer", "password").andExpect(status().isOk()).andExpect(jsonPath("$.user.role").value("CUSTOMER"));
    }

    @Test void employeeCanLoginWithCorrectPassword() throws Exception {
        createEmployee();
        login(" EMPLOYEE ", "password").andExpect(status().isOk()).andExpect(jsonPath("$.user.role").value("EMPLOYEE"));
    }

    @Test void unknownLoginIdReturnsLoginFailed() throws Exception {
        login("missing", "password").andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("LOGIN_FAILED")).andExpect(jsonPath("$.fieldErrors").isEmpty());
    }

    @Test void wrongPasswordReturnsSameLoginFailed() throws Exception {
        signup("customer", "password");
        String wrong = login("customer", "wrong").andExpect(status().isUnauthorized()).andReturn().getResponse().getContentAsString();
        String missing = login("missing", "wrong").andReturn().getResponse().getContentAsString();
        assertThat(wrong).isEqualTo(missing);
    }

    @Test void passwordHashIsNotExposed() throws Exception {
        String signupBody = signup("customer", "sensitive-value").andReturn().getResponse().getContentAsString();
        String loginBody = login("customer", "sensitive-value").andReturn().getResponse().getContentAsString();
        assertThat(signupBody + loginBody).doesNotContain("sensitive-value", "passwordHash", "password", "$2a$");
        assertThat(new LoginRequest("customer", "sensitive-value").toString()).doesNotContain("sensitive-value");
        assertThat(new SignupRequest("customer", "sensitive-value", "A", "B", "C").toString()).doesNotContain("sensitive-value");
    }

    @Test void loginResponseContainsBearerTokenAndExpiresInSeconds() throws Exception {
        signup("customer", "password");
        login("customer", "password").andExpect(jsonPath("$.accessToken").isString())
                .andExpect(jsonPath("$.tokenType").value("Bearer")).andExpect(jsonPath("$.expiresIn").value(3600));
    }

    @Test void issuedTokenAuthenticatesRequest() throws Exception {
        signup("customer", "password");
        String token = token("customer");
        long id = accounts.findByLoginId("customer").orElseThrow().getId();
        http.perform(get("/api/v1/customers/me/travel-history").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.role").value("CUSTOMER"));
        var claims = SignedJWT.parse(token).getJWTClaimsSet();
        assertThat(claims.getSubject()).isEqualTo(Long.toString(id));
        assertThat(claims.getClaim("loginId")).isNull();
        assertThat(claims.getExpirationTime().toInstant().getEpochSecond()
                - claims.getIssueTime().toInstant().getEpochSecond()).isEqualTo(3600);
    }

    @Test void missingTokenReturnsAuthenticationRequired() throws Exception {
        http.perform(get("/api/v1/employee/security-probe")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"))
                .andExpect(jsonPath("$.fieldErrors").isEmpty())
                .andExpect(header().string("WWW-Authenticate", "Bearer"));
    }

    @Test void malformedOrBadSignatureTokenReturnsInvalidAccessToken() throws Exception {
        for (String token : new String[]{"not-a-jwt", signedToken("other-test-only-signing-key-32-bytes", Instant.now().plusSeconds(60), "CUSTOMER", "1")}) {
            http.perform(get("/api/v1/employee/security-probe").header("Authorization", "Bearer " + token))
                    .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("INVALID_ACCESS_TOKEN"));
        }
    }

    @Test void malformedAuthorizationHeaderReturnsInvalidAccessToken() throws Exception {
        http.perform(get("/api/v1/employee/security-probe").header("Authorization", "Bearer"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("INVALID_ACCESS_TOKEN"));
    }

    @Test void expiredTokenReturnsAccessTokenExpired() throws Exception {
        http.perform(get("/api/v1/employee/security-probe")
                .header("Authorization", "Bearer " + signedToken(secret, Instant.now().minusSeconds(5), "EMPLOYEE", "1")))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("ACCESS_TOKEN_EXPIRED"));
    }

    @Test void expiredTokenWithBadSignatureStillReturnsInvalidAccessToken() throws Exception {
        http.perform(get("/api/v1/employee/security-probe").header("Authorization", "Bearer " +
                signedToken("other-test-only-signing-key-32-bytes", Instant.now().minusSeconds(5), "EMPLOYEE", "1")))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("INVALID_ACCESS_TOKEN"));
    }

    @Test void invalidIdentityOrRoleCannotAuthenticate() throws Exception {
        for (String token : new String[]{signedToken(secret, Instant.now().plusSeconds(60), "ADMIN", "1"),
                signedToken(secret, Instant.now().plusSeconds(60), "CUSTOMER", "0")}) {
            http.perform(get("/api/v1/customers/me/travel-history").header("Authorization", "Bearer " + token))
                    .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("INVALID_ACCESS_TOKEN"));
        }
    }

    @Test void customerCannotAccessEmployeePath() throws Exception {
        signup("customer", "password");
        http.perform(get("/api/v1/employee/security-probe").header("Authorization", "Bearer " + token("customer")))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test void employeeCanAccessEmployeePath() throws Exception {
        createEmployee();
        http.perform(get("/api/v1/employee/security-probe").header("Authorization", "Bearer " + token("employee")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.role").value("EMPLOYEE"));
    }

    @Test void employeeCannotUseCustomerHistoryPath() throws Exception {
        createEmployee();
        http.perform(get("/api/v1/customers/me/travel-history").header("Authorization", "Bearer " + token("employee")))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test void unapprovedEndpointsAreDeniedByDefault() throws Exception {
        signup("customer", "password");
        http.perform(get("/api/v1/unapproved").header("Authorization", "Bearer " + token("customer")))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test void publicReadPathsDoNotRequireAuthentication() throws Exception {
        for (String path : new String[]{"/api/v1/tours", "/api/v1/tour-schedules"}) {
            http.perform(get(path)).andExpect(status().isOk());
        }
        for (String path : new String[]{"/api/v1/tours/9223372036854775807", "/api/v1/tour-schedules/9223372036854775807"}) {
            http.perform(get(path)).andExpect(status().isNotFound());
        }
    }

    @Test void malformedJsonReturns400CommonError() throws Exception {
        http.perform(post("/api/v1/auth/login").contentType("application/json").content("{"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"))
                .andExpect(jsonPath("$.fieldErrors").isEmpty());
    }

    @Test void numericCredentialIsMalformedRatherThanCoercedToString() throws Exception {
        http.perform(post("/api/v1/auth/login").contentType("application/json").content("{\"loginId\":123,\"password\":\"password\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
    }

    @Test void passwordValidationErrorsDoNotEchoCredentials() throws Exception {
        String secretPassword = "sensitive".repeat(20);
        String body = signup("customer", secretPassword).andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain(secretPassword, "rejectedValue", "exception");
    }

    @Test void invalidQueryParameterReturnsCommonError() throws Exception {
        http.perform(get("/api/v1/tour-schedules").param("tourId", "bad"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_QUERY_PARAMETER"))
                .andExpect(jsonPath("$.fieldErrors").isEmpty());
    }

    @Test void unexpectedFailureDoesNotExposeInternalDetails() throws Exception {
        createEmployee();
        http.perform(get("/api/v1/employee/security-failure").header("Authorization", "Bearer " + token("employee")))
                .andExpect(status().isInternalServerError()).andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.fieldErrors").isEmpty())
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("internal-secret"))));
    }

    @Test void forbiddenReturns403CommonError() throws Exception {
        signup("customer", "password");
        http.perform(get("/api/v1/employee/security-probe").header("Authorization", "Bearer " + token("customer")))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.fieldErrors").isEmpty());
    }

    @Test void bearerAuthenticationDoesNotCreateSession() throws Exception {
        signup("customer", "password");
        var result = http.perform(get("/api/v1/customers/me/travel-history").header("Authorization", "Bearer " + token("customer")))
                .andExpect(status().isOk()).andReturn();
        assertThat(result.getRequest().getSession(false)).isNull();
        assertThat(result.getResponse().getCookie("JSESSIONID")).isNull();
    }

    private ResultActions signup(String loginId, String password) throws Exception {
        return http.perform(post("/api/v1/auth/signup").contentType("application/json")
                .content(json.writeValueAsString(new SignupRequest(loginId, password, "Customer", "Seoul", "010-0000-0000"))));
    }

    private ResultActions login(String loginId, String password) throws Exception {
        return http.perform(post("/api/v1/auth/login").contentType("application/json")
                .content(json.writeValueAsString(new LoginRequest(loginId, password))));
    }

    private String token(String loginId) throws Exception {
        return json.readTree(login(loginId, "password").andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString()).get("accessToken").asString();
    }

    private void createEmployee() {
        accounts.saveAndFlush(new UserAccountJpaEntity("employee", passwords.encode("password"), "Employee", "Seoul", "010", UserRole.EMPLOYEE));
    }

    private String signedToken(String signingKey, Instant expiry, String role, String subject) throws Exception {
        var claims = new JWTClaimsSet.Builder().subject(subject).claim("role", role)
                .issueTime(Date.from(Instant.now().minusSeconds(120))).expirationTime(Date.from(expiry)).build();
        var jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
        jwt.sign(new MACSigner(signingKey.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        return jwt.serialize();
    }

    @TestConfiguration
    static class ProbeConfig {
        @Bean SecurityProbe securityProbe() { return new SecurityProbe(); }
    }

    // Only unimplemented use cases retain probes; catalog/schedule tests use production controllers.
    @RestController
    @TestComponent
    static class SecurityProbe {
        @GetMapping({"/api/v1/employee/security-probe", "/api/v1/customers/me/travel-history"})
        AuthenticatedUser identity(@AuthenticationPrincipal Jwt principal) { return AuthenticatedUser.from(principal); }

        @GetMapping("/api/v1/employee/security-failure")
        void fail() { throw new IllegalStateException("internal-secret"); }
    }
}
