package com.printverse.repository;

import com.printverse.domain.Material;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface MaterialRepository extends JpaRepository<Material, Long> {
    boolean existsByNameIgnoreCase(String name);
    boolean existsByNameIgnoreCaseAndIdNot(String name, Long id);

    @Query("select material from Material material where material.active = true "
            + "and material.stockGrams is not null and material.lowStockThresholdGrams is not null "
            + "and material.stockGrams <= material.lowStockThresholdGrams order by material.stockGrams, material.name")
    List<Material> findActiveLowStock();
}
