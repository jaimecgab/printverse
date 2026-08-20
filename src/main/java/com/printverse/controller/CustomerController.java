package com.printverse.controller;

import com.printverse.dto.CustomerDtos;
import com.printverse.service.CustomerService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
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
@RequestMapping("/api/customers")
public class CustomerController {

    private final CustomerService service;

    public CustomerController(CustomerService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<CustomerDtos.Response> create(@Valid @RequestBody CustomerDtos.Request request) {
        CustomerDtos.Response response = service.create(request);
        return ResponseEntity.created(resourceUri(response.id())).body(response);
    }

    @GetMapping
    public List<CustomerDtos.Response> list() {
        return service.list();
    }

    @GetMapping("/{id}")
    public CustomerDtos.Response get(@PathVariable Long id) {
        return service.get(id);
    }

    @PutMapping("/{id}")
    public CustomerDtos.Response update(@PathVariable Long id, @Valid @RequestBody CustomerDtos.Request request) {
        return service.update(id, request);
    }

    private static URI resourceUri(Long id) {
        return ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(id).toUri();
    }
}
