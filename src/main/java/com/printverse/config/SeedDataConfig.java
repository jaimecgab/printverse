package com.printverse.config;

import com.printverse.domain.Material;
import com.printverse.domain.Printer;
import com.printverse.repository.MaterialRepository;
import com.printverse.repository.PrinterRepository;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.math.BigDecimal;

@Configuration
public class SeedDataConfig {

    @Bean
    @ConditionalOnProperty(name = "app.seed.enabled", havingValue = "true")
    ApplicationRunner seedCatalogs(MaterialRepository materialRepository, PrinterRepository printerRepository) {
        return arguments -> {
            if (!materialRepository.existsByNameIgnoreCase("PLA")) {
                materialRepository.save(new Material("PLA", new BigDecimal("350.00"), true));
            }
            if (!printerRepository.existsByNameIgnoreCase("Creality K1C")) {
                printerRepository.save(new Printer("Creality K1C", "K1C", new BigDecimal("10.00"), true));
            }
        };
    }
}
