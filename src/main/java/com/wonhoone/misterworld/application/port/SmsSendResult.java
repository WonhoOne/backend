package com.wonhoone.misterworld.application.port;

/** Provider accepted the request; this is not a guarantee of handset delivery. */
public record SmsSendResult(String providerMessageId) {}
