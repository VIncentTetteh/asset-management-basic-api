package com.assetiq.controllers.v1;

import com.assetiq.exceptions.MfaCodeInvalidException;
import com.assetiq.exceptions.MfaEnrolmentRequiredException;
import com.assetiq.exceptions.MfaStepUpRequiredException;
import com.assetiq.exceptions.PaymentGatewayException;
import com.assetiq.exceptions.PaymentRejectedException;
import com.assetiq.services.FeatureDisabledException;
import jakarta.persistence.EntityNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

@ControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private Map<String, Object> errorBody(int status, String message, String errorCode) {
        Map<String, Object> body = new HashMap<>();
        body.put("status", status);
        body.put("message", message != null ? message : "An error occurred");
        body.put("errorCode", errorCode != null ? errorCode : "UNKNOWN");
        body.put("timestamp", Instant.now().toString());
        String requestId = MDC.get("requestId");
        if (requestId != null) body.put("requestId", requestId);
        return body;
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(
            org.springframework.http.converter.HttpMessageNotReadableException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        log.warn("Malformed JSON request: {}", ex.getMessage());
        return new ResponseEntity<>(errorBody(400, "Malformed JSON request", "BAD_REQUEST_MALFORMED_JSON"), HttpStatus.BAD_REQUEST);
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        Map<String, String> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
                .collect(Collectors.toMap(f -> f.getField(), f -> f.getDefaultMessage(), (a, b) -> a));
        Map<String, Object> body = errorBody(400, "Validation failed", "VALIDATION_FAILED");
        body.put("errors", fieldErrors);
        return new ResponseEntity<>(body, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(com.assetiq.exceptions.FieldValidationException.class)
    public ResponseEntity<Object> handleFieldValidation(com.assetiq.exceptions.FieldValidationException ex) {
        Map<String, Object> body = errorBody(400, "Validation failed", "VALIDATION_FAILED");
        body.put("errors", Map.of(ex.getField(), ex.getMessage()));
        return new ResponseEntity<>(body, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(com.assetiq.exceptions.DuplicateFieldException.class)
    public ResponseEntity<Object> handleDuplicateField(com.assetiq.exceptions.DuplicateFieldException ex) {
        Map<String, Object> body = errorBody(409, ex.getMessage(), "DUPLICATE");
        body.put("errors", new HashMap<>(Map.of(ex.getField(), DataIntegrityViolationClassifier.ALREADY_IN_USE)));
        return new ResponseEntity<>(body, HttpStatus.CONFLICT);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Object> handleIllegalArgument(IllegalArgumentException ex) {
        return new ResponseEntity<>(errorBody(400, ex.getMessage(), "BAD_REQUEST"), HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(PaymentGatewayException.class)
    public ResponseEntity<Object> handlePaymentGateway(PaymentGatewayException ex) {
        log.error("Payment gateway error: {}", ex.getMessage());
        return new ResponseEntity<>(errorBody(502, ex.getMessage(), "PAYMENT_GATEWAY_ERROR"), HttpStatus.BAD_GATEWAY);
    }

    @ExceptionHandler(PaymentRejectedException.class)
    public ResponseEntity<Object> handlePaymentRejected(PaymentRejectedException ex) {
        return new ResponseEntity<>(errorBody(422, ex.getMessage(), "PAYMENT_REJECTED"), HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Object> handleIllegalState(IllegalStateException ex) {
        return new ResponseEntity<>(errorBody(409, ex.getMessage(), "CONFLICT"), HttpStatus.CONFLICT);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Object> handleAccessDenied(AccessDeniedException ex) {
        return new ResponseEntity<>(errorBody(403, ex.getMessage() != null ? ex.getMessage() : "Access denied",
                "FORBIDDEN"),
                HttpStatus.FORBIDDEN);
    }

    // Step-up MFA. 401 (not 403) because the fix is to re-authenticate, and the
    // distinct errorCode lets the web client tell this apart from an expired
    // session, which must redirect to login instead of prompting for a code.
    @ExceptionHandler(MfaStepUpRequiredException.class)
    public ResponseEntity<Object> handleMfaStepUpRequired(MfaStepUpRequiredException ex) {
        return new ResponseEntity<>(errorBody(401, ex.getMessage(), "MFA_STEP_UP_REQUIRED"),
                HttpStatus.UNAUTHORIZED);
    }

    @ExceptionHandler(MfaCodeInvalidException.class)
    public ResponseEntity<Object> handleMfaCodeInvalid(MfaCodeInvalidException ex) {
        return new ResponseEntity<>(errorBody(401, ex.getMessage(), "MFA_CODE_INVALID"),
                HttpStatus.UNAUTHORIZED);
    }

    @ExceptionHandler(MfaEnrolmentRequiredException.class)
    public ResponseEntity<Object> handleMfaEnrolmentRequired(MfaEnrolmentRequiredException ex) {
        return new ResponseEntity<>(errorBody(428, ex.getMessage(), "MFA_ENROLMENT_REQUIRED"),
                HttpStatus.PRECONDITION_REQUIRED);
    }

    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<Object> handleEntityNotFound(EntityNotFoundException ex) {
        return new ResponseEntity<>(errorBody(404,
                ex.getMessage() != null ? ex.getMessage() : "Resource not found",
                "NOT_FOUND"),
                HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<Object> handleOptimisticLocking(ObjectOptimisticLockingFailureException ex) {
        return new ResponseEntity<>(
                errorBody(409, "This record was modified by another request. Please refresh and try again.", "CONFLICT_OPTIMISTIC_LOCK"),
                HttpStatus.CONFLICT);
    }

    /**
     * Database integrity failures, split by the root SQLState (see
     * {@link DataIntegrityViolationClassifier}): 23505 is a 409 DUPLICATE naming the
     * field, NOT NULL / too long / out of range / CHECK are 400s, and a foreign-key
     * failure is a 409 IN_USE. When the field is known it goes in {@code errors} so
     * the web app can mark it, exactly like a Bean Validation failure.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Object> handleDataIntegrity(DataIntegrityViolationException ex) {
        DataIntegrityViolationClassifier.Classification c = DataIntegrityViolationClassifier.classify(ex);
        // Log state and constraint only: the driver message can carry the conflicting value.
        log.warn("Data integrity violation: sqlState={} constraint={} -> {} {}",
                c.sqlState(), c.constraint(), c.status().value(), c.errorCode());
        Map<String, Object> body = errorBody(c.status().value(), c.message(), c.errorCode());
        if (!c.fieldErrors().isEmpty()) {
            body.put("errors", new HashMap<>(c.fieldErrors()));
        }
        return new ResponseEntity<>(body, c.status());
    }

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<Object> handleDataAccess(DataAccessException ex) {
        log.error("Database error", ex);
        return new ResponseEntity<>(errorBody(500, "A database error occurred. Please try again later.", "DATABASE_ERROR"),
                HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @ExceptionHandler(UnsupportedOperationException.class)
    public ResponseEntity<Object> handleUnsupportedOperation(UnsupportedOperationException ex) {
        return new ResponseEntity<>(
                errorBody(403, ex.getMessage() != null ? ex.getMessage() : "Operation not permitted", "FORBIDDEN_UNSUPPORTED_OPERATION"),
                HttpStatus.FORBIDDEN);
    }

    // P0-8: A method guarded by @FeatureFlagGate was invoked while the flag
    // was OFF for the current tenant. Default behaviour: 404 so the feature's
    // existence stays hidden. Callers that set throwNotImplemented=true (e.g.
    // public docs features) get a 501 instead.
    @ExceptionHandler(FeatureDisabledException.class)
    public ResponseEntity<Object> handleFeatureDisabled(FeatureDisabledException ex) {
        HttpStatus status = ex.isNotImplemented()
                ? HttpStatus.NOT_IMPLEMENTED
                : HttpStatus.NOT_FOUND;
        String code = ex.isNotImplemented() ? "FEATURE_NOT_IMPLEMENTED" : "NOT_FOUND";
        return new ResponseEntity<>(
                errorBody(status.value(), ex.getMessage(), code),
                status);
    }

    // The caller or their organisation has used its share of the AI assistant.
    // 429 with Retry-After, because a client needs to know to back off — unlike a
    // provider outage, which degrades inside a 200 so the answer box can explain.
    @ExceptionHandler(com.assetiq.services.ai.AiQuotaExceededException.class)
    public ResponseEntity<Object> handleAiQuota(com.assetiq.services.ai.AiQuotaExceededException ex) {
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.RETRY_AFTER, String.valueOf(ex.getRetryAfterSeconds()));
        return new ResponseEntity<>(
                errorBody(429, ex.getMessage(), "AI_QUOTA_EXCEEDED"),
                headers,
                HttpStatus.TOO_MANY_REQUESTS);
    }

    // An application-level allowance is spent (invitation sending, for example).
    // Same 429 + Retry-After shape as the AI quota above so a client has one
    // back-off path, with its own code so the message can be shown as-is.
    @ExceptionHandler(com.assetiq.exceptions.TooManyRequestsException.class)
    public ResponseEntity<Object> handleTooManyRequests(com.assetiq.exceptions.TooManyRequestsException ex) {
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.RETRY_AFTER, String.valueOf(ex.getRetryAfterSeconds()));
        return new ResponseEntity<>(
                errorBody(429, ex.getMessage(), "RATE_LIMITED"),
                headers,
                HttpStatus.TOO_MANY_REQUESTS);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> handleUnexpected(Exception ex) {
        log.error("Unexpected error", ex);
        return new ResponseEntity<>(errorBody(500, "An unexpected error occurred. Please try again later.", "INTERNAL_SERVER_ERROR_UNEXPECTED"),
                HttpStatus.INTERNAL_SERVER_ERROR);
    }

}
