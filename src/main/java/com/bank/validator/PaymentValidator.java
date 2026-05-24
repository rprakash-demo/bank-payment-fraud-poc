package com.bank.service;

import com.bank.model.PaymentDTO;
import java.util.Set;

public class PaymentValidator {
    private static final Set<String> VALID_COUNTRIES = Set.of("US", "DE", "IN", "GB", "SY", "CU", "IQ", "IR", "KP", "SD");
    private static final Set<String> VALID_CURRENCIES = Set.of("USD", "EUR", "INR", "GBP");

    public void validate(PaymentDTO payment) throws Exception {
        if (payment.getTransactionId() == null || payment.getTransactionId().isEmpty()) 
            throw new Exception("Validation Error: Transaction ID is required");
        
        if (payment.getPayerCountryCode() == null || !VALID_COUNTRIES.contains(payment.getPayerCountryCode())) 
            throw new Exception("Invalid Country Code: " + payment.getPayerCountryCode());
        
        // Only validate currency if it was provided in the request
        if (payment.getCurrency() != null && !VALID_CURRENCIES.contains(payment.getCurrency())) 
            throw new Exception("Invalid Currency: " + payment.getCurrency());
    }
}