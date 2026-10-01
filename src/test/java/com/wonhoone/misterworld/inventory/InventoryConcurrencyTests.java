package com.wonhoone.misterworld.inventory;

import com.wonhoone.misterworld.api.dto.InventoryAddRequest;
import com.wonhoone.misterworld.domain.InventoryItemType;
import java.util.List;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.transaction.support.TransactionTemplate;
import static com.wonhoone.misterworld.domain.InventoryItemType.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Timeout(30)
class InventoryConcurrencyTests extends InventoryIntegrationSupport {
    @Test void concurrentAddsToSameItemDoNotLoseUpdates() throws Exception {
        var responses = concurrentAdds(COUPLE_TSHIRT, COUPLE_TSHIRT);
        assertThat(quantity(COUPLE_TSHIRT)).isEqualTo(12);
        assertThat(responses).contains(12L);
        assertThat(responses.stream().filter(value -> value != 12L).toList()).hasSize(1)
                .allMatch(value -> value == 5L || value == 7L);
        assertThat(inventory.count()).isEqualTo(4);
    }

    @Test void differentItemConcurrentAddsKeepIndependentState() throws Exception {
        assertThat(concurrentAdds(COUPLE_TSHIRT, GOLF_BALL)).containsExactly(5L, 7L);
        assertThat(quantity(COUPLE_TSHIRT)).isEqualTo(5);
        assertThat(quantity(GOLF_BALL)).isEqualTo(7);
        assertThat(quantity(GINSENG_GIFT)).isZero();
        assertThat(quantity(SCARF)).isZero();
    }

    @Test void heldInventoryLockBlocksSameItemAddUntilCommit() throws Exception {
        var acquired = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var secondStarted = new CountDownLatch(1);
        var workers = Executors.newFixedThreadPool(2);
        try {
            var holder = workers.submit(() -> new TransactionTemplate(transactions).execute(transaction -> {
                var row = inventory.findByItemTypeForUpdate(COUPLE_TSHIRT).orElseThrow();
                row.addQuantity(5);
                inventory.flush();
                acquired.countDown();
                await(release);
                return null;
            }));
            assertTrue(acquired.await(5, TimeUnit.SECONDS));
            var second = workers.submit(() -> {
                secondStarted.countDown();
                return commands.add(new InventoryAddRequest(COUPLE_TSHIRT, 7L));
            });
            assertTrue(secondStarted.await(5, TimeUnit.SECONDS));
            assertThrows(TimeoutException.class, () -> second.get(300, TimeUnit.MILLISECONDS));
            release.countDown();
            holder.get(10, TimeUnit.SECONDS);
            assertThat(second.get(10, TimeUnit.SECONDS).quantity()).isEqualTo(12);
            assertThat(quantity(COUPLE_TSHIRT)).isEqualTo(12);
        } finally {
            release.countDown();
            workers.shutdownNow();
            assertTrue(workers.awaitTermination(10, TimeUnit.SECONDS));
        }
    }

    private List<Long> concurrentAdds(InventoryItemType firstType, InventoryItemType secondType) throws Exception {
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        var workers = Executors.newFixedThreadPool(2);
        try {
            var first = workers.submit(() -> postAfterStart(firstType, 5, ready, start));
            var second = workers.submit(() -> postAfterStart(secondType, 7, ready, start));
            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();
            return List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS));
        } finally {
            start.countDown();
            workers.shutdownNow();
            assertTrue(workers.awaitTermination(10, TimeUnit.SECONDS));
        }
    }

    private long postAfterStart(InventoryItemType type, long amount, CountDownLatch ready, CountDownLatch start) throws Exception {
        ready.countDown();
        await(start);
        String body = add("{\"itemType\":\"" + type + "\",\"quantity\":" + amount + "}")
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("quantity").asLong();
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Test coordination timed out");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Test worker interrupted", exception);
        }
    }
}
