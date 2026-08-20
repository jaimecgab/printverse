package com.printverse.service;

import com.printverse.domain.Material;
import com.printverse.dto.MaterialDtos;
import com.printverse.exception.ConflictException;
import com.printverse.exception.ResourceNotFoundException;
import com.printverse.repository.MaterialRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class MaterialService {

    private final MaterialRepository repository;

    public MaterialService(MaterialRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public MaterialDtos.Response create(MaterialDtos.Request request) {
        String name = CustomerService.clean(request.name());
        if (repository.existsByNameIgnoreCase(name)) {
            throw new ConflictException("A material named '" + name + "' already exists");
        }
        Material material = new Material(name, MoneyUtils.money(request.pricePerKg()), request.active());
        return toResponse(repository.save(material));
    }

    @Transactional(readOnly = true)
    public List<MaterialDtos.Response> list() {
        return repository.findAll().stream().map(MaterialService::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public MaterialDtos.Response get(Long id) {
        return toResponse(find(id));
    }

    @Transactional
    public MaterialDtos.Response update(Long id, MaterialDtos.Request request) {
        Material material = find(id);
        String name = CustomerService.clean(request.name());
        if (repository.existsByNameIgnoreCaseAndIdNot(name, id)) {
            throw new ConflictException("A material named '" + name + "' already exists");
        }
        material.setName(name);
        material.setPricePerKg(MoneyUtils.money(request.pricePerKg()));
        material.setActive(request.active());
        return toResponse(material);
    }

    @Transactional
    public MaterialDtos.Response setActive(Long id, boolean active) {
        Material material = find(id);
        material.setActive(active);
        return toResponse(material);
    }

    Material find(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Material " + id + " was not found"));
    }

    private static MaterialDtos.Response toResponse(Material value) {
        return new MaterialDtos.Response(value.getId(), value.getName(), value.getPricePerKg(),
                value.isActive(), value.getCreatedAt());
    }
}
