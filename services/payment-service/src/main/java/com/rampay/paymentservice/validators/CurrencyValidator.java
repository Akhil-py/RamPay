package com.rampay.paymentservice.validators;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.util.Set;

public class CurrencyValidator implements ConstraintValidator<ValidCurrency, String> {

    private static final Set<String> VALID_CURRENCIES = Set.of(
            "USD", "EUR", "GBP", "JPY", "CAD", "AUD", "CHF", "CNY"
    );

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null) {
            return true; // Use @NotNull for null check
        }
        return VALID_CURRENCIES.contains(value);
    }
}
