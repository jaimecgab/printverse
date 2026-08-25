package com.printverse.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "printers")
public class Printer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String name;

    @Column(length = 100)
    private String model;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal costPerHour;

    @Column(nullable = false)
    private boolean active;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private PrinterOperationalStatus operationalStatus = PrinterOperationalStatus.AVAILABLE;

    @Column(length = 1000)
    private String notes;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    protected Printer() {
    }

    public Printer(String name, String model, BigDecimal costPerHour, boolean active) {
        this(name, model, costPerHour, active, PrinterOperationalStatus.AVAILABLE, null);
    }

    public Printer(String name, String model, BigDecimal costPerHour, boolean active,
                   PrinterOperationalStatus operationalStatus, String notes) {
        this.name = name;
        this.model = model;
        this.costPerHour = costPerHour;
        this.active = active;
        this.operationalStatus = operationalStatus;
        this.notes = notes;
    }

    @PrePersist
    void prePersist() {
        createdAt = Instant.now();
        if (operationalStatus == null) {
            operationalStatus = PrinterOperationalStatus.AVAILABLE;
        }
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }
    public BigDecimal getCostPerHour() { return costPerHour; }
    public void setCostPerHour(BigDecimal costPerHour) { this.costPerHour = costPerHour; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public PrinterOperationalStatus getOperationalStatus() { return operationalStatus; }
    public void setOperationalStatus(PrinterOperationalStatus operationalStatus) {
        this.operationalStatus = operationalStatus;
    }
    public void occupy() { operationalStatus = PrinterOperationalStatus.BUSY; }
    public void release() { operationalStatus = PrinterOperationalStatus.AVAILABLE; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public Instant getCreatedAt() { return createdAt; }
}
