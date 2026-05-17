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
    private long assigned;
    private long unassigned;

    public long getTotal()       { return total; }
    public long getInUse()       { return inUse; }
    public long getInStock()     { return inStock; }
    public long getMaintenance() { return maintenance; }
    public long getRetired()     { return retired; }
    public long getDisposed()    { return disposed; }
    public long getReserved()    { return reserved; }
    public long getMissing()     { return missing; }
    public long getAssigned()    { return assigned; }
    public long getUnassigned()  { return unassigned; }

    public void setTotal(long total)             { this.total = total; }
    public void setInUse(long inUse)             { this.inUse = inUse; }
    public void setInStock(long inStock)         { this.inStock = inStock; }
    public void setMaintenance(long maintenance) { this.maintenance = maintenance; }
    public void setRetired(long retired)         { this.retired = retired; }
    public void setDisposed(long disposed)       { this.disposed = disposed; }
    public void setReserved(long reserved)       { this.reserved = reserved; }
    public void setMissing(long missing)         { this.missing = missing; }
    public void setAssigned(long assigned)       { this.assigned = assigned; }
    public void setUnassigned(long unassigned)   { this.unassigned = unassigned; }
}
