package com.wonhoone.misterworld.application.tour;

import com.wonhoone.misterworld.api.dto.TourProductWriteRequest;
import com.wonhoone.misterworld.api.error.*;
import com.wonhoone.misterworld.domain.TourStyle;
import com.wonhoone.misterworld.domain.TourStylePolicy;
import java.util.ArrayList;
import java.util.EnumSet;
import org.springframework.stereotype.Component;

@Component
public class TourProductWriteValidator {
    // Bean Validation checks required fields and storage bounds before this semantic validation.
    public void validate(TourProductWriteRequest request) {
        var allowed = TourStylePolicy.allowedStyles(request.theme());
        var seen = EnumSet.noneOf(TourStyle.class);
        var errors = new ArrayList<ApiError.FieldError>();
        for (int index = 0; index < request.stylePrices().size(); index++) {
            var price = request.stylePrices().get(index);
            String field = "stylePrices[" + index + "]";
            if (!seen.add(price.style())) {
                errors.add(new ApiError.FieldError(field + ".style", FieldErrorCode.DUPLICATE_VALUE,
                        "Each style must occur exactly once."));
            }
            if (!allowed.contains(price.style())) {
                errors.add(new ApiError.FieldError(field + ".style", FieldErrorCode.NOT_ALLOWED,
                        "This style is not allowed for the selected theme."));
            }
            if (!"KRW".equals(price.currency())) {
                errors.add(new ApiError.FieldError(field + ".currency", FieldErrorCode.NOT_ALLOWED,
                        "Only KRW is supported."));
            }
        }
        for (var style : allowed) {
            if (!seen.contains(style)) {
                errors.add(new ApiError.FieldError("stylePrices", FieldErrorCode.REQUIRED,
                        "A price is required for " + style + "."));
            }
        }
        if (!errors.isEmpty()) throw new RequestValidationException(errors);
    }
}
