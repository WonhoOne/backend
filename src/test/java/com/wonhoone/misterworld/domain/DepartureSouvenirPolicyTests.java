package com.wonhoone.misterworld.domain;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DepartureSouvenirPolicyTests {
    @Test void eachThemeHasItsIncludedSouvenir() {
        assertThat(DepartureSouvenirPolicy.itemType(Theme.HONEYMOON_ROMANCE)).isEqualTo(InventoryItemType.COUPLE_TSHIRT);
        assertThat(DepartureSouvenirPolicy.itemType(Theme.PARENTS_HEALING)).isEqualTo(InventoryItemType.GINSENG_GIFT);
        assertThat(DepartureSouvenirPolicy.itemType(Theme.GOLF_CHALLENGE)).isEqualTo(InventoryItemType.GOLF_BALL);
        assertThat(DepartureSouvenirPolicy.itemType(Theme.OUTDOOR_TREKKING)).isEqualTo(InventoryItemType.SCARF);
    }

    @Test void honeymoonCountsCouplesOthersCountParticipants() {
        assertThat(DepartureSouvenirPolicy.quantity(Theme.HONEYMOON_ROMANCE, 4)).isEqualTo(2);
        assertThat(DepartureSouvenirPolicy.quantity(Theme.OUTDOOR_TREKKING, 3)).isEqualTo(3);
        assertThat(DepartureSouvenirPolicy.quantity(Theme.GOLF_CHALLENGE, 0)).isZero();
        assertThatThrownBy(() -> DepartureSouvenirPolicy.quantity(Theme.GOLF_CHALLENGE, -1))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
