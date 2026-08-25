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
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Entity
@Table(name = "production_orders")
public class ProductionOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "quote_id", nullable = false, unique = true)
    private Quote quote;

    @Column(nullable = false, unique = true, length = 40)
    private String orderNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ProductionOrderStatus status = ProductionOrderStatus.PENDING;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private ProductionOrderPriority priority;

    private LocalDate dueDate;

    @Column(length = 3000)
    private String notes;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    private Instant startedAt;
    private Instant readyAt;
    private Instant deliveredAt;
    private Instant cancelledAt;

    @OneToMany(mappedBy = "productionOrder", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    private List<ProductionOrderItem> items = new ArrayList<>();

    protected ProductionOrder() {
    }

    public ProductionOrder(Quote quote, String orderNumber, LocalDate dueDate,
                           ProductionOrderPriority priority, String notes) {
        this.quote = quote;
        this.orderNumber = orderNumber;
        this.dueDate = dueDate;
        this.priority = priority;
        this.notes = notes;
    }

    @PrePersist
    void prePersist() {
        createdAt = Instant.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }

    public void addItem(ProductionOrderItem item) {
        items.add(item);
        item.setProductionOrder(this);
    }

    public void changeStatus(ProductionOrderStatus target) {
        Instant now = Instant.now();
        status = target;
        updatedAt = now;
        if (target == ProductionOrderStatus.IN_PRODUCTION) {
            startedAt = now;
        } else if (target == ProductionOrderStatus.READY) {
            readyAt = now;
        } else if (target == ProductionOrderStatus.DELIVERED) {
            deliveredAt = now;
        } else if (target == ProductionOrderStatus.CANCELLED) {
            cancelledAt = now;
        }
    }

    public void touch() {
        updatedAt = Instant.now();
    }

    public Long getId() { return id; }
    public Quote getQuote() { return quote; }
    public String getOrderNumber() { return orderNumber; }
    public ProductionOrderStatus getStatus() { return status; }
    public ProductionOrderPriority getPriority() { return priority; }
    public LocalDate getDueDate() { return dueDate; }
    public String getNotes() { return notes; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getReadyAt() { return readyAt; }
    public Instant getDeliveredAt() { return deliveredAt; }
    public Instant getCancelledAt() { return cancelledAt; }
    public List<ProductionOrderItem> getItems() { return Collections.unmodifiableList(items); }
}
