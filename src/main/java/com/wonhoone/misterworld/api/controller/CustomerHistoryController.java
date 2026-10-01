package com.wonhoone.misterworld.api.controller;

import com.wonhoone.misterworld.api.dto.TravelHistoryResponse;
import com.wonhoone.misterworld.application.reservation.TravelHistoryQueryService;
import com.wonhoone.misterworld.security.AuthenticatedUser;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/customers/me/travel-history")
public class CustomerHistoryController {
    private final TravelHistoryQueryService history;

    public CustomerHistoryController(TravelHistoryQueryService history) { this.history = history; }

    @GetMapping
    public List<TravelHistoryResponse> list(@AuthenticationPrincipal Jwt principal) {
        return history.list(AuthenticatedUser.from(principal).id());
    }
}
