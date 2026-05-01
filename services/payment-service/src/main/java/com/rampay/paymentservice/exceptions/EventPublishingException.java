package com.rampay.paymentservice.exceptions;

public class EventPublishingException extends RuntimeException {
    public EventPublishingException(String message) {
        super(message);
    }

    public EventPublishingException(String message, Throwable cause) {
        super(message, cause);
    }
}
