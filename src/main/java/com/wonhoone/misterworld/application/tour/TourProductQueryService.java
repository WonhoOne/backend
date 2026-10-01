package com.wonhoone.misterworld.application.tour;

import com.wonhoone.misterworld.api.dto.TourProductResponse;
import com.wonhoone.misterworld.api.error.*;
import com.wonhoone.misterworld.infrastructure.persistence.entity.TourProductJpaEntity;
import com.wonhoone.misterworld.infrastructure.persistence.repository.*;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class TourProductQueryService {
    private final TourProductJpaRepository products;
    private final TourProductStylePriceJpaRepository prices;
    private final TourProductResponseFactory responses;

    public TourProductQueryService(TourProductJpaRepository products,
                                   TourProductStylePriceJpaRepository prices,
                                   TourProductResponseFactory responses) {
        this.products = products;
        this.prices = prices;
        this.responses = responses;
    }

    public List<TourProductResponse> list() {
        var catalog = products.findAllByOrderByIdAsc();
        if (catalog.isEmpty()) return List.of();
        var productIds = catalog.stream().map(TourProductJpaEntity::getId).toList();
        var pricesByProduct = prices.findByTourProductIdIn(productIds).stream()
                .collect(Collectors.groupingBy(price -> price.getTourProduct().getId()));
        return catalog.stream().map(product -> responses.create(product,
                pricesByProduct.getOrDefault(product.getId(), List.of()))).toList();
    }

    public TourProductResponse detail(long tourId) {
        InvalidRequestParameterException.requirePositive(tourId);
        var product = products.findById(tourId).orElseThrow(() ->
                new ResourceNotFoundException("TOUR_PRODUCT_NOT_FOUND", "Tour product was not found."));
        return responses.create(product, prices.findByTourProductId(tourId));
    }
}
