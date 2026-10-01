package com.wonhoone.misterworld.inventory;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.transaction.support.TransactionTemplate;
import static com.wonhoone.misterworld.domain.InventoryItemType.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class InventoryApiIntegrationTests extends InventoryIntegrationSupport {
    @Test void employeeCanListInventory() throws Exception {
        var body = json.readTree(list(employee).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(body.isArray()).isTrue();
        assertThat(body.size()).isEqualTo(4);
        var types = new ArrayList<String>();
        body.forEach(item -> types.add(item.get("itemType").asString()));
        assertThat(types).containsExactlyInAnyOrder("COUPLE_TSHIRT", "GINSENG_GIFT", "GOLF_BALL", "SCARF");
    }

    @Test void inventoryListIncludesZeroQuantityItems() throws Exception {
        var body = json.readTree(list(employee).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(body.size()).isEqualTo(4);
        body.forEach(item -> assertThat(item.get("quantity").asLong()).isZero());
    }

    @Test void inventoryListIsOrderedByIdAscending() throws Exception {
        var body = json.readTree(list(employee).andReturn().getResponse().getContentAsString());
        var ids = new ArrayList<Long>();
        body.forEach(item -> ids.add(item.get("id").asLong()));
        assertThat(ids).hasSize(4).isSorted().doesNotHaveDuplicates();
    }

    @Test void inventoryResponsesHaveExactlyThreeFields() throws Exception {
        var body = json.readTree(list(employee).andReturn().getResponse().getContentAsString());
        body.forEach(item -> assertThat(item.propertyNames()).containsExactlyInAnyOrder("id", "itemType", "quantity"));
        var updated = json.readTree(add("{\"itemType\":\"SCARF\",\"quantity\":5}")
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(updated.propertyNames()).containsExactlyInAnyOrder("id", "itemType", "quantity");
    }

    @Test void employeeCanAddInventoryQuantity() throws Exception {
        long id = inventory.findByItemType(COUPLE_TSHIRT).orElseThrow().getId();
        add("{\"itemType\":\"COUPLE_TSHIRT\",\"quantity\":5}").andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id)).andExpect(jsonPath("$.itemType").value("COUPLE_TSHIRT"))
                .andExpect(jsonPath("$.quantity").value(5));
        assertThat(quantity(COUPLE_TSHIRT)).isEqualTo(5);
        assertThat(inventory.count()).isEqualTo(4);
    }

    @Test void inventoryPostAddsRatherThanReplaces() throws Exception {
        balance(COUPLE_TSHIRT, 10);
        add("{\"itemType\":\"COUPLE_TSHIRT\",\"quantity\":5}").andExpect(status().isOk())
                .andExpect(jsonPath("$.quantity").value(15));
        assertThat(quantity(COUPLE_TSHIRT)).isEqualTo(15);
    }

    @Test void repeatedAddsAccumulate() throws Exception {
        for (long amount : List.of(3L, 4L, 5L)) {
            add("{\"itemType\":\"COUPLE_TSHIRT\",\"quantity\":" + amount + "}").andExpect(status().isOk());
        }
        assertThat(quantity(COUPLE_TSHIRT)).isEqualTo(12);
        assertThat(inventory.count()).isEqualTo(4);
    }

    @Test void addingOneItemDoesNotChangeOtherItems() throws Exception {
        add("{\"itemType\":\"COUPLE_TSHIRT\",\"quantity\":5}").andExpect(status().isOk());
        assertThat(quantity(COUPLE_TSHIRT)).isEqualTo(5);
        for (var other : List.of(GINSENG_GIFT, GOLF_BALL, SCARF)) assertThat(quantity(other)).isZero();
    }

    @Test void quantitiesBeyondIntegerRangeAreSupported() throws Exception {
        add("{\"itemType\":\"SCARF\",\"quantity\":3000000000}").andExpect(status().isOk())
                .andExpect(jsonPath("$.quantity").value(3_000_000_000L));
        assertThat(quantity(SCARF)).isEqualTo(3_000_000_000L);
    }

    @Test void exactLongMaximumIsRepresentable() throws Exception {
        balance(SCARF, Long.MAX_VALUE - 2);
        add("{\"itemType\":\"SCARF\",\"quantity\":2}").andExpect(status().isOk())
                .andExpect(jsonPath("$.quantity").value(Long.MAX_VALUE));
        assertThat(quantity(SCARF)).isEqualTo(Long.MAX_VALUE);
    }

    @Test void inventoryOverflowReturnsInternalErrorAndRollsBack() throws Exception {
        long before = Long.MAX_VALUE - 2;
        balance(COUPLE_TSHIRT, before);
        assertSafeInternalError(add("{\"itemType\":\"COUPLE_TSHIRT\",\"quantity\":5}"));
        assertThat(quantity(COUPLE_TSHIRT)).isEqualTo(before);
        assertThat(inventory.count()).isEqualTo(4);
    }

    @Test void missingFixedCatalogRowFailsSafelyWithoutCreatingReplacement() {
        // Roll back the broken fixture, preserving V2's original catalog identity.
        new TransactionTemplate(transactions).executeWithoutResult(transaction -> {
            try {
                jdbc.update("DELETE FROM inventory WHERE item_type = ?", SCARF.name());
                assertSafeInternalError(add("{\"itemType\":\"SCARF\",\"quantity\":5}"));
                assertThat(inventory.findByItemType(SCARF)).isEmpty();
                assertThat(inventory.count()).isEqualTo(3);
            } catch (Exception exception) {
                throw new AssertionError(exception);
            } finally {
                transaction.setRollbackOnly();
            }
        });
        assertThat(inventory.findByItemType(SCARF)).isPresent();
        assertThat(inventory.count()).isEqualTo(4);
    }

    @ParameterizedTest @ValueSource(longs = {0, -1, -5})
    void nonpositiveQuantityIsRejected(long amount) throws Exception {
        add("{\"itemType\":\"SCARF\",\"quantity\":" + amount + "}").andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("quantity"))
                .andExpect(jsonPath("$.fieldErrors[0].code").value("OUT_OF_RANGE"));
        assertThat(quantity(SCARF)).isZero();
    }

    @ParameterizedTest @ValueSource(strings = {"{}", "{\"quantity\":1}", "{\"itemType\":null,\"quantity\":1}"})
    void missingItemTypeIsRejected(String body) throws Exception {
        add(body).andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("itemType"))
                .andExpect(jsonPath("$.fieldErrors[0].code").value("REQUIRED"));
    }

    @ParameterizedTest @ValueSource(strings = {"{\"itemType\":\"SCARF\"}", "{\"itemType\":\"SCARF\",\"quantity\":null}"})
    void missingQuantityIsRejected(String body) throws Exception {
        add(body).andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("quantity"))
                .andExpect(jsonPath("$.fieldErrors[0].code").value("REQUIRED"));
    }

    @ParameterizedTest @ValueSource(strings = {
            "{\"itemType\":\"UNKNOWN\",\"quantity\":1}",
            "{\"itemType\":\"SCARF\",\"quantity\":1.5}",
            "{\"itemType\":\"SCARF\",\"quantity\":\"5\"}",
            "{\"itemType\":\"SCARF\",\"quantity\":1,\"delta\":1}",
            "{\"itemType\":\"SCARF\",\"quantity\":9223372036854775808}"})
    void malformedRequestsAreRejected(String body) throws Exception {
        add(body).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"))
                .andExpect(jsonPath("$.fieldErrors").isEmpty());
        assertThat(quantity(SCARF)).isZero();
        assertThat(inventory.count()).isEqualTo(4);
    }

    @Test void anonymousCannotListInventory() throws Exception {
        list(null).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
    }
    @Test void customerCannotListInventory() throws Exception {
        list(customer).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }
    @Test void anonymousCannotAddInventory() throws Exception {
        add("{\"itemType\":\"SCARF\",\"quantity\":5}", null).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
        assertThat(quantity(SCARF)).isZero();
    }
    @Test void customerCannotAddInventory() throws Exception {
        add("{\"itemType\":\"SCARF\",\"quantity\":5}", customer).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        assertThat(quantity(SCARF)).isZero();
    }

    private void assertSafeInternalError(org.springframework.test.web.servlet.ResultActions result) throws Exception {
        String body = result.andExpect(status().isInternalServerError()).andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.fieldErrors").isEmpty()).andReturn().getResponse().getContentAsString();
        assertThat(json.readTree(body).get("message").asString()).isEqualTo("An unexpected error occurred.");
        assertThat(body).doesNotContain("ArithmeticException", "IllegalStateException", "overflow", "SQL", "catalog",
                Long.toString(Long.MAX_VALUE), Long.toString(Long.MAX_VALUE - 2));
    }
}
