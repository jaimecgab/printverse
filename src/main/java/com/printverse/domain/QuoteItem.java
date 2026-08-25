package com.printverse.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Entity
@Table(name = "quote_items")
public class QuoteItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "quote_id", nullable = false)
    private Quote quote;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(nullable = false)
    private int quantity;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "material_id", nullable = false)
    private Material material;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "printer_id", nullable = false)
    private Printer printer;

    @Column(nullable = false, precision = 14, scale = 3)
    private BigDecimal weightGrams;

    @Column(nullable = false)
    private int printTimeMinutes;

    @Column(nullable = false, precision = 7, scale = 4)
    private BigDecimal failureRiskPercentage;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal materialPricePerKgSnapshot;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal printerCostPerHourSnapshot;

    @Column(nullable = false, length = 100)
    private String materialNameSnapshot;

    @Column(nullable = false, length = 100)
    private String printerNameSnapshot;

    @Column(length = 100)
    private String printerModelSnapshot;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal materialCostUnit = BigDecimal.ZERO;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal machineCostUnit = BigDecimal.ZERO;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal failureRiskCostUnit = BigDecimal.ZERO;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal additionalChargesUnit = BigDecimal.ZERO;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal internalCostUnit = BigDecimal.ZERO;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal suggestedPriceUnit = BigDecimal.ZERO;

    @Column(precision = 14, scale = 2)
    private BigDecimal manualUnitPrice;

    @OneToMany(mappedBy = "quoteItem", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    private List<AdditionalCharge> additionalCharges = new ArrayList<>();

    protected QuoteItem() {
    }

    public QuoteItem(String name, int quantity, Material material, Printer printer,
                     BigDecimal weightGrams, int printTimeMinutes,
                     BigDecimal failureRiskPercentage, BigDecimal manualUnitPrice) {
        this.name = name;
        this.quantity = quantity;
        this.material = material;
        this.printer = printer;
        this.weightGrams = weightGrams;
        this.printTimeMinutes = printTimeMinutes;
        this.failureRiskPercentage = failureRiskPercentage;
        this.manualUnitPrice = manualUnitPrice;
        this.materialPricePerKgSnapshot = material.getPricePerKg();
        this.printerCostPerHourSnapshot = printer.getCostPerHour();
        this.materialNameSnapshot = material.getName();
        this.printerNameSnapshot = printer.getName();
        this.printerModelSnapshot = printer.getModel();
    }

    public void addAdditionalCharge(AdditionalCharge charge) {
        additionalCharges.add(charge);
        charge.setQuoteItem(this);
    }

    public void removeAdditionalCharge(AdditionalCharge charge) {
        additionalCharges.remove(charge);
        charge.setQuoteItem(null);
    }

    public void replaceAdditionalCharges(List<AdditionalCharge> charges) {
        additionalCharges.forEach(charge -> charge.setQuoteItem(null));
        additionalCharges.clear();
        charges.forEach(this::addAdditionalCharge);
    }

    public void changeMaterial(Material material) {
        this.material = material;
        materialPricePerKgSnapshot = material.getPricePerKg();
        materialNameSnapshot = material.getName();
    }

    public void changePrinter(Printer printer) {
        this.printer = printer;
        printerCostPerHourSnapshot = printer.getCostPerHour();
        printerNameSnapshot = printer.getName();
        printerModelSnapshot = printer.getModel();
    }

    public void copySnapshotsFrom(QuoteItem source) {
        materialPricePerKgSnapshot = source.materialPricePerKgSnapshot;
        printerCostPerHourSnapshot = source.printerCostPerHourSnapshot;
        materialNameSnapshot = source.materialNameSnapshot;
        printerNameSnapshot = source.printerNameSnapshot;
        printerModelSnapshot = source.printerModelSnapshot;
    }

    public Long getId() { return id; }
    public Quote getQuote() { return quote; }
    void setQuote(Quote quote) { this.quote = quote; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }
    public Material getMaterial() { return material; }
    public void setMaterial(Material material) { this.material = material; }
    public Printer getPrinter() { return printer; }
    public void setPrinter(Printer printer) { this.printer = printer; }
    public BigDecimal getWeightGrams() { return weightGrams; }
    public void setWeightGrams(BigDecimal weightGrams) { this.weightGrams = weightGrams; }
    public int getPrintTimeMinutes() { return printTimeMinutes; }
    public void setPrintTimeMinutes(int printTimeMinutes) { this.printTimeMinutes = printTimeMinutes; }
    public BigDecimal getFailureRiskPercentage() { return failureRiskPercentage; }
    public void setFailureRiskPercentage(BigDecimal failureRiskPercentage) { this.failureRiskPercentage = failureRiskPercentage; }
    public BigDecimal getMaterialPricePerKgSnapshot() { return materialPricePerKgSnapshot; }
    public void setMaterialPricePerKgSnapshot(BigDecimal value) { this.materialPricePerKgSnapshot = value; }
    public BigDecimal getPrinterCostPerHourSnapshot() { return printerCostPerHourSnapshot; }
    public void setPrinterCostPerHourSnapshot(BigDecimal value) { this.printerCostPerHourSnapshot = value; }
    public String getMaterialNameSnapshot() { return materialNameSnapshot; }
    public String getPrinterNameSnapshot() { return printerNameSnapshot; }
    public String getPrinterModelSnapshot() { return printerModelSnapshot; }
    public BigDecimal getMaterialCostUnit() { return materialCostUnit; }
    public void setMaterialCostUnit(BigDecimal value) { this.materialCostUnit = value; }
    public BigDecimal getMachineCostUnit() { return machineCostUnit; }
    public void setMachineCostUnit(BigDecimal value) { this.machineCostUnit = value; }
    public BigDecimal getFailureRiskCostUnit() { return failureRiskCostUnit; }
    public void setFailureRiskCostUnit(BigDecimal value) { this.failureRiskCostUnit = value; }
    public BigDecimal getAdditionalChargesUnit() { return additionalChargesUnit; }
    public void setAdditionalChargesUnit(BigDecimal value) { this.additionalChargesUnit = value; }
    public BigDecimal getInternalCostUnit() { return internalCostUnit; }
    public void setInternalCostUnit(BigDecimal value) { this.internalCostUnit = value; }
    public BigDecimal getSuggestedPriceUnit() { return suggestedPriceUnit; }
    public void setSuggestedPriceUnit(BigDecimal value) { this.suggestedPriceUnit = value; }
    public BigDecimal getManualUnitPrice() { return manualUnitPrice; }
    public void setManualUnitPrice(BigDecimal manualUnitPrice) { this.manualUnitPrice = manualUnitPrice; }
    public BigDecimal getFinalUnitPrice() { return manualUnitPrice != null ? manualUnitPrice : suggestedPriceUnit; }
    public List<AdditionalCharge> getAdditionalCharges() { return Collections.unmodifiableList(additionalCharges); }
}
