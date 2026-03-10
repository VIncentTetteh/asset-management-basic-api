package com.example.demo.exceptions;

/**
 * Thrown when an upstream payment gateway (e.g. Paystack) returns an error or
 * is otherwise unavailable. Maps to HTTP 502 Bad Gateway.
 */
public class PaymentGatewayException extends RuntimeException {

    public PaymentGatewayException(String message) {
        super(message);
    }

    public PaymentGatewayException(String message, Throwable cause) {
        super(message, cause);
    }
}
