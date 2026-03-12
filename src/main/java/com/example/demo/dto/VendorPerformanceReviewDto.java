package com.example.demo.dto;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public class VendorPerformanceReviewDto {

    private UUID id;

    private UUID supplierId;
    private String supplierName;

    @NotNull
    @DecimalMin("1.0") @DecimalMax("5.0")
    private BigDecimal rating;

    @Min(1) @Max(5)
    private Integer deliveryScore;

    @Min(1) @Max(5)
    private Integer qualityScore;

    @Min(1) @Max(5)
    private Integer supportScore;

    private String feedback;

    private LocalDate periodStart;
    private LocalDate periodEnd;

    private UUID reviewedById;
    private String reviewedByEmail;

    // ---- getters / setters ----

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getSupplierId() { return supplierId; }
    public void setSupplierId(UUID supplierId) { this.supplierId = supplierId; }

    public String getSupplierName() { return supplierName; }
    public void setSupplierName(String supplierName) { this.supplierName = supplierName; }

    public BigDecimal getRating() { return rating; }
    public void setRating(BigDecimal rating) { this.rating = rating; }

    public Integer getDeliveryScore() { return deliveryScore; }
    public void setDeliveryScore(Integer deliveryScore) { this.deliveryScore = deliveryScore; }

    public Integer getQualityScore() { return qualityScore; }
    public void setQualityScore(Integer qualityScore) { this.qualityScore = qualityScore; }

    public Integer getSupportScore() { return supportScore; }
    public void setSupportScore(Integer supportScore) { this.supportScore = supportScore; }

    public String getFeedback() { return feedback; }
    public void setFeedback(String feedback) { this.feedback = feedback; }

    public LocalDate getPeriodStart() { return periodStart; }
    public void setPeriodStart(LocalDate periodStart) { this.periodStart = periodStart; }

    public LocalDate getPeriodEnd() { return periodEnd; }
    public void setPeriodEnd(LocalDate periodEnd) { this.periodEnd = periodEnd; }

    public UUID getReviewedById() { return reviewedById; }
    public void setReviewedById(UUID reviewedById) { this.reviewedById = reviewedById; }

    public String getReviewedByEmail() { return reviewedByEmail; }
    public void setReviewedByEmail(String reviewedByEmail) { this.reviewedByEmail = reviewedByEmail; }
}
