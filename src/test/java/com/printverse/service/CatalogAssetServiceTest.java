package com.printverse.service;

import com.printverse.domain.Material;
import com.printverse.domain.Printer;
import com.printverse.domain.PrinterOperationalStatus;
import com.printverse.dto.MaterialDtos;
import com.printverse.dto.PrinterDtos;
import com.printverse.repository.MaterialRepository;
import com.printverse.repository.PrinterRepository;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CatalogAssetServiceTest {

    @Test
    void mapsAndNormalizesMaterialAssetFields() {
        MaterialRepository repository = mock(MaterialRepository.class);
        when(repository.save(any(Material.class))).thenAnswer(invocation -> invocation.getArgument(0));
        MaterialService service = new MaterialService(repository);

        MaterialDtos.Response response = service.create(new MaterialDtos.Request(
                " PLA rojo ", new BigDecimal("350"), " PLA ", " Marca ", " Rojo ",
                new BigDecimal("750.125"), new BigDecimal("200.000"), " Uso general ", true));

        assertThat(response.name()).isEqualTo("PLA rojo");
        assertThat(response.pricePerKg()).isEqualByComparingTo("350.00");
        assertThat(response.materialType()).isEqualTo("PLA");
        assertThat(response.brand()).isEqualTo("Marca");
        assertThat(response.color()).isEqualTo("Rojo");
        assertThat(response.stockGrams()).isEqualByComparingTo("750.125");
        assertThat(response.lowStockThresholdGrams()).isEqualByComparingTo("200.000");
        assertThat(response.notes()).isEqualTo("Uso general");
    }

    @Test
    void mapsPrinterOperationalFieldsAndRejectsManualBusyCreation() {
        PrinterRepository repository = mock(PrinterRepository.class);
        when(repository.save(any(Printer.class))).thenAnswer(invocation -> invocation.getArgument(0));
        PrinterService service = new PrinterService(repository);

        PrinterDtos.Response response = service.create(new PrinterDtos.Request(
                " MK4 ", " MK4S ", new BigDecimal("12"), PrinterOperationalStatus.AVAILABLE,
                " Preparada ", true));

        assertThat(response.name()).isEqualTo("MK4");
        assertThat(response.model()).isEqualTo("MK4S");
        assertThat(response.costPerHour()).isEqualByComparingTo("12.00");
        assertThat(response.operationalStatus()).isEqualTo(PrinterOperationalStatus.AVAILABLE);
        assertThat(response.notes()).isEqualTo("Preparada");
        assertThatThrownBy(() -> service.create(new PrinterDtos.Request(
                "Busy", null, BigDecimal.ZERO, PrinterOperationalStatus.BUSY, null, true)))
                .hasMessageContaining("managed by production");
    }

    @Test
    void busyPrinterAllowsOnlyMetadataUpdatesThatPreserveItsOperationalState() {
        PrinterRepository repository = mock(PrinterRepository.class);
        Printer busy = new Printer("MK4", "MK4S", new BigDecimal("12"), true,
                PrinterOperationalStatus.BUSY, "Printing");
        ReflectionTestUtils.setField(busy, "id", 9L);
        when(repository.findByIdForUpdate(9L)).thenReturn(Optional.of(busy));
        PrinterService service = new PrinterService(repository);

        PrinterDtos.Response updated = service.update(9L, new PrinterDtos.Request(
                "MK4 updated", "MK4S", new BigDecimal("13"), PrinterOperationalStatus.BUSY,
                "Still printing", true));

        assertThat(updated.name()).isEqualTo("MK4 updated");
        assertThat(updated.operationalStatus()).isEqualTo(PrinterOperationalStatus.BUSY);
        assertThatThrownBy(() -> service.update(9L, new PrinterDtos.Request(
                "MK4 updated", "MK4S", new BigDecimal("13"), PrinterOperationalStatus.AVAILABLE,
                null, true))).hasMessageContaining("must remain BUSY and active");
        assertThatThrownBy(() -> service.setActive(9L, false)).hasMessageContaining("cannot be archived");
    }
}
