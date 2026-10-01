package com.wonhoone.misterworld.domain;

public enum TransportOption {
    PRIVATE_LUXURY_CAR_2(2), PREMIUM_VAN_10(10);

    private final int capacity;

    TransportOption(int capacity) { this.capacity = capacity; }

    public int capacity() { return capacity; }
}
