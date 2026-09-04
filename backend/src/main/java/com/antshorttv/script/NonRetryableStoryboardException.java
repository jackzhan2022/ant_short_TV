package com.antshorttv.script;

public class NonRetryableStoryboardException extends RuntimeException {
    public NonRetryableStoryboardException(String message, Throwable cause) {
        super(message, cause);
    }
}
