package com.bank2budget.service;

import com.bank2budget.config.GoogleSheetsProperties;
import com.bank2budget.model.Transaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.util.List;
import java.util.Map;

@Service
public class GoogleSheetsService {

    private static final Logger log = LoggerFactory.getLogger(GoogleSheetsService.class);

    private final RestClient restClient;
    private final GoogleSheetsProperties properties;

    public GoogleSheetsService(GoogleSheetsProperties properties) {
        this.properties = properties;

        // Google Apps Script redirects POST -> GET on 302; NORMAL handles this correctly
        var httpClient = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        this.restClient = RestClient.builder()
                .requestFactory(new JdkClientHttpRequestFactory(httpClient))
                .build();
    }

    public boolean isConfigured() {
        return properties.isConfigured();
    }

    public void uploadTransactions(List<Transaction> transactions) {
        if (!isConfigured()) {
            log.warn("Google Sheets URL is not configured; skipping upload");
            return;
        }

        log.info("Uploading {} transactions to Google Sheets", transactions.size());

        List<Map<String, Object>> slim = transactions.stream()
                .map(tx -> Map.<String, Object>of(
                        "date", tx.bookingDate() != null ? tx.bookingDate().toString() : "",
                        "amount", tx.amount(),
                        "currency", tx.currency(),
                        "type", tx.type(),
                        "category", tx.category(),
                        "details", tx.details()
                ))
                .toList();

        Map<String, Object> payload = Map.of("transactions", slim);

        restClient.post()
                .uri(properties.url())
                .contentType(MediaType.APPLICATION_JSON)
                .body(payload)
                .retrieve()
                .toBodilessEntity();

        log.info("Successfully uploaded transactions to Google Sheets");
    }
}
