package com.wonhoone.misterworld.api.controller;

import com.wonhoone.misterworld.api.dto.InventoryAddRequest;
import com.wonhoone.misterworld.api.dto.InventoryResponse;
import com.wonhoone.misterworld.application.inventory.InventoryCommandService;
import com.wonhoone.misterworld.application.inventory.InventoryQueryService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/employee/inventory")
public class EmployeeInventoryController {
    private final InventoryQueryService queries;
    private final InventoryCommandService commands;

    public EmployeeInventoryController(InventoryQueryService queries, InventoryCommandService commands) {
        this.queries = queries;
        this.commands = commands;
    }

    @GetMapping
    public List<InventoryResponse> list() {
        return queries.list();
    }

    @PostMapping
    public InventoryResponse add(@Valid @RequestBody InventoryAddRequest request) {
        return commands.add(request);
    }
}
