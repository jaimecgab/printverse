package com.printverse.service;

import com.printverse.domain.Printer;
import com.printverse.domain.PrinterOperationalStatus;
import com.printverse.dto.PrinterDtos;
import com.printverse.exception.BusinessRuleException;
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
        if (request.operationalStatus() == PrinterOperationalStatus.BUSY) {
            throw new BusinessRuleException("BUSY is managed by production and cannot be set manually");
        }
        String name = CustomerService.clean(request.name());
        if (repository.existsByNameIgnoreCase(name)) {
            throw new ConflictException("A printer named '" + name + "' already exists");
        }
        Printer printer = new Printer(name, CustomerService.nullable(request.model()),
                MoneyUtils.money(request.costPerHour()), request.active(), request.operationalStatus(),
                CustomerService.nullable(request.notes()));
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
        Printer printer = findForUpdate(id);
        validateAdministrativeState(printer, request.operationalStatus(), request.active());
        String name = CustomerService.clean(request.name());
        if (repository.existsByNameIgnoreCaseAndIdNot(name, id)) {
            throw new ConflictException("A printer named '" + name + "' already exists");
        }
        printer.setName(name);
        printer.setModel(CustomerService.nullable(request.model()));
        printer.setCostPerHour(MoneyUtils.money(request.costPerHour()));
        printer.setOperationalStatus(request.operationalStatus());
        printer.setNotes(CustomerService.nullable(request.notes()));
        printer.setActive(request.active());
        return toResponse(printer);
    }

    @Transactional
    public PrinterDtos.Response setActive(Long id, boolean active) {
        Printer printer = findForUpdate(id);
        if (printer.getOperationalStatus() == PrinterOperationalStatus.BUSY && !active) {
            throw new BusinessRuleException("A BUSY printer cannot be archived");
        }
        printer.setActive(active);
        return toResponse(printer);
    }

    Printer find(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Printer " + id + " was not found"));
    }

    private Printer findForUpdate(Long id) {
        return repository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Printer " + id + " was not found"));
    }

    private static void validateAdministrativeState(Printer printer,
                                                    PrinterOperationalStatus requestedStatus,
                                                    boolean requestedActive) {
        if (printer.getOperationalStatus() == PrinterOperationalStatus.BUSY) {
            if (requestedStatus != PrinterOperationalStatus.BUSY || !requestedActive) {
                throw new BusinessRuleException("A BUSY printer must remain BUSY and active until production releases it");
            }
            return;
        }
        if (requestedStatus == PrinterOperationalStatus.BUSY) {
            throw new BusinessRuleException("BUSY is managed by production and cannot be set manually");
        }
    }

    private static PrinterDtos.Response toResponse(Printer value) {
        return new PrinterDtos.Response(value.getId(), value.getName(), value.getModel(),
                value.getCostPerHour(), value.getOperationalStatus(), value.getNotes(),
                value.isActive(), value.getCreatedAt());
    }
}
