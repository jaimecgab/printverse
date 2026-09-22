package com.printverse.controller;

import com.printverse.dto.PrinterDtos;
import com.printverse.service.PrinterService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.http.ResponseEntity;
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
@RequestMapping("/api/printers")
public class PrinterController {

    private final PrinterService service;

    public PrinterController(PrinterService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<PrinterDtos.Response> create(@Valid @RequestBody PrinterDtos.Request request) {
        PrinterDtos.Response response = service.create(request);
        return ResponseEntity.created(resourceUri(response.id())).body(response);
    }

    @GetMapping
    public List<PrinterDtos.Response> list() {
        return service.list();
    }

    @GetMapping("/{id}")
    public PrinterDtos.Response get(@PathVariable @Positive Long id) {
        return service.get(id);
    }

    @PutMapping("/{id}")
    public PrinterDtos.Response update(@PathVariable @Positive Long id,
                                       @Valid @RequestBody PrinterDtos.Request request) {
        return service.update(id, request);
    }

    @PatchMapping("/{id}/active")
    public PrinterDtos.Response setActive(@PathVariable @Positive Long id,
                                          @Valid @RequestBody PrinterDtos.ActiveRequest request) {
        return service.setActive(id, request.active());
    }

    private static URI resourceUri(Long id) {
        return ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(id).toUri();
    }
}
