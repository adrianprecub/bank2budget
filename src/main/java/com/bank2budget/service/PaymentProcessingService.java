package com.bank2budget.service;

import com.bank2budget.exception.CamtParseException;
import com.bank2budget.model.ProcessingResult;
import com.bank2budget.model.Transaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Service
public class PaymentProcessingService {

    private static final Logger log = LoggerFactory.getLogger(PaymentProcessingService.class);

    /** Maximum number of XML entries to process from a single ZIP. */
    private static final int MAX_ZIP_ENTRIES = 100;

    /** Maximum total decompressed bytes across all entries (200 MB). */
    private static final long MAX_TOTAL_UNCOMPRESSED_BYTES = 200L * 1024 * 1024;

    /** Maximum compression ratio per entry (guards against ZIP bombs). */
    private static final double MAX_COMPRESSION_RATIO = 100.0;

    /** Maximum total transactions across all files. */
    private static final int MAX_TRANSACTIONS = 10_000;

    private final CamtParserService parserService;
    private final GoogleSheetsService sheetsService;
    private final TransactionCategorizationService categorizationService;

    public PaymentProcessingService(CamtParserService parserService,
                                    GoogleSheetsService sheetsService,
                                    TransactionCategorizationService categorizationService) {
        this.parserService = parserService;
        this.sheetsService = sheetsService;
        this.categorizationService = categorizationService;
    }

    public ProcessingResult processZip(InputStream zipStream, String filename) throws IOException {
        String safeFilename = sanitize(filename);
        log.info("Processing ZIP file: {}", safeFilename);

        List<Transaction> allTransactions = new ArrayList<>();
        int filesProcessed = 0;
        long totalBytesRead = 0;
        int totalEntries = 0;

        try (ZipInputStream zis = new ZipInputStream(zipStream)) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                totalEntries++;

                if (totalEntries > MAX_ZIP_ENTRIES) {
                    throw new CamtParseException(
                            "ZIP file exceeds maximum allowed entries (%d)".formatted(MAX_ZIP_ENTRIES));
                }

                String entryName = sanitize(entry.getName());

                if (entry.getName().contains("..") || entry.getName().startsWith("/")
                        || entry.getName().startsWith("\\")) {
                    throw new CamtParseException("Invalid ZIP entry path: " + entryName);
                }

                if (entry.isDirectory() || !entryName.toLowerCase().endsWith(".xml")) {
                    log.debug("Skipping non-XML entry: {}", entryName);
                    zis.closeEntry();
                    continue;
                }

                if (entry.getCompressedSize() > 0 && entry.getSize() > 0) {
                    double ratio = (double) entry.getSize() / entry.getCompressedSize();
                    if (ratio > MAX_COMPRESSION_RATIO) {
                        throw new CamtParseException(
                                "ZIP entry '%s' has suspicious compression ratio (%.0f:1)"
                                        .formatted(entryName, ratio));
                    }
                }

                log.info("Processing XML entry: {}", entryName);

                var countingStream = new CountingNonClosingInputStream(zis);
                List<Transaction> transactions = parserService.parse(countingStream);

                totalBytesRead += countingStream.getBytesRead();
                if (totalBytesRead > MAX_TOTAL_UNCOMPRESSED_BYTES) {
                    throw new CamtParseException(
                            "ZIP decompressed content exceeds maximum allowed size (%d MB)"
                                    .formatted(MAX_TOTAL_UNCOMPRESSED_BYTES / (1024 * 1024)));
                }

                allTransactions.addAll(transactions);
                if (allTransactions.size() > MAX_TRANSACTIONS) {
                    throw new CamtParseException(
                            "Total transactions exceed maximum allowed (%d)".formatted(MAX_TRANSACTIONS));
                }

                filesProcessed++;
                log.info("Parsed {} transactions from {}", transactions.size(), entryName);
                zis.closeEntry();
            }
        }

        if (filesProcessed == 0) {
            throw new CamtParseException("ZIP file contains no XML files");
        }

        log.info("Total: {} transactions from {} XML files", allTransactions.size(), filesProcessed);

        return buildResult(allTransactions, filesProcessed);
    }

    public ProcessingResult processXml(InputStream xmlStream, String filename) {
        String safeFilename = sanitize(filename);
        log.info("Processing XML file: {}", safeFilename);

        List<Transaction> transactions = parserService.parse(xmlStream);

        if (transactions.isEmpty()) {
            throw new CamtParseException("XML file contains no transactions");
        }

        if (transactions.size() > MAX_TRANSACTIONS) {
            throw new CamtParseException(
                    "Total transactions exceed maximum allowed (%d)".formatted(MAX_TRANSACTIONS));
        }

        log.info("Parsed {} transactions from {}", transactions.size(), safeFilename);

        return buildResult(transactions, 1);
    }

    private ProcessingResult buildResult(List<Transaction> transactions, int filesProcessed) {
        // Categorize transactions (keyword matching + AI fallback) and apply results
        List<String> categories = categorizationService.categorize(transactions);
        for (int i = 0; i < transactions.size(); i++) {
            transactions.set(i, transactions.get(i).withCategory(categories.get(i)));
        }

        boolean uploaded = false;
        String message;

        if (sheetsService.isConfigured()) {
            try {
                sheetsService.uploadTransactions(transactions);
                uploaded = true;
                message = "Parsed %d transactions from %d files and uploaded to Google Sheets"
                        .formatted(transactions.size(), filesProcessed);
            } catch (Exception e) {
                log.error("Failed to upload to Google Sheets", e);
                message = "Parsed %d transactions from %d files but failed to upload to Google Sheets"
                        .formatted(transactions.size(), filesProcessed);
            }
        } else {
            message = "Parsed %d transactions from %d files (Google Sheets URL not configured, skipping upload)"
                    .formatted(transactions.size(), filesProcessed);
        }

        return new ProcessingResult(filesProcessed, transactions.size(), uploaded, message);
    }

    /**
     * Strips control characters (newlines, tabs, etc.) to prevent log injection.
     */
    static String sanitize(String input) {
        if (input == null) return "unknown";
        return input.replaceAll("[\\r\\n\\t]", "_");
    }

    /**
     * Wraps an InputStream to:
     * 1. Prevent close() from closing the underlying ZipInputStream
     * 2. Count total bytes read for ZIP bomb detection
     */
    static class CountingNonClosingInputStream extends InputStream {
        private final InputStream delegate;
        private long bytesRead;

        CountingNonClosingInputStream(InputStream delegate) {
            this.delegate = delegate;
        }

        long getBytesRead() {
            return bytesRead;
        }

        @Override
        public int read() throws IOException {
            int b = delegate.read();
            if (b != -1) bytesRead++;
            return b;
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            int n = delegate.read(b, off, len);
            if (n > 0) bytesRead += n;
            return n;
        }

        @Override
        public int available() throws IOException {
            return delegate.available();
        }

        @Override
        public long skip(long n) throws IOException {
            long skipped = delegate.skip(n);
            bytesRead += skipped;
            return skipped;
        }

        @Override
        public void close() {
            // intentionally do nothing — ZipInputStream manages the lifecycle
        }
    }
}
