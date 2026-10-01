package com.wonhoone.misterworld.application.tour;

import com.wonhoone.misterworld.api.dto.*;
import com.wonhoone.misterworld.api.error.*;
import com.wonhoone.misterworld.infrastructure.persistence.entity.*;
import com.wonhoone.misterworld.infrastructure.persistence.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class TourProductCommandService {
    private final TourProductJpaRepository products;
    private final TourProductStylePriceJpaRepository prices;
    private final TourProductWriteValidator validator;
    private final TourProductResponseFactory responses;

    public TourProductCommandService(TourProductJpaRepository products,
                                     TourProductStylePriceJpaRepository prices,
                                     TourProductWriteValidator validator, TourProductResponseFactory responses) {
        this.products = products;
        this.prices = prices;
        this.validator = validator;
        this.responses = responses;
    }

    public TourProductResponse create(TourProductWriteRequest request) {
        validator.validate(request);
        var product = products.save(new TourProductJpaEntity(request.theme(), request.name(), request.description()));
        return savePricesAndProject(product, request);
    }

    public TourProductResponse update(long tourId, TourProductWriteRequest request) {
        InvalidRequestParameterException.requirePositive(tourId);
        var product = products.findById(tourId).orElseThrow(() ->
                new ResourceNotFoundException("TOUR_PRODUCT_NOT_FOUND", "Tour product was not found."));
        validator.validate(request);
        product.updateDetails(request.theme(), request.name(), request.description());
        prices.deleteByTourProductId(tourId);
        // Flush deletes before inserts reuse the unique (product, style) key.
        prices.flush();
        return savePricesAndProject(product, request);
    }

    private TourProductResponse savePricesAndProject(TourProductJpaEntity product, TourProductWriteRequest request) {
        var completePrices = request.stylePrices().stream().map(price ->
                new TourProductStylePriceJpaEntity(product, price.style(), price.amount())).toList();
        prices.saveAllAndFlush(completePrices);
        return responses.create(product, completePrices);
    }
}
