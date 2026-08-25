package com.printverse.dto;

import com.printverse.domain.QuoteStatus;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public final class CustomerDtos {

    private CustomerDtos() {
    }

    public record Request(
            @NotBlank @Size(max = 150) String name,
            @NotBlank @Size(max = 40) String phone,
            @Email @Size(max = 254) String email,
            @Size(max = 2000) String notes) {
    }

    public record Response(Long id, String name, String phone, String email, String notes, Instant createdAt) {
    }

    public record Overview(
            Response customer,
            Map<QuoteStatus, Long> quoteCounts,
            BigDecimal totalAcceptedRevenue,
            BigDecimal totalEstimatedProfit,
            List<QuoteDtos.SummaryResponse> recentQuotes,
            long productionOrderCount) {
    }
}
