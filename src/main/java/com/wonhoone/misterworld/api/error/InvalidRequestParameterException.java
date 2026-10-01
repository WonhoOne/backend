package com.wonhoone.misterworld.api.error;

public class InvalidRequestParameterException extends RuntimeException {
    public InvalidRequestParameterException() { super("Request parameter is invalid."); }

    public static void requirePositive(long id) {
        if (id <= 0) throw new InvalidRequestParameterException();
    }
}
