package com.wonhoone.misterworld.security;

import java.util.List;
import org.springframework.context.annotation.*;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {
    @Bean PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(); }

    @Bean
    SecurityFilterChain apiSecurity(HttpSecurity http, SecurityErrorHandlers errors) throws Exception {
        var principal = new JwtAuthenticationConverter();
        principal.setJwtGrantedAuthoritiesConverter(jwt ->
                List.of(new SimpleGrantedAuthority("ROLE_" + jwt.getClaimAsString("role"))));
        return http
                // Only explicit Bearer headers authenticate; no automatically sent cookie credentials.
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .requestCache(cache -> cache.disable())
                .authorizeHttpRequests(access -> access
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/signup", "/api/v1/auth/login").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/tours", "/api/v1/tours/{tourId}",
                                "/api/v1/tour-schedules", "/api/v1/tour-schedules/{scheduleId}").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/reservations").hasRole("CUSTOMER")
                        .requestMatchers(HttpMethod.GET, "/api/v1/reservations/{reservationId}",
                                "/api/v1/customers/me/travel-history").hasRole("CUSTOMER")
                        .requestMatchers("/api/v1/employee/**").hasRole("EMPLOYEE")
                        .anyRequest().denyAll())
                .exceptionHandling(handler -> handler.authenticationEntryPoint(errors).accessDeniedHandler(errors))
                .oauth2ResourceServer(resource -> resource
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(principal))
                        .authenticationEntryPoint(errors).accessDeniedHandler(errors))
                .build();
    }
}
