package com.wonhoone.misterworld.api.dto;

import com.wonhoone.misterworld.domain.Theme;
import com.wonhoone.misterworld.domain.TourStyle;
import java.util.List;

public record TourProductResponse(Long id, Theme theme, String name, String description,
                                  List<TourStyle> availableStyles, List<StylePrice> stylePrices) {
    public record StylePrice(TourStyle style, long amount, String currency) {}
}
