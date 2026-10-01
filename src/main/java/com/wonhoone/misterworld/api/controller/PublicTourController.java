package com.wonhoone.misterworld.api.controller;

import com.wonhoone.misterworld.api.dto.TourProductResponse;
import com.wonhoone.misterworld.application.tour.TourProductQueryService;
import java.util.List;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/tours")
public class PublicTourController {
    private final TourProductQueryService tours;
    public PublicTourController(TourProductQueryService tours) { this.tours = tours; }
    @GetMapping
    public List<TourProductResponse> list() { return tours.list(); }
    @GetMapping("/{tourId}")
    public TourProductResponse detail(@PathVariable long tourId) { return tours.detail(tourId); }
}
