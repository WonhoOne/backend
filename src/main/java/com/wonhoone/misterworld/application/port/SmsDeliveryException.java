package com.wonhoone.misterworld.application.port;

/** Only fixed categories cross this boundary. Never attach raw provider response/cause. */
public class SmsDeliveryException extends RuntimeException {
    public enum Category { NETWORK, TIMEOUT, HTTP_REJECTED, PROVIDER_REJECTED, INVALID_RESPONSE, INVALID_CONTACT }
    private final Category category;
    public SmsDeliveryException(Category category) { super(category.name()); this.category = category; }
    public Category category() { return category; }
}
