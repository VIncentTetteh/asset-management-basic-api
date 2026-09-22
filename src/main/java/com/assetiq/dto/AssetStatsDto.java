package com.assetiq.dto;

public class AssetStatsDto {
    private long total;
    private long inUse;
    private long inStock;
    private long maintenance;
    private long retired;
    private long disposed;
    private long reserved;
    private long missing;
    private long pendingProcurement;
    private long underRepair;
    private long assigned;
    private long unassigned;
    /** Register value: purchase cost of every non-disposed asset, in {@link #currency}. */
    private java.math.BigDecimal totalValue;
    private String currency;
    private boolean complete = true;
    private java.util.List<String> missingRates = java.util.List.of();

    public long getTotal()       { return total; }
    public long getInUse()       { return inUse; }
    public long getInStock()     { return inStock; }
    public long getMaintenance() { return maintenance; }
    public long getRetired()     { return retired; }
    public long getDisposed()    { return disposed; }
    public long getReserved()    { return reserved; }
    public long getMissing()     { return missing; }
    public long getPendingProcurement() { return pendingProcurement; }
    public long getUnderRepair() { return underRepair; }
    public long getAssigned()    { return assigned; }
    public long getUnassigned()  { return unassigned; }
    public java.math.BigDecimal getTotalValue() { return totalValue; }
    public String getCurrency()  { return currency; }
    public boolean isComplete()  { return complete; }
    public java.util.List<String> getMissingRates() { return missingRates; }

    public void setTotal(long total)             { this.total = total; }
    public void setInUse(long inUse)             { this.inUse = inUse; }
    public void setInStock(long inStock)         { this.inStock = inStock; }
    public void setMaintenance(long maintenance) { this.maintenance = maintenance; }
    public void setRetired(long retired)         { this.retired = retired; }
    public void setDisposed(long disposed)       { this.disposed = disposed; }
    public void setReserved(long reserved)       { this.reserved = reserved; }
    public void setMissing(long missing)         { this.missing = missing; }
    public void setPendingProcurement(long pendingProcurement) { this.pendingProcurement = pendingProcurement; }
    public void setUnderRepair(long underRepair) { this.underRepair = underRepair; }
    public void setAssigned(long assigned)       { this.assigned = assigned; }
    public void setUnassigned(long unassigned)   { this.unassigned = unassigned; }
    public void setTotalValue(java.math.BigDecimal totalValue) { this.totalValue = totalValue; }
    public void setCurrency(String currency)     { this.currency = currency; }
    public void setComplete(boolean complete)    { this.complete = complete; }
    public void setMissingRates(java.util.List<String> missingRates) { this.missingRates = missingRates; }
}
