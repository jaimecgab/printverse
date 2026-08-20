package com.printverse.service;

import com.printverse.domain.Printer;
import com.printverse.dto.PrinterDtos;
import com.printverse.exception.ConflictException;
import com.printverse.exception.ResourceNotFoundException;
import com.printverse.repository.PrinterRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class PrinterService {

    private final PrinterRepository repository;

    public PrinterService(PrinterRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public PrinterDtos.Response create(PrinterDtos.Request request) {
        String name = CustomerService.clean(request.name());
        if (repository.existsByNameIgnoreCase(name)) {
            throw new ConflictException("A printer named '" + name + "' already exists");
        }
        Printer printer = new Printer(name, CustomerService.nullable(request.model()),
                MoneyUtils.money(request.costPerHour()), request.active());
        return toResponse(repository.save(printer));
    }

    @Transactional(readOnly = true)
    public List<PrinterDtos.Response> list() {
        return repository.findAll().stream().map(PrinterService::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public PrinterDtos.Response get(Long id) {
        return toResponse(find(id));
    }

    @Transactional
    public PrinterDtos.Response update(Long id, PrinterDtos.Request request) {
        Printer printer = find(id);
        String name = CustomerService.clean(request.name());
        if (repository.existsByNameIgnoreCaseAndIdNot(name, id)) {
            throw new ConflictException("A printer named '" + name + "' already exists");
        }
        printer.setName(name);
        printer.setModel(CustomerService.nullable(request.model()));
        printer.setCostPerHour(MoneyUtils.money(request.costPerHour()));
        printer.setActive(request.active());
        return toResponse(printer);
    }

    @Transactional
    public PrinterDtos.Response setActive(Long id, boolean active) {
        Printer printer = find(id);
        printer.setActive(active);
        return toResponse(printer);
    }

    Printer find(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Printer " + id + " was not found"));
    }

    private static PrinterDtos.Response toResponse(Printer value) {
        return new PrinterDtos.Response(value.getId(), value.getName(), value.getModel(),
                value.getCostPerHour(), value.isActive(), value.getCreatedAt());
    }
}
