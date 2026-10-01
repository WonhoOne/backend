package com.wonhoone.misterworld.inventory;

import com.wonhoone.misterworld.application.inventory.InventoryCommandService;
import com.wonhoone.misterworld.domain.InventoryItemType;
import com.wonhoone.misterworld.domain.UserRole;
import com.wonhoone.misterworld.infrastructure.persistence.repository.InventoryJpaRepository;
import com.wonhoone.misterworld.security.JwtTokenService;
import jakarta.servlet.Filter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.ObjectMapper;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:inventory-api;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE;LOCK_TIMEOUT=5000")
@ActiveProfiles("test")
abstract class InventoryIntegrationSupport {
    static final String PATH = "/api/v1/employee/inventory";
    @Autowired WebApplicationContext context;
    @Autowired @Qualifier("springSecurityFilterChain") Filter securityFilter;
    @Autowired InventoryJpaRepository inventory;
    @Autowired InventoryCommandService commands;
    @Autowired PlatformTransactionManager transactions;
    @Autowired JwtTokenService tokens;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    MockMvc http;
    String employee;
    String customer;

    @BeforeEach void prepareInventory() {
        resetQuantities();
        http = MockMvcBuilders.webAppContextSetup(context).addFilters(securityFilter).build();
        employee = "Bearer " + tokens.issue(1L, UserRole.EMPLOYEE).value();
        customer = "Bearer " + tokens.issue(2L, UserRole.CUSTOMER).value();
    }

    @AfterEach void resetQuantities() {
        jdbc.update("UPDATE inventory SET quantity = 0");
    }

    ResultActions list(String authorization) throws Exception {
        var request = get(PATH);
        if (authorization != null) request.header("Authorization", authorization);
        return http.perform(request);
    }

    ResultActions add(String body) throws Exception { return add(body, employee); }

    ResultActions add(String body, String authorization) throws Exception {
        var request = post(PATH).contentType("application/json").content(body);
        if (authorization != null) request.header("Authorization", authorization);
        return http.perform(request);
    }

    long quantity(InventoryItemType type) {
        return inventory.findByItemType(type).orElseThrow().getQuantity();
    }

    void balance(InventoryItemType type, long quantity) {
        jdbc.update("UPDATE inventory SET quantity = ? WHERE item_type = ?", quantity, type.name());
    }
}
