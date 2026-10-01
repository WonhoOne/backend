package com.wonhoone.misterworld.application.tour;

import com.wonhoone.misterworld.api.dto.TourProductResponse;
import com.wonhoone.misterworld.domain.TourStylePolicy;
import com.wonhoone.misterworld.infrastructure.persistence.entity.*;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class TourProductResponseFactory {
    public TourProductResponse create(TourProductJpaEntity product,
                                      List<TourProductStylePriceJpaEntity> prices) {
        var allowed = TourStylePolicy.allowedStyles(product.getTheme());
        var byStyle = prices.stream().collect(Collectors.toMap(
                TourProductStylePriceJpaEntity::getStyle, TourProductStylePriceJpaEntity::getAmount));
        // An incomplete stored catalog must not be published as a valid contract representation.
        if (byStyle.size() != allowed.size() || !byStyle.keySet().containsAll(allowed)) {
            throw new IllegalStateException("Stored product prices must match the allowed styles.");
        }
        var responsePrices = allowed.stream().map(style ->
                new TourProductResponse.StylePrice(style, byStyle.get(style), "KRW")).toList();
        return new TourProductResponse(product.getId(), product.getTheme(), product.getName(),
                product.getDescription(), allowed, responsePrices);
    }
}
