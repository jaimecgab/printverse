package com.printverse.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Entity
@Table(name = "quotes")
public class Quote {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 30)
    private String quoteNumber;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private QuoteStatus status = QuoteStatus.DRAFT;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    private Instant sentAt;

    private Instant acceptedAt;

    private Instant rejectedAt;

    @Column(nullable = false)
    private LocalDate validUntil;

    private LocalDate estimatedDeliveryDate;

    @Column(precision = 7, scale = 4)
    private BigDecimal depositPercentage;

    @Column(length = 3000)
    private String notes;

    @Column(length = 200)
    private String title;

    @Column(length = 3000)
    private String internalNotes;

    @Column(nullable = false, length = 3, updatable = false)
    private String currencyCode = "MXN";

    @Column(length = 150)
    private String customerNameSnapshot;

    @Column(length = 40)
    private String customerPhoneSnapshot;

    @Column(length = 254)
    private String customerEmailSnapshot;

    @Column(nullable = false, precision = 7, scale = 4)
    private BigDecimal markupPercentage;

    @Column(nullable = false, precision = 7, scale = 4)
    private BigDecimal discountPercentage = BigDecimal.ZERO;

    @Column(nullable = false)
    private boolean taxEnabled;

    @Column(nullable = false, precision = 7, scale = 4)
    private BigDecimal taxPercentage = BigDecimal.ZERO;

    @Column(nullable = false, precision = 16, scale = 2)
    private BigDecimal internalCost = BigDecimal.ZERO;

    @Column(nullable = false, precision = 16, scale = 2)
    private BigDecimal suggestedSubtotal = BigDecimal.ZERO;

    @Column(nullable = false, precision = 16, scale = 2)
    private BigDecimal finalSubtotal = BigDecimal.ZERO;

    @Column(nullable = false, precision = 16, scale = 2)
    private BigDecimal discountAmount = BigDecimal.ZERO;

    @Column(nullable = false, precision = 16, scale = 2)
    private BigDecimal taxAmount = BigDecimal.ZERO;

    @Column(nullable = false, precision = 16, scale = 2)
    private BigDecimal total = BigDecimal.ZERO;

    @Column(nullable = false, precision = 16, scale = 2)
    private BigDecimal estimatedProfit = BigDecimal.ZERO;

    @Column(nullable = false, precision = 10, scale = 4)
    private BigDecimal realMarginPercentage = BigDecimal.ZERO;

    @OneToMany(mappedBy = "quote", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    private List<QuoteItem> items = new ArrayList<>();

    protected Quote() {
    }

    public Quote(String quoteNumber, Customer customer, LocalDate validUntil,
                 LocalDate estimatedDeliveryDate, BigDecimal depositPercentage,
                 String notes, BigDecimal markupPercentage,
                 BigDecimal discountPercentage, boolean taxEnabled, BigDecimal taxPercentage) {
        this.quoteNumber = quoteNumber;
        this.customer = customer;
        this.validUntil = validUntil;
        this.estimatedDeliveryDate = estimatedDeliveryDate;
        this.depositPercentage = depositPercentage;
        this.notes = notes;
        this.markupPercentage = markupPercentage;
        this.discountPercentage = discountPercentage;
        this.taxEnabled = taxEnabled;
        this.taxPercentage = taxPercentage;
    }

    @PrePersist
    void prePersist() {
        createdAt = Instant.now();
        updatedAt = createdAt;
    }

    public void addItem(QuoteItem item) {
        items.add(item);
        item.setQuote(this);
    }

    public void removeItem(QuoteItem item) {
        items.remove(item);
        item.setQuote(null);
    }

    public void transitionTo(QuoteStatus targetStatus) {
        Instant now = Instant.now();
        status = targetStatus;
        if (targetStatus == QuoteStatus.SENT) {
            sentAt = now;
            customerNameSnapshot = customer.getName();
            customerPhoneSnapshot = customer.getPhone();
            customerEmailSnapshot = customer.getEmail();
        } else if (targetStatus == QuoteStatus.ACCEPTED) {
            acceptedAt = now;
        } else if (targetStatus == QuoteStatus.REJECTED) {
            rejectedAt = now;
        }
        updatedAt = now;
    }

    public void copyCustomerSnapshotsFrom(Quote source) {
        customerNameSnapshot = source.customerNameSnapshot;
        customerPhoneSnapshot = source.customerPhoneSnapshot;
        customerEmailSnapshot = source.customerEmailSnapshot;
    }

    public void touch() {
        updatedAt = Instant.now();
    }

    public Long getId() { return id; }
    public String getQuoteNumber() { return quoteNumber; }
    public Customer getCustomer() { return customer; }
    public QuoteStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getSentAt() { return sentAt; }
    public Instant getAcceptedAt() { return acceptedAt; }
    public Instant getRejectedAt() { return rejectedAt; }
    public LocalDate getValidUntil() { return validUntil; }
    public void setValidUntil(LocalDate validUntil) { this.validUntil = validUntil; }
    public LocalDate getEstimatedDeliveryDate() { return estimatedDeliveryDate; }
    public void setEstimatedDeliveryDate(LocalDate date) { this.estimatedDeliveryDate = date; }
    public BigDecimal getDepositPercentage() { return depositPercentage; }
    public void setDepositPercentage(BigDecimal value) { this.depositPercentage = value; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getInternalNotes() { return internalNotes; }
    public void setInternalNotes(String internalNotes) { this.internalNotes = internalNotes; }
    public String getCurrencyCode() { return currencyCode; }
    public String getCustomerNameSnapshot() { return customerNameSnapshot; }
    public String getCustomerPhoneSnapshot() { return customerPhoneSnapshot; }
    public String getCustomerEmailSnapshot() { return customerEmailSnapshot; }
    public BigDecimal getMarkupPercentage() { return markupPercentage; }
    public void setMarkupPercentage(BigDecimal value) { this.markupPercentage = value; }
    public BigDecimal getDiscountPercentage() { return discountPercentage; }
    public void setDiscountPercentage(BigDecimal value) { this.discountPercentage = value; }
    public boolean isTaxEnabled() { return taxEnabled; }
    public void setTaxEnabled(boolean taxEnabled) { this.taxEnabled = taxEnabled; }
    public BigDecimal getTaxPercentage() { return taxPercentage; }
    public void setTaxPercentage(BigDecimal value) { this.taxPercentage = value; }
    public BigDecimal getInternalCost() { return internalCost; }
    public void setInternalCost(BigDecimal value) { this.internalCost = value; }
    public BigDecimal getSuggestedSubtotal() { return suggestedSubtotal; }
    public void setSuggestedSubtotal(BigDecimal value) { this.suggestedSubtotal = value; }
    public BigDecimal getFinalSubtotal() { return finalSubtotal; }
    public void setFinalSubtotal(BigDecimal value) { this.finalSubtotal = value; }
    public BigDecimal getDiscountAmount() { return discountAmount; }
    public void setDiscountAmount(BigDecimal value) { this.discountAmount = value; }
    public BigDecimal getTaxAmount() { return taxAmount; }
    public void setTaxAmount(BigDecimal value) { this.taxAmount = value; }
    public BigDecimal getTotal() { return total; }
    public void setTotal(BigDecimal value) { this.total = value; }
    public BigDecimal getEstimatedProfit() { return estimatedProfit; }
    public void setEstimatedProfit(BigDecimal value) { this.estimatedProfit = value; }
    public BigDecimal getRealMarginPercentage() { return realMarginPercentage; }
    public void setRealMarginPercentage(BigDecimal value) { this.realMarginPercentage = value; }
    public List<QuoteItem> getItems() { return Collections.unmodifiableList(items); }
}
