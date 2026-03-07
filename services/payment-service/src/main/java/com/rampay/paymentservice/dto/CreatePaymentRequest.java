package com.rampay.paymentservice.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Data
public class CreatePaymentRequest {

    @NotNull(message = "fromAccountId is required")
    private UUID fromAccountId;

    @NotNull(message = "toAccountId is required")
    private UUID toAccountId;

    @NotNull(message = "amount is required")
    @DecimalMin(value = "0.01", message = "amount must be greater than 0")
    @DecimalMax(value = "1000000.00", message = "amount must not exceed 1,000,000")
    private BigDecimal amount;

    @NotBlank(message = "currency is required")
    @Pattern(regexp = "^[A-Z]{3}$", message = "currency must be a valid ISO 4217 code")
    private String currency;

    @AssertTrue(message = "fromAccountId and toAccountId must be different")
    public boolean isAccountsDifferent() {
        return fromAccountId == null || toAccountId == null || !fromAccountId.equals(toAccountId);
    }
}
