package com.wonhoone.misterworld.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

/** Customer modification: same Configuration shape as create; participants and schedule cannot change. */
public record ReservationConfigurationUpdateRequest(@NotNull @Valid ReservationCreateRequest.Configuration configuration) {}
