package com.wonhoone.misterworld.application.reservation;

import com.wonhoone.misterworld.api.dto.ReservationCreateRequest;
import com.wonhoone.misterworld.api.error.*;
import com.wonhoone.misterworld.domain.*;
import java.util.ArrayList;
import java.util.EnumSet;
import org.springframework.stereotype.Component;

@Component
public class ReservationCreateValidator {
    public TourConfiguration validate(Theme theme, ReservationCreateRequest request) {
        // Required fields and the base range have already passed Bean Validation at the HTTP boundary.
        var selected = request.configuration();
        var errors = new ArrayList<ApiError.FieldError>();
        if (theme == Theme.HONEYMOON_ROMANCE && request.participantCount() % 2 != 0) {
            errors.add(new ApiError.FieldError("participantCount", FieldErrorCode.NOT_ALLOWED,
                    "Honeymoon requires 2, 4, 6, 8 or 10 participants."));
        }
        if (!TourStylePolicy.isAllowed(theme, selected.style())) {
            errors.add(new ApiError.FieldError("configuration.style", FieldErrorCode.NOT_ALLOWED,
                    "This style is not allowed for the selected theme."));
        }
        if (selected.transportOption().capacity() < request.participantCount()) {
            errors.add(new ApiError.FieldError("configuration.transportOption", FieldErrorCode.CAPACITY_EXCEEDED,
                    "Transport capacity must cover all participants."));
        }
        var seen = EnumSet.noneOf(ExtraOption.class);
        if (selected.extraOptions().stream().anyMatch(extra -> !seen.add(extra))) {
            errors.add(new ApiError.FieldError("configuration.extraOptions", FieldErrorCode.DUPLICATE_VALUE,
                    "Extra options must be unique."));
        }
        if (!errors.isEmpty()) throw new RequestValidationException(errors);

        var configuration = TourConfiguration.create(selected.style(), selected.hotelOption(),
                selected.transportOption(), selected.mealOption(), selected.extraOptions());
        // Public field errors do not replace the pure domain's final invariant checks.
        ReservationConfigurationPolicy.validate(theme, request.participantCount(), configuration);
        return configuration;
    }
}
