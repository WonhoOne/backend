package com.wonhoone.misterworld.application.port;

public interface SmsSender {
    SmsSendResult send(SmsMessage message);
}
