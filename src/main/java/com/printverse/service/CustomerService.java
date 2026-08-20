package com.printverse.service;

import com.printverse.domain.Customer;
import com.printverse.dto.CustomerDtos;
import com.printverse.exception.ResourceNotFoundException;
import com.printverse.repository.CustomerRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class CustomerService {

    private final CustomerRepository repository;

    public CustomerService(CustomerRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public CustomerDtos.Response create(CustomerDtos.Request request) {
        Customer customer = new Customer(clean(request.name()), clean(request.phone()), nullable(request.email()), nullable(request.notes()));
        return toResponse(repository.save(customer));
    }

    @Transactional(readOnly = true)
    public List<CustomerDtos.Response> list() {
        return repository.findAll().stream().map(CustomerService::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public CustomerDtos.Response get(Long id) {
        return toResponse(find(id));
    }

    @Transactional
    public CustomerDtos.Response update(Long id, CustomerDtos.Request request) {
        Customer customer = find(id);
        customer.setName(clean(request.name()));
        customer.setPhone(clean(request.phone()));
        customer.setEmail(nullable(request.email()));
        customer.setNotes(nullable(request.notes()));
        return toResponse(customer);
    }

    Customer find(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Customer " + id + " was not found"));
    }

    private static CustomerDtos.Response toResponse(Customer value) {
        return new CustomerDtos.Response(value.getId(), value.getName(), value.getPhone(),
                value.getEmail(), value.getNotes(), value.getCreatedAt());
    }

    static String clean(String value) {
        return value.trim();
    }

    static String nullable(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
