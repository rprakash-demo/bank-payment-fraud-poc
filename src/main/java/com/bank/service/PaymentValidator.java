package com.bank.service;

import com.bank.model.PaymentDTO;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Currency;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public class PaymentValidator {

    private static final Set<String> ISO_ALPHA3_COUNTRIES =
            Set.of(Locale.getISOCountries())
                    .stream()
                    .map(code -> new Locale("", code).getISO3Country())
                    .collect(Collectors.toSet());

    public void validate(PaymentDTO payment) {

        if (payment == null) {
            throw new IllegalArgumentException("Payment payload is required");
        }

        // Mandatory field checks
        require(payment.getTransactionId(), "Transaction ID");
        require(payment.getPayerName(), "Payer Name");
        require(payment.getPayerBank(), "Payer Bank");
        require(payment.getPayerCountryCode(), "Payer Country Code");
        require(payment.getPayerAccount(), "Payer Account");
        require(payment.getPayeeName(), "Payee Name");
        require(payment.getPayeeBank(), "Payee Bank");
        require(payment.getPayeeCountryCode(), "Payee Country Code");
        require(payment.getPayeeAccount(), "Payee Account");
        require(payment.getExecutionDate(), "Execution Date");
        require(payment.getAmount(), "Amount");
        require(payment.getCurrency(), "Currency");
        require(payment.getCreationTimestamp(), "Creation Timestamp");

        validateUUID(payment.getTransactionId());
        validateCountry(payment.getPayerCountryCode(), "Payer Country Code");
        validateCountry(payment.getPayeeCountryCode(), "Payee Country Code");
        validateCurrency(payment.getCurrency());
        validateAmount(payment.getAmount());
        validateDate(payment.getExecutionDate());
        validateTimestamp(payment.getCreationTimestamp());
    }

    private void require(String value, String field) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(field + " is mandatory");
        }
    }

    private void validateUUID(String uuid) {
        try {
            UUID.fromString(uuid);
        } catch (Exception e) {
            throw new IllegalArgumentException("Transaction ID must be valid UUID");
        }
    }

    private void validateCountry(String code, String field) {
        if (!ISO_ALPHA3_COUNTRIES.contains(code)) {
            throw new IllegalArgumentException(field + " must be valid ISO alpha-3 country code");
        }
    }

    private void validateCurrency(String currencyCode) {
        try {
            Currency.getInstance(currencyCode);
        } catch (Exception e) {
            throw new IllegalArgumentException("Currency must be valid ISO 4217 code");
        }
    }

    private void validateAmount(String amount) {
        try {
            BigDecimal bd = new BigDecimal(amount);
            if (bd.scale() != 2) {
                throw new IllegalArgumentException("Amount must have exactly 2 decimal places");
            }
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Amount must be valid decimal");
        }
    }

    private void validateDate(String date) {
        try {
            LocalDate.parse(date);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Execution Date must be ISO format YYYY-MM-DD");
        }
    }

    private void validateTimestamp(String timestamp) {
        try {
            Instant.parse(timestamp);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Creation Timestamp must be ISO UTC timestamp");
        }
    }
}