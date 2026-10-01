package com.wonhoone.misterworld.api.controller;

import com.wonhoone.misterworld.api.dto.TourScheduleResponse;
import com.wonhoone.misterworld.api.error.InvalidRequestParameterException;
import com.wonhoone.misterworld.application.tour.TourScheduleQueryService;
import java.util.List;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/tour-schedules")
public class TourScheduleController {
    private final TourScheduleQueryService schedules;
    public TourScheduleController(TourScheduleQueryService schedules) { this.schedules = schedules; }
    @GetMapping
    public List<TourScheduleResponse> list(@RequestParam(required = false) String tourId) {
        // Keep an explicitly empty query distinct from an absent optional filter.
        if (tourId == null) return schedules.list(null);
        try {
            return schedules.list(Long.valueOf(tourId));
        } catch (NumberFormatException exception) {
            throw new InvalidRequestParameterException();
        }
    }
    @GetMapping("/{scheduleId}")
    public TourScheduleResponse detail(@PathVariable long scheduleId) { return schedules.detail(scheduleId); }
}
