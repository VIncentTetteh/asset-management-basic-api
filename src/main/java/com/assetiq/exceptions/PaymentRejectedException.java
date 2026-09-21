package com.assetiq.exceptions;

/**
 * A payment the gateway reports but we refuse to honour: not successful, wrong amount,
 * or wrong currency.
 *
 * <p>A distinct type so the billing transaction can commit the FAILED record instead of
 * rolling it back along with the error. Without that, a rejected payment left no trace
 * and the same bad reference could be retried indefinitely.
 */
public class PaymentRejectedException extends RuntimeException {
    public PaymentRejectedException(String message) {
        super(message);
    }
}
