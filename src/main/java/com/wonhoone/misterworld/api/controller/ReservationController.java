package com.wonhoone.misterworld.api.controller;

import com.wonhoone.misterworld.api.dto.*;
import com.wonhoone.misterworld.application.reservation.*;
import com.wonhoone.misterworld.security.AuthenticatedUser;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/reservations")
public class ReservationController {
    private final ReservationCommandService commands;
    private final ReservationQueryService queries;

    public ReservationController(ReservationCommandService commands, ReservationQueryService queries) {
        this.commands = commands;
        this.queries = queries;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ReservationResponse create(@AuthenticationPrincipal Jwt principal,
                                      @Valid @RequestBody ReservationCreateRequest request) {
        return commands.create(AuthenticatedUser.from(principal).id(), request).response();
    }

    @GetMapping("/{reservationId}")
    public ReservationResponse detail(@AuthenticationPrincipal Jwt principal, @PathVariable long reservationId) {
        return queries.detail(AuthenticatedUser.from(principal).id(), reservationId);
    }
}
