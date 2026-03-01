package com.bank2budget.controller;

import com.bank2budget.exception.CamtParseException;
import com.bank2budget.model.ProcessingResult;
import com.bank2budget.service.PaymentProcessingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    private static final Logger log = LoggerFactory.getLogger(PaymentController.class);

    private final PaymentProcessingService processingService;

    public PaymentController(PaymentProcessingService processingService) {
        this.processingService = processingService;
    }

    @PostMapping(value = "/upload", consumes = "multipart/form-data")
    public ResponseEntity<ProcessingResult> uploadCamtZip(
            @RequestParam("file") MultipartFile file) throws IOException {

        validateContentType(file, "zip");

        log.info("Received ZIP file: {} ({} bytes)",
                file.getOriginalFilename(), file.getSize());

        ProcessingResult result = processingService.processZip(
                file.getInputStream(), file.getOriginalFilename());
        return ResponseEntity.ok(result);
    }

    @PostMapping(value = "/upload-xml", consumes = "multipart/form-data")
    public ResponseEntity<ProcessingResult> uploadCamtXml(
            @RequestParam("file") MultipartFile file) throws IOException {

        validateContentType(file, "xml");

        log.info("Received XML file: {} ({} bytes)",
                file.getOriginalFilename(), file.getSize());

        ProcessingResult result = processingService.processXml(
                file.getInputStream(), file.getOriginalFilename());
        return ResponseEntity.ok(result);
    }

    private void validateContentType(MultipartFile file, String expected) {
        String contentType = file.getContentType();
        if (contentType == null || !contentType.toLowerCase().contains(expected)) {
            throw new CamtParseException(
                    "Invalid file type: expected a %s file but received '%s'".formatted(expected.toUpperCase(), contentType));
        }
    }
}
