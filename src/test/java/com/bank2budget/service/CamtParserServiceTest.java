package com.bank2budget.service;

import com.bank2budget.exception.CamtParseException;
import com.bank2budget.model.Transaction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CamtParserServiceTest {

    private CamtParserService parserService;

    @BeforeEach
    void setUp() {
        parserService = new CamtParserService();
    }

    @Test
    void shouldParseValidCamt053File() {
        InputStream input = getClass().getResourceAsStream("/sample-camt053.xml");
        assertNotNull(input, "sample-camt053.xml should be on classpath");

        List<Transaction> transactions = parserService.parse(input);

        assertEquals(3, transactions.size());
    }

    @Test
    void shouldExtractCreditTransaction() {
        InputStream input = getClass().getResourceAsStream("/sample-camt053.xml");
        List<Transaction> transactions = parserService.parse(input);

        Transaction credit = transactions.get(0);
        assertEquals(new BigDecimal("1500.00"), credit.amount());
        assertEquals("EUR", credit.currency());
        assertEquals("CREDIT", credit.type());
        assertEquals("Monthly salary payment", credit.details());
        assertEquals(LocalDate.of(2024, 1, 15), credit.bookingDate());
        assertEquals(LocalDate.of(2024, 1, 15), credit.valueDate());
        assertEquals("BOOK", credit.status());
        assertEquals("REF001", credit.accountServicerReference());
        assertEquals("John Doe", credit.counterpartyName());
        assertEquals("Invoice payment 12345", credit.remittanceInfo());
        assertEquals("E2E001", credit.endToEndId());
    }

    @Test
    void shouldExtractDebitTransaction() {
        InputStream input = getClass().getResourceAsStream("/sample-camt053.xml");
        List<Transaction> transactions = parserService.parse(input);

        Transaction debit = transactions.get(1);
        assertEquals(new BigDecimal("250.75"), debit.amount());
        assertEquals("EUR", debit.currency());
        assertEquals("DEBIT", debit.type());
        assertEquals("Utility payment", debit.details());
        assertEquals(LocalDate.of(2024, 1, 16), debit.bookingDate());
        assertEquals("Electric Company", debit.counterpartyName());
        assertEquals("Electricity bill January", debit.remittanceInfo());
    }

    @Test
    void shouldHandleMissingOptionalFields() {
        InputStream input = getClass().getResourceAsStream("/sample-camt053.xml");
        List<Transaction> transactions = parserService.parse(input);

        // Third entry has no NtryDtls, no AcctSvcrRef
        Transaction minimal = transactions.get(2);
        assertEquals(new BigDecimal("99.99"), minimal.amount());
        assertEquals("USD", minimal.currency());
        assertEquals("DEBIT", minimal.type());
        assertEquals("Online purchase", minimal.details());
        assertEquals("PDNG", minimal.status());
        assertEquals("", minimal.counterpartyName());
        assertEquals("", minimal.remittanceInfo());
        assertEquals("", minimal.endToEndId());
        assertEquals("", minimal.accountServicerReference());
    }

    @Test
    void shouldParseCamt053V8WithNestedStatus() {
        InputStream input = getClass().getResourceAsStream("/sample-camt053-v8.xml");
        assertNotNull(input, "sample-camt053-v8.xml should be on classpath");

        List<Transaction> transactions = parserService.parse(input);
        assertEquals(1, transactions.size());

        Transaction tx = transactions.getFirst();
        assertEquals(new BigDecimal("3200.00"), tx.amount());
        assertEquals("EUR", tx.currency());
        assertEquals("CREDIT", tx.type());
        assertEquals("BOOK", tx.status());
        assertEquals("Professional services income", tx.details());
        assertEquals("E2E-V8-001", tx.endToEndId());
    }

    @Test
    void shouldRejectInvalidXml() {
        InputStream input = new ByteArrayInputStream(
                "<invalid>not a camt file</invalid>".getBytes(StandardCharsets.UTF_8));

        assertThrows(CamtParseException.class, () -> parserService.parse(input));
    }

    @Test
    void shouldRejectMalformedXml() {
        InputStream input = new ByteArrayInputStream(
                "this is not xml at all".getBytes(StandardCharsets.UTF_8));

        assertThrows(CamtParseException.class, () -> parserService.parse(input));
    }

    @Test
    void shouldRejectInvalidAmount() {
        String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <Document><BkToCstmrStmt><Stmt><Ntry>
                    <Amt Ccy="EUR">NOT_A_NUMBER</Amt>
                    <CdtDbtInd>CRDT</CdtDbtInd>
                    <Sts>BOOK</Sts>
                    <BookgDt><Dt>2024-01-15</Dt></BookgDt>
                    <ValDt><Dt>2024-01-15</Dt></ValDt>
                    <AddtlNtryInf>Test</AddtlNtryInf>
                </Ntry></Stmt></BkToCstmrStmt></Document>
                """;
        InputStream input = new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8));

        CamtParseException ex = assertThrows(CamtParseException.class, () -> parserService.parse(input));
        assertTrue(ex.getMessage().contains("Invalid amount format"));
    }

    @Test
    void shouldRejectInvalidDate() {
        String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <Document><BkToCstmrStmt><Stmt><Ntry>
                    <Amt Ccy="EUR">100.00</Amt>
                    <CdtDbtInd>CRDT</CdtDbtInd>
                    <Sts>BOOK</Sts>
                    <BookgDt><Dt>2024-13-99</Dt></BookgDt>
                    <ValDt><Dt>2024-01-15</Dt></ValDt>
                    <AddtlNtryInf>Test</AddtlNtryInf>
                </Ntry></Stmt></BkToCstmrStmt></Document>
                """;
        InputStream input = new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8));

        CamtParseException ex = assertThrows(CamtParseException.class, () -> parserService.parse(input));
        assertTrue(ex.getMessage().contains("Invalid date format"));
    }
}
