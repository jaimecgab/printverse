package com.printverse.controller;

import com.printverse.domain.ProductionOrderPriority;
import com.printverse.domain.ProductionOrderStatus;
import com.printverse.dto.ProductionOrderDtos;
import com.printverse.service.ProductionOrderService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ProductionOrderControllerTest {

    @AfterEach
    void clearRequest() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void conversionReturnsCreatedOnlyTheFirstTime() {
        ProductionOrderService service = mock(ProductionOrderService.class);
        ProductionOrderController controller = new ProductionOrderController(service);
        ProductionOrderDtos.Response order = new ProductionOrderDtos.Response(20L, "OP-TEST", null,
                ProductionOrderStatus.PENDING, ProductionOrderPriority.NORMAL, null, null,
                null, null, null, null, null, null, List.of());
        when(service.convert(10L, null))
                .thenReturn(new ProductionOrderDtos.ConversionResult(order, true))
                .thenReturn(new ProductionOrderDtos.ConversionResult(order, false));
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/quotes/10/production-order");
        request.setServerName("localhost");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        var created = controller.convert(10L, null);
        var existing = controller.convert(10L, null);

        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(created.getHeaders().getLocation()).hasPath("/api/production-orders/20");
        assertThat(existing.getStatusCode()).isEqualTo(HttpStatus.OK);
    }
}
