package com.printverse.controller;

import com.printverse.dto.QuoteDtos;
import com.printverse.service.QuoteService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/quotes")
public class QuoteController {

    private final QuoteService service;

    public QuoteController(QuoteService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<QuoteDtos.Response> create(@Valid @RequestBody QuoteDtos.CreateRequest request) {
        QuoteDtos.Response response = service.create(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}")
                .buildAndExpand(response.id()).toUri();
        return ResponseEntity.created(location).body(response);
    }

    @GetMapping
    public List<QuoteDtos.SummaryResponse> list() {
        return service.list();
    }

    @GetMapping("/{id}")
    public QuoteDtos.Response get(@PathVariable Long id) {
        return service.get(id);
    }

    @PutMapping("/{id}")
    public QuoteDtos.Response update(@PathVariable Long id, @Valid @RequestBody QuoteDtos.UpdateRequest request) {
        return service.update(id, request);
    }

    @PostMapping("/{quoteId}/items")
    public ResponseEntity<QuoteDtos.Response> addItem(@PathVariable Long quoteId,
                                                      @Valid @RequestBody QuoteDtos.ItemRequest request) {
        return ResponseEntity.status(201).body(service.addItem(quoteId, request));
    }

    @PutMapping("/{quoteId}/items/{itemId}")
    public QuoteDtos.Response updateItem(@PathVariable Long quoteId, @PathVariable Long itemId,
                                         @Valid @RequestBody QuoteDtos.ItemRequest request) {
        return service.updateItem(quoteId, itemId, request);
    }

    @DeleteMapping("/{quoteId}/items/{itemId}")
    public ResponseEntity<Void> removeItem(@PathVariable Long quoteId, @PathVariable Long itemId) {
        service.removeItem(quoteId, itemId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{quoteId}/items/{itemId}/charges")
    public ResponseEntity<QuoteDtos.Response> addCharge(@PathVariable Long quoteId, @PathVariable Long itemId,
                                                        @Valid @RequestBody QuoteDtos.ChargeRequest request) {
        return ResponseEntity.status(201).body(service.addCharge(quoteId, itemId, request));
    }

    @DeleteMapping("/{quoteId}/items/{itemId}/charges/{chargeId}")
    public ResponseEntity<Void> removeCharge(@PathVariable Long quoteId, @PathVariable Long itemId,
                                             @PathVariable Long chargeId) {
        service.removeCharge(quoteId, itemId, chargeId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/recalculate")
    public QuoteDtos.Response recalculate(@PathVariable Long id) {
        return service.recalculate(id);
    }

    @PatchMapping("/{id}/status")
    public QuoteDtos.Response changeStatus(@PathVariable Long id,
                                           @Valid @RequestBody QuoteDtos.StatusRequest request) {
        return service.changeStatus(id, request.status());
    }
}
