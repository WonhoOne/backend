package com.wonhoone.misterworld.application.time;

import java.time.Clock;
import java.time.LocalDate;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

@Component
public class BusinessDateProvider {
    private final Clock clock;
    public BusinessDateProvider(@Qualifier("businessClock") Clock clock) { this.clock = clock; }
    public LocalDate today() { return LocalDate.now(clock); }
}
