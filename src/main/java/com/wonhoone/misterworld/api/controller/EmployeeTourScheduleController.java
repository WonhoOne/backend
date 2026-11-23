package com.wonhoone.misterworld.api.controller;

import com.wonhoone.misterworld.api.dto.EmployeeTourScheduleResponse;
import com.wonhoone.misterworld.application.tour.TourScheduleOperationService;
import java.util.List;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/employee/tour-schedules")
public class EmployeeTourScheduleController {
    private final TourScheduleOperationService operations;

    public EmployeeTourScheduleController(TourScheduleOperationService operations) {
        this.operations = operations;
    }

    @GetMapping
    public List<EmployeeTourScheduleResponse> list() {
        return operations.list();
    }

    @PostMapping("/{scheduleId}/departure")
    public EmployeeTourScheduleResponse.Departure depart(@PathVariable long scheduleId) {
        return operations.depart(scheduleId);
    }

    @PostMapping("/{scheduleId}/completion")
    public EmployeeTourScheduleResponse.Completion complete(@PathVariable long scheduleId) {
        return operations.complete(scheduleId);
    }
}
