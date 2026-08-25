package com.printverse.controller;

import com.printverse.service.QuotePdfService;
import jakarta.validation.constraints.Positive;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/quotes")
public class QuotePdfController {

    private final QuotePdfService service;

    public QuotePdfController(QuotePdfService service) {
        this.service = service;
    }

    @GetMapping(value = "/{id}/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> pdf(@PathVariable @Positive Long id) {
        QuotePdfService.GeneratedPdf pdf = service.generate(id);
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(pdf.fileName(), StandardCharsets.UTF_8).build();
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .cacheControl(CacheControl.noStore())
                .contentLength(pdf.content().length)
                .body(pdf.content());
    }
}
