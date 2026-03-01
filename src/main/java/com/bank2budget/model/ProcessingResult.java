package com.bank2budget.model;

public record ProcessingResult(
        int fileCount,
        int transactionCount,
        boolean uploadedToGoogleSheets,
        String message
) {
}
