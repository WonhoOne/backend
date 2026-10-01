package com.wonhoone.misterworld.application.reservation;

import com.wonhoone.misterworld.api.dto.ReservationResponse;

/** Internal first-confirmation boundary for B8's future after-commit notification handling. */
public record ReservationCreationResult(ReservationResponse response, boolean scheduleJustConfirmed) {}
