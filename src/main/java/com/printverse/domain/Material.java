package com.printverse.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "materials")
public class Material {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String name;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal pricePerKg;

    @Column(length = 60)
    private String materialType;

    @Column(length = 100)
    private String brand;

    @Column(length = 80)
    private String color;

    @Column(precision = 14, scale = 3)
    private BigDecimal stockGrams;

    @Column(precision = 14, scale = 3)
    private BigDecimal lowStockThresholdGrams;

    @Column(length = 1000)
    private String notes;

    @Column(nullable = false)
    private boolean active;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    protected Material() {
    }

    public Material(String name, BigDecimal pricePerKg, boolean active) {
        this(name, pricePerKg, active, null, null, null, null, null, null);
    }

    public Material(String name, BigDecimal pricePerKg, boolean active, String materialType,
                    String brand, String color, BigDecimal stockGrams,
                    BigDecimal lowStockThresholdGrams, String notes) {
        this.name = name;
        this.pricePerKg = pricePerKg;
        this.active = active;
        this.materialType = materialType;
        this.brand = brand;
        this.color = color;
        this.stockGrams = stockGrams;
        this.lowStockThresholdGrams = lowStockThresholdGrams;
        this.notes = notes;
    }

    @PrePersist
    void prePersist() {
        createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public BigDecimal getPricePerKg() { return pricePerKg; }
    public void setPricePerKg(BigDecimal pricePerKg) { this.pricePerKg = pricePerKg; }
    public String getMaterialType() { return materialType; }
    public void setMaterialType(String materialType) { this.materialType = materialType; }
    public String getBrand() { return brand; }
    public void setBrand(String brand) { this.brand = brand; }
    public String getColor() { return color; }
    public void setColor(String color) { this.color = color; }
    public BigDecimal getStockGrams() { return stockGrams; }
    public void setStockGrams(BigDecimal stockGrams) { this.stockGrams = stockGrams; }
    public BigDecimal getLowStockThresholdGrams() { return lowStockThresholdGrams; }
    public void setLowStockThresholdGrams(BigDecimal lowStockThresholdGrams) {
        this.lowStockThresholdGrams = lowStockThresholdGrams;
    }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public Instant getCreatedAt() { return createdAt; }
}
