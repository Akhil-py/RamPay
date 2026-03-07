package com.rampay.paymentservice.handlers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rampay.paymentservice.dto.ErrorResponse;
import com.rampay.paymentservice.exceptions.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.time.Instant;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for GlobalExceptionHandler.
 * Tests exception handling for all custom exceptions and validation errors.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("GlobalExceptionHandler Tests")
class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler globalExceptionHandler;

    @Mock
    private HttpServletRequest request;

    @Mock
    private MethodArgumentNotValidException methodArgumentNotValidException;

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        globalExceptionHandler = new GlobalExceptionHandler();
        objectMapper = new ObjectMapper();
    }

    @Test
    @DisplayName("handlePaymentNotFoundException - Should return 404 with error details")
    void testHandlePaymentNotFoundException_Success() {
        String message = "Payment not found with id: " + UUID.randomUUID();
        PaymentNotFoundException exception = new PaymentNotFoundException(message);
        String requestUri = "/payments/" + UUID.randomUUID();

        when(request.getRequestURI()).thenReturn(requestUri);

        ResponseEntity<ErrorResponse> response = globalExceptionHandler.handlePaymentNotFoundException(exception, request);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        ErrorResponse errorResponse = response.getBody();
        assertNotNull(errorResponse);
        assertEquals(HttpStatus.NOT_FOUND.value(), errorResponse.getStatus());
        assertEquals("Not Found", errorResponse.getError());
        assertEquals(message, errorResponse.getMessage());
        assertEquals(requestUri, errorResponse.getPath());
        assertTrue(errorResponse.getDetails().isEmpty());
        assertNotNull(errorResponse.getTimestamp());
    }

    @Test
    @DisplayName("handleInvalidPaymentStatusException - Should return 400 with error details")
    void testHandleInvalidPaymentStatusException_Success() {
        String message = "Cannot approve payment with status: APPROVED";
        InvalidPaymentStatusException exception = new InvalidPaymentStatusException(message);
        String requestUri = "/payments/" + UUID.randomUUID() + "/approve";

        when(request.getRequestURI()).thenReturn(requestUri);

        ResponseEntity<ErrorResponse> response = globalExceptionHandler.handleInvalidPaymentStatusException(exception, request);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        ErrorResponse errorResponse = response.getBody();
        assertNotNull(errorResponse);
        assertEquals(HttpStatus.BAD_REQUEST.value(), errorResponse.getStatus());
        assertEquals("Bad Request", errorResponse.getError());
        assertEquals(message, errorResponse.getMessage());
        assertEquals(requestUri, errorResponse.getPath());
        assertTrue(errorResponse.getDetails().isEmpty());
        assertNotNull(errorResponse.getTimestamp());
    }

    @Test
    @DisplayName("handleDuplicatePaymentException - Should return 409 with error details")
    void testHandleDuplicatePaymentException_Success() {
        String message = "Payment already processed with this idempotency key";
        DuplicatePaymentException exception = new DuplicatePaymentException(message);
        String requestUri = "/payments";

        when(request.getRequestURI()).thenReturn(requestUri);

        ResponseEntity<ErrorResponse> response = globalExceptionHandler.handleDuplicatePaymentException(exception, request);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        ErrorResponse errorResponse = response.getBody();
        assertNotNull(errorResponse);
        assertEquals(HttpStatus.CONFLICT.value(), errorResponse.getStatus());
        assertEquals("Conflict", errorResponse.getError());
        assertEquals(message, errorResponse.getMessage());
        assertEquals(requestUri, errorResponse.getPath());
        assertTrue(errorResponse.getDetails().isEmpty());
        assertNotNull(errorResponse.getTimestamp());
    }

    @Test
    @DisplayName("handleInvalidPaymentAmountException - Should return 400 with error details")
    void testHandleInvalidPaymentAmountException_Success() {
        String message = "Refund amount cannot exceed original amount";
        InvalidPaymentAmountException exception = new InvalidPaymentAmountException(message);
        String requestUri = "/payments/" + UUID.randomUUID() + "/refund";

        when(request.getRequestURI()).thenReturn(requestUri);

        ResponseEntity<ErrorResponse> response = globalExceptionHandler.handleInvalidPaymentAmountException(exception, request);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        ErrorResponse errorResponse = response.getBody();
        assertNotNull(errorResponse);
        assertEquals(HttpStatus.BAD_REQUEST.value(), errorResponse.getStatus());
        assertEquals("Bad Request", errorResponse.getError());
        assertEquals(message, errorResponse.getMessage());
        assertEquals(requestUri, errorResponse.getPath());
        assertTrue(errorResponse.getDetails().isEmpty());
        assertNotNull(errorResponse.getTimestamp());
    }

    @Test
    @DisplayName("handleInsufficientFundsException - Should return 400 with error details")
    void testHandleInsufficientFundsException_Success() {
        String message = "Insufficient funds in account";
        InsufficientFundsException exception = new InsufficientFundsException(message);
        String requestUri = "/payments";

        when(request.getRequestURI()).thenReturn(requestUri);

        ResponseEntity<ErrorResponse> response = globalExceptionHandler.handleInsufficientFundsException(exception, request);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        ErrorResponse errorResponse = response.getBody();
        assertNotNull(errorResponse);
        assertEquals(HttpStatus.BAD_REQUEST.value(), errorResponse.getStatus());
        assertEquals("Bad Request", errorResponse.getError());
        assertEquals(message, errorResponse.getMessage());
        assertEquals(requestUri, errorResponse.getPath());
        assertTrue(errorResponse.getDetails().isEmpty());
        assertNotNull(errorResponse.getTimestamp());
    }

    @Test
    @DisplayName("handleEventPublishingException - Should return 500 with error details")
    void testHandleEventPublishingException_Success() {
        String message = "Failed to publish event to Kafka";
        EventPublishingException exception = new EventPublishingException(message);
        String requestUri = "/payments/" + UUID.randomUUID() + "/approve";

        when(request.getRequestURI()).thenReturn(requestUri);

        ResponseEntity<ErrorResponse> response = globalExceptionHandler.handleEventPublishingException(exception, request);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        ErrorResponse errorResponse = response.getBody();
        assertNotNull(errorResponse);
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR.value(), errorResponse.getStatus());
        assertEquals("Internal Server Error", errorResponse.getError());
        assertEquals(message, errorResponse.getMessage());
        assertEquals(requestUri, errorResponse.getPath());
        assertEquals(1, errorResponse.getDetails().size());
        assertEquals("Failed to publish event", errorResponse.getDetails().get(0));
        assertNotNull(errorResponse.getTimestamp());
    }

    @Test
    @DisplayName("handleValidationException - Should return 400 with validation errors")
    void testHandleValidationException_Success() {
        FieldError fieldError1 = new FieldError("payment", "amount", "invalid", false, null, null, "must be greater than 0");
        FieldError fieldError2 = new FieldError("payment", "currency", "xyz", false, null, null, "must be a valid ISO 4217 code");

        org.springframework.validation.BindingResult bindingResult = mock(org.springframework.validation.BindingResult.class);
        when(methodArgumentNotValidException.getBindingResult()).thenReturn(bindingResult);
        when(bindingResult.getFieldErrors()).thenReturn(java.util.List.of(fieldError1, fieldError2));
        String requestUri = "/payments";

        when(request.getRequestURI()).thenReturn(requestUri);

        ResponseEntity<ErrorResponse> response = globalExceptionHandler.handleValidationException(methodArgumentNotValidException, request);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        ErrorResponse errorResponse = response.getBody();
        assertNotNull(errorResponse);
        assertEquals(HttpStatus.BAD_REQUEST.value(), errorResponse.getStatus());
        assertEquals("Validation Error", errorResponse.getError());
        assertEquals("Request validation failed", errorResponse.getMessage());
        assertEquals(requestUri, errorResponse.getPath());
        assertEquals(2, errorResponse.getDetails().size());
        assertTrue(errorResponse.getDetails().contains("amount: must be greater than 0"));
        assertTrue(errorResponse.getDetails().contains("currency: must be a valid ISO 4217 code"));
        assertNotNull(errorResponse.getTimestamp());
    }

    @Test
    @DisplayName("handleValidationException - Should handle single validation error")
    void testHandleValidationException_SingleError() {
        FieldError fieldError = new FieldError("payment", "fromAccountId", null, false, null, null, "is required");

        org.springframework.validation.BindingResult bindingResult = mock(org.springframework.validation.BindingResult.class);
        when(methodArgumentNotValidException.getBindingResult()).thenReturn(bindingResult);
        when(bindingResult.getFieldErrors()).thenReturn(java.util.List.of(fieldError));
        String requestUri = "/payments";

        when(request.getRequestURI()).thenReturn(requestUri);

        ResponseEntity<ErrorResponse> response = globalExceptionHandler.handleValidationException(methodArgumentNotValidException, request);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        ErrorResponse errorResponse = response.getBody();
        assertNotNull(errorResponse);
        assertEquals(1, errorResponse.getDetails().size());
        assertEquals("fromAccountId: is required", errorResponse.getDetails().get(0));
    }

    @Test
    @DisplayName("handleGenericException - Should return 500 with generic error message")
    void testHandleGenericException_Success() {
        String message = "Unexpected error occurred";
        Exception exception = new RuntimeException(message);
        String requestUri = "/payments/" + UUID.randomUUID();

        when(request.getRequestURI()).thenReturn(requestUri);

        ResponseEntity<ErrorResponse> response = globalExceptionHandler.handleGenericException(exception, request);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        ErrorResponse errorResponse = response.getBody();
        assertNotNull(errorResponse);
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR.value(), errorResponse.getStatus());
        assertEquals("Internal Server Error", errorResponse.getError());
        assertEquals("An unexpected error occurred", errorResponse.getMessage());
        assertEquals(requestUri, errorResponse.getPath());
        assertEquals(1, errorResponse.getDetails().size());
        assertEquals(message, errorResponse.getDetails().get(0));
        assertNotNull(errorResponse.getTimestamp());
    }

    @Test
    @DisplayName("handleGenericException - Should handle null exception message")
    void testHandleGenericException_NullMessage() {
        Exception exception = new NullPointerException();
        String requestUri = "/payments/" + UUID.randomUUID();

        when(request.getRequestURI()).thenReturn(requestUri);

        ResponseEntity<ErrorResponse> response = globalExceptionHandler.handleGenericException(exception, request);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        ErrorResponse errorResponse = response.getBody();
        assertNotNull(errorResponse);
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR.value(), errorResponse.getStatus());
        assertEquals("Internal Server Error", errorResponse.getError());
        assertEquals("An unexpected error occurred", errorResponse.getMessage());
        assertEquals(requestUri, errorResponse.getPath());
        assertNotNull(errorResponse.getTimestamp());
    }

    @Test
    @DisplayName("handleGenericException - Should handle exception with long message")
    void testHandleGenericException_LongMessage() {
        String longMessage = "This is a very long error message that contains " +
                "multiple sentences and provides detailed information " +
                "about what went wrong in the system. It might " +
                "include stack traces and other debugging information.";
        Exception exception = new RuntimeException(longMessage);
        String requestUri = "/payments/" + UUID.randomUUID();

        when(request.getRequestURI()).thenReturn(requestUri);

        ResponseEntity<ErrorResponse> response = globalExceptionHandler.handleGenericException(exception, request);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        ErrorResponse errorResponse = response.getBody();
        assertNotNull(errorResponse);
        assertEquals(longMessage, errorResponse.getDetails().get(0));
    }

    @Test
    @DisplayName("handlePaymentNotFoundException - Should handle exception with UUID in message")
    void testHandlePaymentNotFoundException_WithUUID() {
        UUID paymentId = UUID.randomUUID();
        String message = "Payment not found with id: " + paymentId;
        PaymentNotFoundException exception = new PaymentNotFoundException(message);
        String requestUri = "/payments/" + paymentId;

        when(request.getRequestURI()).thenReturn(requestUri);

        ResponseEntity<ErrorResponse> response = globalExceptionHandler.handlePaymentNotFoundException(exception, request);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        ErrorResponse errorResponse = response.getBody();
        assertNotNull(errorResponse);
        assertEquals(message, errorResponse.getMessage());
        assertTrue(message.contains(paymentId.toString()));
    }

    @Test
    @DisplayName("handleInvalidPaymentStatusException - Should handle all payment statuses")
    void testHandleInvalidPaymentStatusException_DifferentStatuses() {
        String[] statuses = {"PENDING", "APPROVED", "FAILED", "REFUNDED"};
        String requestUri = "/payments/" + UUID.randomUUID() + "/approve";

        when(request.getRequestURI()).thenReturn(requestUri);

        for (String status : statuses) {
            String message = "Cannot approve payment with status: " + status;
            InvalidPaymentStatusException exception = new InvalidPaymentStatusException(message);

            ResponseEntity<ErrorResponse> response = globalExceptionHandler.handleInvalidPaymentStatusException(exception, request);

            assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
            ErrorResponse errorResponse = response.getBody();
            assertNotNull(errorResponse);
            assertEquals(message, errorResponse.getMessage());
        }
    }

    @Test
    @DisplayName("handleValidationException - Should handle empty field errors")
    void testHandleValidationException_EmptyFieldErrors() {
        org.springframework.validation.BindingResult bindingResult = mock(org.springframework.validation.BindingResult.class);
        when(methodArgumentNotValidException.getBindingResult()).thenReturn(bindingResult);
        when(bindingResult.getFieldErrors()).thenReturn(Collections.emptyList());
        String requestUri = "/payments";

        when(request.getRequestURI()).thenReturn(requestUri);

        ResponseEntity<ErrorResponse> response = globalExceptionHandler.handleValidationException(methodArgumentNotValidException, request);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        ErrorResponse errorResponse = response.getBody();
        assertNotNull(errorResponse);
        assertTrue(errorResponse.getDetails().isEmpty());
    }

    @Test
    @DisplayName("handleEventPublishingException - Should verify error details content")
    void testHandleEventPublishingException_VerifyDetails() {
        String message = "Failed to publish event to Kafka";
        EventPublishingException exception = new EventPublishingException(message);
        String requestUri = "/payments/" + UUID.randomUUID() + "/approve";

        when(request.getRequestURI()).thenReturn(requestUri);

        ResponseEntity<ErrorResponse> response = globalExceptionHandler.handleEventPublishingException(exception, request);

        ErrorResponse errorResponse = response.getBody();
        assertNotNull(errorResponse);
        assertEquals("Failed to publish event", errorResponse.getDetails().get(0));
    }

    @Test
    @DisplayName("handleGenericException - Should handle different exception types")
    void testHandleGenericException_DifferentExceptionTypes() {
        String requestUri = "/payments/" + UUID.randomUUID();
        when(request.getRequestURI()).thenReturn(requestUri);

        // Test with RuntimeException
        ResponseEntity<ErrorResponse> response1 = globalExceptionHandler.handleGenericException(
                new RuntimeException("Runtime error"), request);
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response1.getStatusCode());

        // Test with IllegalArgumentException
        ResponseEntity<ErrorResponse> response2 = globalExceptionHandler.handleGenericException(
                new IllegalArgumentException("Illegal argument"), request);
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response2.getStatusCode());

        // Test with IllegalStateException
        ResponseEntity<ErrorResponse> response3 = globalExceptionHandler.handleGenericException(
                new IllegalStateException("Illegal state"), request);
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response3.getStatusCode());
    }

    @Test
    @DisplayName("ErrorResponse - Should serialize to JSON correctly")
    void testErrorResponse_JsonSerialization() throws Exception {
        String message = "Payment not found";
        PaymentNotFoundException exception = new PaymentNotFoundException(message);
        String requestUri = "/payments/" + UUID.randomUUID();

        when(request.getRequestURI()).thenReturn(requestUri);

        ResponseEntity<ErrorResponse> response = globalExceptionHandler.handlePaymentNotFoundException(exception, request);
        ErrorResponse errorResponse = response.getBody();

        String json = objectMapper.writeValueAsString(errorResponse);

        assertNotNull(json);
        assertTrue(json.contains("\"status\":404"));
        assertTrue(json.contains("\"error\":\"Not Found\""));
        assertTrue(json.contains("\"message\":\"" + message + "\""));
        assertTrue(json.contains("\"path\":\"" + requestUri + "\""));
    }

    @Test
    @DisplayName("handleValidationException - Should handle validation errors with special characters")
    void testHandleValidationException_SpecialCharacters() {
        FieldError fieldError = new FieldError("payment", "reason", "<script>alert('xss')</script>", false, null, null, "contains invalid characters");

        org.springframework.validation.BindingResult bindingResult = mock(org.springframework.validation.BindingResult.class);
        when(methodArgumentNotValidException.getBindingResult()).thenReturn(bindingResult);
        when(bindingResult.getFieldErrors()).thenReturn(java.util.List.of(fieldError));
        String requestUri = "/payments/" + UUID.randomUUID() + "/fail";

        when(request.getRequestURI()).thenReturn(requestUri);

        ResponseEntity<ErrorResponse> response = globalExceptionHandler.handleValidationException(methodArgumentNotValidException, request);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        ErrorResponse errorResponse = response.getBody();
        assertNotNull(errorResponse);
        assertTrue(errorResponse.getDetails().get(0).contains("reason"));
    }

    @Test
    @DisplayName("handleGenericException - Should handle exception with cause")
    void testHandleGenericException_WithCause() {
        Exception cause = new RuntimeException("Root cause");
        Exception exception = new RuntimeException("Wrapper exception", cause);
        String requestUri = "/payments/" + UUID.randomUUID();

        when(request.getRequestURI()).thenReturn(requestUri);

        ResponseEntity<ErrorResponse> response = globalExceptionHandler.handleGenericException(exception, request);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        ErrorResponse errorResponse = response.getBody();
        assertNotNull(errorResponse);
        assertEquals("Wrapper exception", errorResponse.getDetails().get(0));
    }

    @Test
    @DisplayName("handleDuplicatePaymentException - Should handle exception with idempotency key")
    void testHandleDuplicatePaymentException_WithIdempotencyKey() {
        String idempotencyKey = "test-key-123";
        String message = "Payment already processed with idempotency key: " + idempotencyKey;
        DuplicatePaymentException exception = new DuplicatePaymentException(message);
        String requestUri = "/payments";

        when(request.getRequestURI()).thenReturn(requestUri);

        ResponseEntity<ErrorResponse> response = globalExceptionHandler.handleDuplicatePaymentException(exception, request);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        ErrorResponse errorResponse = response.getBody();
        assertNotNull(errorResponse);
        assertEquals(message, errorResponse.getMessage());
        assertTrue(message.contains(idempotencyKey));
    }

    @Test
    @DisplayName("handleInvalidPaymentAmountException - Should handle amount validation errors")
    void testHandleInvalidPaymentAmountException_AmountErrors() {
        String[] errorMessages = {
                "Refund amount cannot exceed original amount",
                "Amount must be greater than 0",
                "Amount must not exceed 1,000,000"
        };
        String requestUri = "/payments/" + UUID.randomUUID() + "/refund";

        when(request.getRequestURI()).thenReturn(requestUri);

        for (String message : errorMessages) {
            InvalidPaymentAmountException exception = new InvalidPaymentAmountException(message);

            ResponseEntity<ErrorResponse> response = globalExceptionHandler.handleInvalidPaymentAmountException(exception, request);

            assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
            ErrorResponse errorResponse = response.getBody();
            assertNotNull(errorResponse);
            assertEquals(message, errorResponse.getMessage());
        }
    }

    @Test
    @DisplayName("handleInsufficientFundsException - Should handle different fund error messages")
    void testHandleInsufficientFundsException_DifferentMessages() {
        String[] errorMessages = {
                "Insufficient funds in account",
                "Account balance too low",
                "Not enough funds to complete transaction"
        };
        String requestUri = "/payments";

        when(request.getRequestURI()).thenReturn(requestUri);

        for (String message : errorMessages) {
            InsufficientFundsException exception = new InsufficientFundsException(message);

            ResponseEntity<ErrorResponse> response = globalExceptionHandler.handleInsufficientFundsException(exception, request);

            assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
            ErrorResponse errorResponse = response.getBody();
            assertNotNull(errorResponse);
            assertEquals(message, errorResponse.getMessage());
        }
    }

    @Test
    @DisplayName("handleValidationException - Should handle validation errors with long field names")
    void testHandleValidationException_LongFieldNames() {
        String longFieldName = "veryLongFieldNameThatExceedsNormalLength";
        FieldError fieldError = new FieldError("payment", longFieldName, null, false, null, null, "is required");

        org.springframework.validation.BindingResult bindingResult = mock(org.springframework.validation.BindingResult.class);
        when(methodArgumentNotValidException.getBindingResult()).thenReturn(bindingResult);
        when(bindingResult.getFieldErrors()).thenReturn(java.util.List.of(fieldError));
        String requestUri = "/payments";

        when(request.getRequestURI()).thenReturn(requestUri);

        ResponseEntity<ErrorResponse> response = globalExceptionHandler.handleValidationException(methodArgumentNotValidException, request);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        ErrorResponse errorResponse = response.getBody();
        assertNotNull(errorResponse);
        assertTrue(errorResponse.getDetails().get(0).contains(longFieldName));
    }

    @Test
    @DisplayName("handleGenericException - Should handle exception with stack trace")
    void testHandleGenericException_WithStackTrace() {
        Exception exception = new RuntimeException("Error with stack trace");
        exception.fillInStackTrace();
        String requestUri = "/payments/" + UUID.randomUUID();

        when(request.getRequestURI()).thenReturn(requestUri);

        ResponseEntity<ErrorResponse> response = globalExceptionHandler.handleGenericException(exception, request);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        ErrorResponse errorResponse = response.getBody();
        assertNotNull(errorResponse);
        assertEquals("Error with stack trace", errorResponse.getDetails().get(0));
        assertNotNull(errorResponse.getTimestamp());
    }
}
