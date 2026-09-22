package com.printverse.domain;

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
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;

@Entity
@Table(name = "production_order_items")
public class ProductionOrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "production_order_id", nullable = false)
    private ProductionOrder productionOrder;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "quote_item_id", nullable = false)
    private QuoteItem quoteItem;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(nullable = false)
    private int quantity;

    @Column(nullable = false, length = 100)
    private String materialName;

    @Column(nullable = false, length = 100)
    private String printerName;

    @Column(length = 100)
    private String printerModel;

    @Column(nullable = false, precision = 14, scale = 3)
    private BigDecimal weightGrams;

    @Column(nullable = false)
    private int printTimeMinutes;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_printer_id")
    private Printer assignedPrinter;

    @Column(nullable = false)
    private int completedQuantity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ProductionOrderItemStatus status = ProductionOrderItemStatus.PENDING;

    @Column(length = 2000)
    private String notes;

    @Version
    @Column(nullable = false)
    private long version;

    protected ProductionOrderItem() {
    }

    public ProductionOrderItem(QuoteItem source) {
        quoteItem = source;
        name = source.getName();
        quantity = source.getQuantity();
        materialName = source.getMaterialNameSnapshot() != null
                ? source.getMaterialNameSnapshot() : source.getMaterial().getName();
        printerName = source.getPrinterNameSnapshot() != null
                ? source.getPrinterNameSnapshot() : source.getPrinter().getName();
        printerModel = source.getPrinterNameSnapshot() != null
                ? source.getPrinterModelSnapshot() : source.getPrinter().getModel();
        weightGrams = source.getWeightGrams();
        printTimeMinutes = source.getPrintTimeMinutes();
    }

    void setProductionOrder(ProductionOrder productionOrder) {
        this.productionOrder = productionOrder;
    }

    public void keepPending(Printer assignedPrinter, String notes) {
        this.assignedPrinter = assignedPrinter;
        status = ProductionOrderItemStatus.PENDING;
        completedQuantity = 0;
        this.notes = notes;
    }

    public void start(Printer assignedPrinter, int completedQuantity, String notes) {
        this.assignedPrinter = assignedPrinter;
        status = ProductionOrderItemStatus.IN_PROGRESS;
        this.completedQuantity = completedQuantity;
        this.notes = notes;
    }

    public void updateInProgress(int completedQuantity, String notes) {
        this.completedQuantity = completedQuantity;
        this.notes = notes;
    }

    public void block(Printer assignedPrinter, int completedQuantity, String notes) {
        this.assignedPrinter = assignedPrinter;
        status = ProductionOrderItemStatus.BLOCKED;
        this.completedQuantity = completedQuantity;
        this.notes = notes;
    }

    public void complete(String notes) {
        status = ProductionOrderItemStatus.COMPLETED;
        completedQuantity = quantity;
        this.notes = notes;
    }

    public Long getId() { return id; }
    public ProductionOrder getProductionOrder() { return productionOrder; }
    public QuoteItem getQuoteItem() { return quoteItem; }
    public String getName() { return name; }
    public int getQuantity() { return quantity; }
    public String getMaterialName() { return materialName; }
    public String getPrinterName() { return printerName; }
    public String getPrinterModel() { return printerModel; }
    public BigDecimal getWeightGrams() { return weightGrams; }
    public int getPrintTimeMinutes() { return printTimeMinutes; }
    public Printer getAssignedPrinter() { return assignedPrinter; }
    public int getCompletedQuantity() { return completedQuantity; }
    public ProductionOrderItemStatus getStatus() { return status; }
    public String getNotes() { return notes; }
    public long getVersion() { return version; }
}
