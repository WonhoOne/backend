package com.wonhoone.misterworld.api.error;

public class ResourceConflictException extends RuntimeException {
    private final String code;

    public ResourceConflictException(String code, String safeMessage) {
        super(safeMessage);
        this.code = code;
    }

    public String code() { return code; }
}
