package com.printverse.controller;

import com.printverse.dto.ProductionOrderDtos;
import com.printverse.service.ProductionOrderService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;

@RestController
public class ProductionOrderController {

    private final ProductionOrderService service;

    public ProductionOrderController(ProductionOrderService service) {
        this.service = service;
    }

    @PostMapping("/api/quotes/{quoteId}/production-order")
    public ResponseEntity<ProductionOrderDtos.Response> convert(
            @PathVariable @Positive Long quoteId,
            @Valid @RequestBody(required = false) ProductionOrderDtos.CreateRequest request) {
        ProductionOrderDtos.ConversionResult result = service.convert(quoteId, request);
        if (!result.created()) {
            return ResponseEntity.ok(result.order());
        }
        URI location = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/api/production-orders/{id}").buildAndExpand(result.order().id()).toUri();
        return ResponseEntity.created(location).body(result.order());
    }

    @GetMapping("/api/production-orders")
    public List<ProductionOrderDtos.Response> list() {
        return service.list();
    }

    @GetMapping("/api/production-orders/{id}")
    public ProductionOrderDtos.Response get(@PathVariable @Positive Long id) {
        return service.get(id);
    }

    @GetMapping("/api/production-orders/by-quote/{quoteId}")
    public ProductionOrderDtos.Response getByQuote(@PathVariable @Positive Long quoteId) {
        return service.getByQuote(quoteId);
    }

    @PatchMapping("/api/production-orders/{id}/status")
    public ProductionOrderDtos.Response changeStatus(@PathVariable @Positive Long id,
            @Valid @RequestBody ProductionOrderDtos.StatusRequest request) {
        return service.changeStatus(id, request.status());
    }

    @PatchMapping("/api/production-orders/{orderId}/items/{itemId}")
    public ProductionOrderDtos.Response updateItem(@PathVariable @Positive Long orderId,
            @PathVariable @Positive Long itemId,
            @Valid @RequestBody ProductionOrderDtos.ItemUpdateRequest request) {
        return service.updateItem(orderId, itemId, request);
    }
}
