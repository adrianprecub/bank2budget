package com.bank2budget.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "app.google-sheets.url=")
@AutoConfigureMockMvc
class PaymentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void shouldProcessZipWithSingleXmlFile() throws Exception {
        byte[] zip = createZip("statement.xml", "/sample-camt053.xml");

        MockMultipartFile file = new MockMultipartFile(
                "file", "statements.zip", "application/zip", zip);

        mockMvc.perform(multipart("/api/payments/upload").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fileCount").value(1))
                .andExpect(jsonPath("$.transactionCount").value(3))
                .andExpect(jsonPath("$.transactions").doesNotExist())
                .andExpect(jsonPath("$.uploadedToGoogleSheets").value(false))
                .andExpect(jsonPath("$.message").value(
                        "Parsed 3 transactions from 1 files (Google Sheets URL not configured, skipping upload)"));
    }

    @Test
    void shouldProcessZipWithMultipleXmlFiles() throws Exception {
        byte[] zip = createZip(
                "camt053-v2.xml", "/sample-camt053.xml",
                "camt053-v8.xml", "/sample-camt053-v8.xml");

        MockMultipartFile file = new MockMultipartFile(
                "file", "statements.zip", "application/zip", zip);

        mockMvc.perform(multipart("/api/payments/upload").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fileCount").value(2))
                .andExpect(jsonPath("$.transactionCount").value(4))
                .andExpect(jsonPath("$.uploadedToGoogleSheets").value(false));
    }

    @Test
    void shouldRejectZipWithNoXmlFiles() throws Exception {
        byte[] zip = createZipFromBytes("readme.txt", "just a text file".getBytes());

        MockMultipartFile file = new MockMultipartFile(
                "file", "noxml.zip", "application/zip", zip);

        mockMvc.perform(multipart("/api/payments/upload").file(file))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid CAMT.053 file"));
    }

    @Test
    void shouldRejectZipWithInvalidXml() throws Exception {
        byte[] zip = createZipFromBytes("bad.xml", "<invalid>data</invalid>".getBytes());

        MockMultipartFile file = new MockMultipartFile(
                "file", "bad.zip", "application/zip", zip);

        mockMvc.perform(multipart("/api/payments/upload").file(file))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid CAMT.053 file"));
    }

    @Test
    void shouldRejectNonZipContentType() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "data.xml", MediaType.APPLICATION_XML_VALUE,
                "<data/>".getBytes());

        mockMvc.perform(multipart("/api/payments/upload").file(file))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid CAMT.053 file"))
                .andExpect(jsonPath("$.detail").value(
                        "Invalid file type: expected a ZIP file but received 'application/xml'"));
    }

    @Test
    void shouldRejectZipWithPathTraversal() throws Exception {
        byte[] zip = createZipFromBytes("../../etc/passwd.xml",
                "<Document><BkToCstmrStmt/></Document>".getBytes());

        MockMultipartFile file = new MockMultipartFile(
                "file", "evil.zip", "application/zip", zip);

        mockMvc.perform(multipart("/api/payments/upload").file(file))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Invalid ZIP entry path: ../../etc/passwd.xml"));
    }

    // --- XML endpoint tests ---

    @Test
    void shouldProcessSingleXmlFile() throws Exception {
        byte[] xml = getClass().getResourceAsStream("/sample-camt053.xml").readAllBytes();

        MockMultipartFile file = new MockMultipartFile(
                "file", "statement.xml", MediaType.APPLICATION_XML_VALUE, xml);

        mockMvc.perform(multipart("/api/payments/upload-xml").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fileCount").value(1))
                .andExpect(jsonPath("$.transactionCount").value(3))
                .andExpect(jsonPath("$.uploadedToGoogleSheets").value(false))
                .andExpect(jsonPath("$.message").value(
                        "Parsed 3 transactions from 1 files (Google Sheets URL not configured, skipping upload)"));
    }

    @Test
    void shouldProcessSingleXmlFileV8() throws Exception {
        byte[] xml = getClass().getResourceAsStream("/sample-camt053-v8.xml").readAllBytes();

        MockMultipartFile file = new MockMultipartFile(
                "file", "statement-v8.xml", MediaType.APPLICATION_XML_VALUE, xml);

        mockMvc.perform(multipart("/api/payments/upload-xml").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fileCount").value(1))
                .andExpect(jsonPath("$.transactionCount").value(1))
                .andExpect(jsonPath("$.uploadedToGoogleSheets").value(false));
    }

    @Test
    void shouldRejectInvalidXmlFile() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "bad.xml", MediaType.APPLICATION_XML_VALUE,
                "<invalid>data</invalid>".getBytes());

        mockMvc.perform(multipart("/api/payments/upload-xml").file(file))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid CAMT.053 file"));
    }

    @Test
    void shouldRejectNonXmlContentTypeOnXmlEndpoint() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "data.zip", "application/zip",
                "not xml".getBytes());

        mockMvc.perform(multipart("/api/payments/upload-xml").file(file))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid CAMT.053 file"))
                .andExpect(jsonPath("$.detail").value(
                        "Invalid file type: expected a XML file but received 'application/zip'"));
    }

    /**
     * Creates a ZIP file with entries from classpath resources.
     * Arguments are pairs of (entryName, classpathResource).
     */
    private byte[] createZip(String... nameResourcePairs) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            for (int i = 0; i < nameResourcePairs.length; i += 2) {
                String entryName = nameResourcePairs[i];
                String resource = nameResourcePairs[i + 1];
                zos.putNextEntry(new ZipEntry(entryName));
                try (InputStream is = getClass().getResourceAsStream(resource)) {
                    is.transferTo(zos);
                }
                zos.closeEntry();
            }
        }
        return baos.toByteArray();
    }

    /**
     * Creates a ZIP file with a single entry from raw bytes.
     */
    private byte[] createZipFromBytes(String entryName, byte[] content) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            zos.putNextEntry(new ZipEntry(entryName));
            zos.write(content);
            zos.closeEntry();
        }
        return baos.toByteArray();
    }
}
