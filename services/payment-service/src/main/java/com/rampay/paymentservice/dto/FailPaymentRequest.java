package com.rampay.paymentservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class FailPaymentRequest {

    @NotBlank(message = "reason is required")
    @Size(max = 255, message = "reason must not exceed 255 characters")
    private String reason;
}
