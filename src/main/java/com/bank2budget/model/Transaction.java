package com.bank2budget.model;

import java.math.BigDecimal;
import java.time.LocalDate;

public record Transaction(
        BigDecimal amount,
        String currency,
        String type,
        String details,
        LocalDate bookingDate,
        LocalDate valueDate,
        String status,
        String accountServicerReference,
        String counterpartyName,
        String remittanceInfo,
        String endToEndId,
        String category
) {
    /** Convenience constructor for building a transaction without a category (defaults to empty string). */
    public Transaction(
            BigDecimal amount,
            String currency,
            String type,
            String details,
            LocalDate bookingDate,
            LocalDate valueDate,
            String status,
            String accountServicerReference,
            String counterpartyName,
            String remittanceInfo,
            String endToEndId
    ) {
        this(amount, currency, type, details, bookingDate, valueDate, status,
                accountServicerReference, counterpartyName, remittanceInfo, endToEndId, "");
    }

    /** Returns a copy of this transaction with the given category applied. */
    public Transaction withCategory(String category) {
        return new Transaction(amount, currency, type, details, bookingDate, valueDate, status,
                accountServicerReference, counterpartyName, remittanceInfo, endToEndId, category);
    }
}
