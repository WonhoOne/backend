package com.wonhoone.misterworld.api.controller;

import com.wonhoone.misterworld.api.dto.*;
import com.wonhoone.misterworld.application.tour.*;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/employee/tours")
public class EmployeeTourController {
    private final TourProductQueryService queries;
    private final TourProductCommandService commands;
    public EmployeeTourController(TourProductQueryService queries, TourProductCommandService commands) {
        this.queries = queries;
        this.commands = commands;
    }
    @GetMapping
    public List<TourProductResponse> list() { return queries.list(); }
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TourProductResponse create(@Valid @RequestBody TourProductWriteRequest request) {
        return commands.create(request);
    }
    @PutMapping("/{tourId}")
    public TourProductResponse update(@PathVariable long tourId, @Valid @RequestBody TourProductWriteRequest request) {
        return commands.update(tourId, request);
    }
}
