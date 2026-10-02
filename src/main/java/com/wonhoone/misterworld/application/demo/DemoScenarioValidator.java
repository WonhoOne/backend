package com.wonhoone.misterworld.application.demo;

import com.wonhoone.misterworld.application.time.BusinessDateProvider;
import com.wonhoone.misterworld.application.tour.TourProductWriteValidator;
import jakarta.validation.Validator;
import java.util.HashSet;
import org.springframework.stereotype.Component;

@Component
public class DemoScenarioValidator {
    private final Validator beanValidation;
    private final TourProductWriteValidator products;
    private final BusinessDateProvider businessDate;

    public DemoScenarioValidator(Validator beanValidation, TourProductWriteValidator products,
                                 BusinessDateProvider businessDate) {
        this.beanValidation = beanValidation; this.products = products; this.businessDate = businessDate;
    }

    public void validate(DemoScenarioManifest manifest) {
        if (manifest == null || !Integer.valueOf(1).equals(manifest.schemaVersion())
                || manifest.products() == null || manifest.products().isEmpty() || manifest.schedules() == null)
            throw invalid();
        var keys = new HashSet<String>();
        for (var product : manifest.products()) {
            if (product == null || product.key() == null || product.key().isBlank() || !keys.add(product.key()))
                throw invalid();
            var request = product.request();
            if (!beanValidation.validate(request).isEmpty()) throw invalid();
            try { products.validate(request); }
            catch (RuntimeException failure) { throw invalid(); }
        }
        var today = businessDate.today();
        for (var schedule : manifest.schedules()) {
            if (schedule == null || !keys.contains(schedule.productKey()) || schedule.startDate() == null
                    || schedule.endDate() == null || schedule.startDate().isAfter(schedule.endDate())
                    || !schedule.startDate().isAfter(today)) throw invalid();
        }
    }
    private static IllegalStateException invalid() {
        // Never attach a Jackson/validation cause containing supplied values or raw JSON.
        return new IllegalStateException("Demo scenario is invalid; check schema, products, prices and future schedules");
    }
}
