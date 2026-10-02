package com.wonhoone.misterworld.application.demo;

import com.wonhoone.misterworld.api.dto.TourProductWriteRequest;
import com.wonhoone.misterworld.domain.Theme;
import java.time.LocalDate;
import java.util.List;

/** Internal references only: keys are never persisted or exposed by REST. No account or SMS fields. */
public record DemoScenarioManifest(Integer schemaVersion, List<Product> products, List<Schedule> schedules) {
    public record Product(String key, Theme theme, String name, String description,
                          List<TourProductWriteRequest.StylePrice> stylePrices) {
        public TourProductWriteRequest request() {
            return new TourProductWriteRequest(theme, name, description, stylePrices);
        }
    }
    public record Schedule(String productKey, LocalDate startDate, LocalDate endDate) {}
}
