package com.bank2budget.service;

import com.bank2budget.config.CategorizationProperties;
import com.bank2budget.model.Transaction;
import dev.langchain4j.model.chat.DisabledChatModel;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class TransactionCategorizationServiceTest {

    /** Build a service with the given keyword rules and no AI fallback. */
    private TransactionCategorizationService serviceWithRules(Map<String, List<String>> rules) {
        var props = new CategorizationProperties(rules, new CategorizationProperties.Ai(null, null, null));
        var aiService = new AiCategorizationService(new DisabledChatModel());
        return new TransactionCategorizationService(props, aiService);
    }

    private Transaction tx(String details, String counterparty, String remittance) {
        return new Transaction(
                new BigDecimal("100.00"), "EUR", "DBIT", details,
                LocalDate.of(2025, 1, 15), null, "BOOK", null,
                counterparty, remittance, null);
    }

    // ======================== Keyword matching ========================

    @Test
    void shouldMatchKeywordInDetails() {
        var rules = Map.of("Health Insurance", List.of("zilveren kruis"));
        var service = serviceWithRules(rules);

        Transaction insurance = tx("Premie Zilveren Kruis Relatienummer 193993139", null, null);
        List<String> result = service.categorize(List.of(insurance));

        assertEquals(1, result.size());
        assertEquals("Health Insurance", result.getFirst());
    }

    @Test
    void shouldMatchKeywordInCounterpartyName() {
        var rules = Map.of("Groceries", List.of("albert heijn"));
        var service = serviceWithRules(rules);

        Transaction grocery = tx("BEA, Apple Pay", "Albert Heijn 8641", null);
        List<String> result = service.categorize(List.of(grocery));

        assertEquals("Groceries", result.getFirst());
    }

    @Test
    void shouldMatchKeywordInRemittanceInfo() {
        var rules = Map.of("Taxes", List.of("belastingdienst"));
        var service = serviceWithRules(rules);

        Transaction tax = tx("SEPA payment", null, "Belastingdienst aanslag 2025");
        List<String> result = service.categorize(List.of(tax));

        assertEquals("Taxes", result.getFirst());
    }

    @Test
    void shouldBeCaseInsensitive() {
        var rules = Map.of("Pet Expenses", List.of("dierenspeciaalzaak"));
        var service = serviceWithRules(rules);

        Transaction pet = tx("BEA, Apple Pay DIERENSPECIAALZAAK F,PAS282", null, null);
        List<String> result = service.categorize(List.of(pet));

        assertEquals("Pet Expenses", result.getFirst());
    }

    @Test
    void shouldReturnEmptyStringForUnmatched() {
        var rules = Map.of("Groceries", List.of("albert heijn"));
        var service = serviceWithRules(rules);

        Transaction unknown = tx("Random purchase somewhere", null, null);
        List<String> result = service.categorize(List.of(unknown));

        assertEquals("", result.getFirst());
    }

    @Test
    void shouldReturnEmptyListForEmptyInput() {
        var rules = Map.of("Groceries", List.of("albert heijn"));
        var service = serviceWithRules(rules);

        List<String> result = service.categorize(List.of());
        assertTrue(result.isEmpty());
    }

    @Test
    void shouldUseFirstMatchingRule() {
        // LinkedHashMap preserves insertion order
        var rules = new LinkedHashMap<String, List<String>>();
        rules.put("Outings", List.of("starbucks"));
        rules.put("Groceries", List.of("starbucks coffee"));
        var service = serviceWithRules(rules);

        Transaction tx = tx("Starbucks Coffee Shop", null, null);
        List<String> result = service.categorize(List.of(tx));

        assertEquals("Outings", result.getFirst());
    }

    @Test
    void shouldCategorizeMixedBatch() {
        var rules = new LinkedHashMap<String, List<String>>();
        rules.put("Health Insurance", List.of("zilveren kruis", "zorgverzeker"));
        rules.put("Groceries", List.of("albert heijn", " ah "));
        rules.put("Pet Expenses", List.of("dierenspeciaalzaak"));
        var service = serviceWithRules(rules);

        List<Transaction> transactions = List.of(
                tx("Premie Zilveren Kruis", null, null),
                tx("BEA, Apple Pay AH 8641 SuMa BV", null, null),
                tx("Random unknown transaction", null, null),
                tx("BEA, Apple Pay DIERENSPECIAALZAAK", null, null)
        );
        List<String> result = service.categorize(transactions);

        assertEquals(4, result.size());
        assertEquals("Health Insurance", result.get(0));
        assertEquals("Groceries", result.get(1));
        assertEquals("", result.get(2));
        assertEquals("Pet Expenses", result.get(3));
    }

    @Test
    void shouldHandleNullFieldsInTransaction() {
        var rules = Map.of("Groceries", List.of("albert heijn"));
        var service = serviceWithRules(rules);

        Transaction nullTx = new Transaction(null, null, null, null, null, null, null, null, null, null, null);
        List<String> result = service.categorize(List.of(nullTx));

        assertEquals("", result.getFirst());
    }

    @Test
    void shouldIgnoreBlankKeywords() {
        var rules = Map.of("Groceries", List.of("albert heijn", "", "  "));
        var service = serviceWithRules(rules);

        // The blank keywords should be filtered out, not match everything
        Transaction tx = tx("Some random text", null, null);
        List<String> result = service.categorize(List.of(tx));

        assertEquals("", result.getFirst());
    }

    @Test
    void shouldMatchMultipleKeywordsForSameCategory() {
        var rules = Map.of("Health Insurance", List.of("zilveren kruis", "zorgverzeker", "menzis"));
        var service = serviceWithRules(rules);

        assertEquals("Health Insurance",
                service.categorize(List.of(tx("Premie Zilveren Kruis", null, null))).getFirst());
        assertEquals("Health Insurance",
                service.categorize(List.of(tx("Zorgverzekering premie", null, null))).getFirst());
        assertEquals("Health Insurance",
                service.categorize(List.of(tx("Menzis betaling", null, null))).getFirst());
    }

    // ======================== Real-world Dutch transactions ========================

    @Test
    void shouldCategorizeSepaIncassoHealthInsurance() {
        var rules = Map.of("Health Insurance", List.of("zilveren kruis", "premie zilveren"));
        var service = serviceWithRules(rules);

        Transaction tx = tx(
                "/TRTP/SEPA Incasso algemeen doorlopend/CSID/NL10ZZZ302086370000 " +
                "/NAME/Zilveren Kruis Zorgverzekeringen NV/MARF/1320000642161 " +
                "/REMI/Premie Zilveren Kruis Relatienummer 193993139 " +
                "Periode 01-03-2026 - 01-04-2026/IBAN/NL58INGB0000003050/BIC/INGBNL2A/EREF/495041780389",
                null, null);
        List<String> result = service.categorize(List.of(tx));

        assertEquals("Health Insurance", result.getFirst());
    }

    @Test
    void shouldCategorizeApplePayGrocery() {
        var rules = Map.of("Groceries", List.of(" ah ", "albert heijn"));
        var service = serviceWithRules(rules);

        Transaction tx = tx(
                "BEA, Apple Pay AH 8641 SuMa BV,PAS272 NR:J0VY28, 14.02.26/16:36 UTRECHT",
                null, null);
        List<String> result = service.categorize(List.of(tx));

        assertEquals("Groceries", result.getFirst());
    }

    @Test
    void shouldCategorizePetShop() {
        var rules = Map.of("Pet Expenses", List.of("dierenspeciaalzaak"));
        var service = serviceWithRules(rules);

        Transaction tx = tx(
                "BEA, Apple Pay DIERENSPECIAALZAAK F,PAS282 NR:RH9901, 14.02.26/12:42 UTRECHT",
                null, null);
        List<String> result = service.categorize(List.of(tx));

        assertEquals("Pet Expenses", result.getFirst());
    }
}
