package com.wonhoone.misterworld.security;

import com.wonhoone.misterworld.api.error.ApiError;
import jakarta.servlet.http.*;
import java.io.IOException;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.jwt.JwtValidationException;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
public class SecurityErrorHandlers implements AuthenticationEntryPoint, AccessDeniedHandler {
    private final ObjectMapper json;
    public SecurityErrorHandlers(ObjectMapper json) { this.json = json; }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException exception) throws IOException {
        String code = request.getHeader("Authorization") == null
                ? "AUTHENTICATION_REQUIRED" : "INVALID_ACCESS_TOKEN";
        // Only typed Spring APIs are inspected; no exception message matching or reflection.
        if (exception instanceof InvalidBearerTokenException
                && exception.getCause() instanceof JwtValidationException validation
                && validation.getErrors().stream().anyMatch(error -> JwtTokenService.EXPIRED_ERROR.equals(error.getErrorCode()))) {
            code = "ACCESS_TOKEN_EXPIRED";
        }
        response.setHeader("WWW-Authenticate", "Bearer");
        write(response, 401, ApiError.of(code, "A valid access token is required."));
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       org.springframework.security.access.AccessDeniedException exception) throws IOException {
        write(response, 403, ApiError.of("FORBIDDEN", "You do not have permission to access this resource."));
    }

    private void write(HttpServletResponse response, int status, ApiError error) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(json.writeValueAsString(error));
    }
}
