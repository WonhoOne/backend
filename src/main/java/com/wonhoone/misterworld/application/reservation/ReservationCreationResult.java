package com.wonhoone.misterworld.application.reservation;

import com.wonhoone.misterworld.api.dto.ReservationResponse;

/** Internal first-transition result. Durable B8 outbox capture happens inside the command transaction. */
public record ReservationCreationResult(ReservationResponse response, boolean scheduleJustConfirmed) {}
