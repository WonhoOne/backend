package com.wonhoone.misterworld.infrastructure.persistence;

import com.wonhoone.misterworld.domain.InventoryItemType;
import com.wonhoone.misterworld.domain.Theme;
import com.wonhoone.misterworld.domain.TourStyle;
import com.wonhoone.misterworld.domain.UserRole;
import com.wonhoone.misterworld.infrastructure.persistence.entity.InventoryJpaEntity;
import com.wonhoone.misterworld.infrastructure.persistence.entity.TourProductJpaEntity;
import com.wonhoone.misterworld.infrastructure.persistence.entity.TourProductStylePriceJpaEntity;
import com.wonhoone.misterworld.infrastructure.persistence.entity.TourScheduleJpaEntity;
import com.wonhoone.misterworld.infrastructure.persistence.entity.UserAccountJpaEntity;
import com.wonhoone.misterworld.infrastructure.persistence.repository.InventoryJpaRepository;
import com.wonhoone.misterworld.infrastructure.persistence.repository.TourProductJpaRepository;
import com.wonhoone.misterworld.infrastructure.persistence.repository.TourProductStylePriceJpaRepository;
import com.wonhoone.misterworld.infrastructure.persistence.repository.TourScheduleJpaRepository;
import com.wonhoone.misterworld.infrastructure.persistence.repository.UserAccountJpaRepository;
import jakarta.persistence.EntityManager;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.EnumSet;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class PersistenceFoundationTests {
    @Autowired
    private UserAccountJpaRepository userAccounts;
    @Autowired
    private TourProductJpaRepository tourProducts;
    @Autowired
    private TourProductStylePriceJpaRepository stylePrices;
    @Autowired
    private TourScheduleJpaRepository tourSchedules;
    @Autowired
    private InventoryJpaRepository inventory;
    @Autowired
    private EntityManager entityManager;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private Flyway flyway;

    @Test
    void flywayAppliesCoreSchemaAndInventoryCatalogBeforeJpaStarts() {
        assertEquals(3, flyway.info().applied().length);
        assertEquals("3", flyway.info().current().getVersion().getVersion());
        assertEquals(0, flyway.info().pending().length);
        assertTrue(flyway.validateWithResult().validationSuccessful);
        assertEquals(0, userAccounts.count());
        assertEquals(0, tourProducts.count());
        assertEquals(0, stylePrices.count());
        assertEquals(0, tourSchedules.count());
    }

    @Test
    void customerAccountRetainsLoginHashProfileAndRole() {
        UserAccountJpaEntity saved = userAccounts.saveAndFlush(account("customer01", UserRole.CUSTOMER));
        entityManager.clear();

        UserAccountJpaEntity reloaded = userAccounts.findByLoginId("customer01").orElseThrow();
        assertTrue(reloaded.getId() > 0);
        assertEquals(saved.getId(), reloaded.getId());
        assertEquals("customer01", reloaded.getLoginId());
        assertEquals("encoded-hash-fixture", reloaded.getPasswordHash());
        assertEquals("Customer", reloaded.getName());
        assertEquals("Seoul", reloaded.getAddress());
        assertEquals("010-0000-0000", reloaded.getContact());
        assertEquals(UserRole.CUSTOMER, reloaded.getRole());
        assertTrue(userAccounts.existsByLoginId("customer01"));
        assertFalse(userAccounts.existsByLoginId("missing"));
        assertEquals("CUSTOMER", jdbc.queryForObject(
                "SELECT role FROM user_account WHERE id = ?", String.class, saved.getId()));
    }

    @Test
    void employeeAccountUsesTheSameStorageWithEmployeeRole() {
        userAccounts.saveAndFlush(account("employee01", UserRole.EMPLOYEE));
        entityManager.clear();

        assertEquals(UserRole.EMPLOYEE, userAccounts.findByLoginId("employee01").orElseThrow().getRole());
    }

    @Test
    void loginIdCannotBelongToTwoAccounts() {
        userAccounts.saveAndFlush(account("duplicate", UserRole.CUSTOMER));

        assertThrows(DataIntegrityViolationException.class,
                () -> userAccounts.saveAndFlush(account("duplicate", UserRole.EMPLOYEE)));
    }

    @Test
    void multipleProductsCanBelongToTheSameTheme() {
        TourProductJpaEntity first = tourProducts.saveAndFlush(product("Golf trip"));
        tourProducts.saveAndFlush(product("Another golf trip"));
        entityManager.clear();

        TourProductJpaEntity reloaded = tourProducts.findById(first.getId()).orElseThrow();
        assertTrue(reloaded.getId() > 0);
        assertEquals(Theme.GOLF_CHALLENGE, reloaded.getTheme());
        assertEquals("Golf trip", reloaded.getName());
        assertEquals("Golf resort journey", reloaded.getDescription());
        assertEquals(2, tourProducts.count());
        assertEquals("GOLF_CHALLENGE", jdbc.queryForObject(
                "SELECT theme FROM tour_product WHERE id = ?", String.class, first.getId()));
    }

    @Test
    void productRetainsOneWholeKrwPriceForEachStoredStyle() {
        TourProductJpaEntity product = tourProducts.saveAndFlush(product("Golf trip"));
        stylePrices.save(new TourProductStylePriceJpaEntity(product, TourStyle.CLASSIC, 1_200_000L));
        stylePrices.save(new TourProductStylePriceJpaEntity(product, TourStyle.GRAND, 1_800_000L));
        stylePrices.saveAndFlush(new TourProductStylePriceJpaEntity(product, TourStyle.PREMIUM, 3_000_000_000L));
        entityManager.clear();

        var reloaded = stylePrices.findByTourProductId(product.getId());
        assertEquals(Map.of(TourStyle.CLASSIC, 1_200_000L, TourStyle.GRAND, 1_800_000L,
                        TourStyle.PREMIUM, 3_000_000_000L),
                reloaded.stream().collect(Collectors.toMap(
                        TourProductStylePriceJpaEntity::getStyle, TourProductStylePriceJpaEntity::getAmount)));
        reloaded.forEach(price -> {
            assertTrue(price.getId() > 0);
            assertEquals(product.getId(), price.getTourProduct().getId());
        });
    }

    @Test
    void tourProductStylePriceIsUniquePerProductAndStyle() {
        TourProductJpaEntity product = tourProducts.saveAndFlush(product("Golf trip"));
        stylePrices.saveAndFlush(new TourProductStylePriceJpaEntity(product, TourStyle.GRAND, 1_800_000L));

        assertThrows(DataIntegrityViolationException.class, () -> stylePrices.saveAndFlush(
                new TourProductStylePriceJpaEntity(product, TourStyle.GRAND, 2_000_000L)));
    }

    @Test
    void differentProductsMayHaveTheirOwnPriceForTheSameStyle() {
        TourProductJpaEntity first = tourProducts.saveAndFlush(product("First trip"));
        TourProductJpaEntity second = tourProducts.saveAndFlush(product("Second trip"));

        stylePrices.saveAndFlush(new TourProductStylePriceJpaEntity(first, TourStyle.GRAND, 1_800_000L));
        stylePrices.saveAndFlush(new TourProductStylePriceJpaEntity(second, TourStyle.GRAND, 2_000_000L));

        assertEquals(2, stylePrices.count());
    }

    @Test
    void databaseRejectsNonpositiveStylePriceEvenWhenConstructorIsBypassed() {
        TourProductJpaEntity product = tourProducts.saveAndFlush(product("Golf trip"));

        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(
                "INSERT INTO tour_product_style_price (tour_product_id, style, amount) VALUES (?, ?, ?)",
                product.getId(), "GRAND", 0L));
    }

    @Test
    void scheduleRetainsProductDatesAndConfirmationTruth() {
        TourProductJpaEntity product = tourProducts.saveAndFlush(product("Golf trip"));
        LocalDate start = LocalDate.of(2026, 11, 10);
        LocalDate end = LocalDate.of(2026, 11, 14);
        TourScheduleJpaEntity pending = tourSchedules.saveAndFlush(
                new TourScheduleJpaEntity(product, start, end, false));
        TourScheduleJpaEntity confirmed = tourSchedules.saveAndFlush(
                new TourScheduleJpaEntity(product, start, end, true));
        entityManager.clear();

        TourScheduleJpaEntity reloaded = tourSchedules.findById(pending.getId()).orElseThrow();
        assertTrue(reloaded.getId() > 0);
        assertEquals(product.getId(), reloaded.getTourProduct().getId());
        assertEquals(start, reloaded.getStartDate());
        assertEquals(end, reloaded.getEndDate());
        assertFalse(reloaded.isConfirmed());
        assertTrue(tourSchedules.findById(confirmed.getId()).orElseThrow().isConfirmed());
    }

    @Test
    void scheduleDefaultsToUnconfirmedWhenNoConfirmationValueIsSupplied() {
        TourProductJpaEntity product = tourProducts.saveAndFlush(product("Golf trip"));
        jdbc.update("INSERT INTO tour_schedule (tour_product_id, start_date, end_date) VALUES (?, ?, ?)",
                product.getId(), LocalDate.of(2026, 11, 10), LocalDate.of(2026, 11, 10));

        assertFalse(tourSchedules.findAll().getFirst().isConfirmed());
    }

    @Test
    void databaseRejectsScheduleWhoseEndIsBeforeItsStart() {
        TourProductJpaEntity product = tourProducts.saveAndFlush(product("Golf trip"));

        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(
                "INSERT INTO tour_schedule (tour_product_id, start_date, end_date) VALUES (?, ?, ?)",
                product.getId(), LocalDate.of(2026, 11, 14), LocalDate.of(2026, 11, 10)));
    }

    @Test
    void scheduleCannotReferToAnAbsentProduct() {
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(
                "INSERT INTO tour_schedule (tour_product_id, start_date, end_date) VALUES (?, ?, ?)",
                Long.MAX_VALUE, LocalDate.of(2026, 11, 10), LocalDate.of(2026, 11, 14)));
    }

    @Test
    void stylePriceCannotReferToAnAbsentProduct() {
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(
                "INSERT INTO tour_product_style_price (tour_product_id, style, amount) VALUES (?, ?, ?)",
                Long.MAX_VALUE, "GRAND", 1_800_000L));
    }

    @Test
    void inventoryCatalogContainsAllFourCanonicalItemTypesWithZeroStock() {
        var catalog = inventory.findAll();

        assertEquals(4, catalog.size());
        assertEquals(EnumSet.allOf(InventoryItemType.class),
                catalog.stream().map(InventoryJpaEntity::getItemType).collect(Collectors.toSet()));
        catalog.forEach(item -> {
            assertTrue(item.getId() > 0);
            assertEquals(0L, item.getQuantity());
        });
        assertEquals(InventoryItemType.SCARF,
                inventory.findByItemType(InventoryItemType.SCARF).orElseThrow().getItemType());
    }

    @Test
    void inventoryItemTypeCannotHaveAnotherStockAggregate() {
        assertThrows(DataIntegrityViolationException.class,
                () -> inventory.saveAndFlush(new InventoryJpaEntity(InventoryItemType.SCARF, 5L)));
    }

    @Test
    void databaseRejectsNegativeInventoryBalance() {
        assertThrows(DataIntegrityViolationException.class,
                () -> jdbc.update("UPDATE inventory SET quantity = ? WHERE item_type = ?", -1L, "SCARF"));
    }

    @Test
    void inventoryBalanceRetainsWholeQuantitiesBeyondIntegerRange() {
        jdbc.update("UPDATE inventory SET quantity = ? WHERE item_type = ?", 3_000_000_000L, "SCARF");
        entityManager.clear();

        assertEquals(3_000_000_000L,
                inventory.findByItemType(InventoryItemType.SCARF).orElseThrow().getQuantity());
    }

    private static UserAccountJpaEntity account(String loginId, UserRole role) {
        return new UserAccountJpaEntity(loginId, "encoded-hash-fixture",
                "Customer", "Seoul", "010-0000-0000", role);
    }

    private static TourProductJpaEntity product(String name) {
        return new TourProductJpaEntity(Theme.GOLF_CHALLENGE, name, "Golf resort journey");
    }
}
