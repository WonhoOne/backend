package com.wonhoone.misterworld.domain;

import java.util.Objects;

public record TourProduct(Theme theme) {
    public TourProduct {
        Objects.requireNonNull(theme, "theme must not be null");
    }
}
