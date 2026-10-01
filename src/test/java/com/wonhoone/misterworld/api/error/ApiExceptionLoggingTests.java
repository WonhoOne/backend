package com.wonhoone.misterworld.api.error;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.servlet.HandlerMapping;
import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(OutputCaptureExtension.class)
class ApiExceptionLoggingTests {
    @Test void unexpectedErrorDiagnosticsExcludeSensitiveRequestAndExceptionValues(CapturedOutput output) {
        var request = new MockHttpServletRequest("GET", "/api/v1/tours/private-path-value");
        request.setQueryString("profile=private-profile-value");
        request.addHeader("Authorization", "Bearer private-jwt-value");
        request.setContent("private-password-body".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        request.setAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, "/api/v1/tours/{tourId}");
        var response = new ApiExceptionHandler().unexpected(
                new IllegalStateException("private-db-credential-value"), request);
        assertThat(response.getStatusCode().value()).isEqualTo(500);
        assertThat(response.getBody().code()).isEqualTo("INTERNAL_ERROR");
        assertThat(response.getBody().fieldErrors()).isEmpty();
        assertThat(output.getAll()).contains("type=java.lang.IllegalStateException", "method=GET", "path=/api/v1/tours/{tourId}")
                .doesNotContain("private-path-value", "private-profile-value", "private-jwt-value",
                        "private-password-body", "private-db-credential-value");
    }
}
