package com.rampay.paymentservice.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class RefundPaymentRequest {

    @NotNull(message = "refundAmount is required")
    @DecimalMin(value = "0.01", message = "refundAmount must be greater than 0")
    private BigDecimal refundAmount;
}
