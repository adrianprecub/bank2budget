package com.bank2budget.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.URI;

@ConfigurationProperties(prefix = "app.google-sheets")
public record GoogleSheetsProperties(String url) {

    public boolean isConfigured() {
        if (url == null || url.isBlank()) return false;
        try {
            var uri = URI.create(url);
            return "https".equals(uri.getScheme())
                    && uri.getHost() != null
                    && uri.getHost().endsWith("script.google.com");
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
