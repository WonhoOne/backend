package com.wonhoone.misterworld.application.port;

import java.util.Objects;

public record SmsMessage(String to, String text) {
    public SmsMessage { Objects.requireNonNull(to); Objects.requireNonNull(text); }
    @Override public String toString() { return "SmsMessage[redacted]"; }
}
