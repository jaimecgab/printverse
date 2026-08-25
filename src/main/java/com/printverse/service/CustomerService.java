package com.printverse.service;

import com.printverse.domain.Customer;
import com.printverse.domain.Quote;
import com.printverse.domain.QuoteStatus;
import com.printverse.dto.CustomerDtos;
import com.printverse.exception.ResourceNotFoundException;
import com.printverse.repository.CustomerRepository;
import com.printverse.repository.ProductionOrderRepository;
import com.printverse.repository.QuoteRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static com.printverse.service.MoneyUtils.money;

@Service
public class CustomerService {

    private final CustomerRepository repository;
    private final QuoteRepository quoteRepository;
    private final ProductionOrderRepository productionOrderRepository;

    public CustomerService(CustomerRepository repository, QuoteRepository quoteRepository,
                           ProductionOrderRepository productionOrderRepository) {
        this.repository = repository;
        this.quoteRepository = quoteRepository;
        this.productionOrderRepository = productionOrderRepository;
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

    @Transactional(readOnly = true)
    public CustomerDtos.Overview overview(Long id) {
        Customer customer = find(id);
        List<Quote> quotes = quoteRepository.findByCustomerIdOrderByCreatedAtDesc(id);
        Map<QuoteStatus, Long> counts = new EnumMap<>(QuoteStatus.class);
        Arrays.stream(QuoteStatus.values()).forEach(status -> counts.put(status, 0L));
        BigDecimal revenue = BigDecimal.ZERO;
        BigDecimal profit = BigDecimal.ZERO;
        for (Quote quote : quotes) {
            counts.compute(quote.getStatus(), (status, count) -> count + 1);
            if (quote.getStatus() == QuoteStatus.ACCEPTED) {
                revenue = revenue.add(quote.getFinalSubtotal().subtract(quote.getDiscountAmount()));
                profit = profit.add(quote.getEstimatedProfit());
            }
        }
        return new CustomerDtos.Overview(toResponse(customer), counts, money(revenue), money(profit),
                quotes.stream().limit(10).map(QuoteService::toSummaryResponse).toList(),
                productionOrderRepository.countByQuoteCustomerId(id));
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
