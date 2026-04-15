package com.assetiq.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * A single entry in an asset's chronological history timeline.
 * Aggregates events from AuditEvent, AssetTransfer, MaintenanceRecord and DisposalRecord.
 */
public class AssetHistoryEventDto {

    public enum EventType {
        API_ACTION, TRANSFER, MAINTENANCE, DISPOSAL
    }

    private UUID id;
    private EventType eventType;
    private LocalDateTime occurredAt;

    /** Human-readable summary of what happened. */
    private String summary;

    /** Actor (email or display name) who triggered the event. */
    private String actor;

    // Transfer-specific fields
    private String fromDepartment;
    private String toDepartment;
    private String fromLocation;
    private String toLocation;
    private String transferStatus;

    // Maintenance-specific fields
    private String maintenanceType;
    private String maintenanceStatus;
    private LocalDate scheduledDate;
    private LocalDate performedDate;

    // Disposal-specific fields
    private String disposalMethod;
    private LocalDate disposalDate;

    // API action-specific fields
    private String httpMethod;
    private String path;
    private Integer responseStatus;

    public AssetHistoryEventDto() {}

    // ---- static factory helpers ----

    public static AssetHistoryEventDto ofAudit(UUID id, LocalDateTime at, String actor,
                                               String method, String path, Integer status) {
        AssetHistoryEventDto e = new AssetHistoryEventDto();
        e.id = id;
        e.eventType = EventType.API_ACTION;
        e.occurredAt = at;
        e.actor = actor;
        e.httpMethod = method;
        e.path = path;
        e.responseStatus = status;
        e.summary = method + " " + path + " → " + status;
        return e;
    }

    public static AssetHistoryEventDto ofTransfer(UUID id, LocalDateTime at, String actor,
                                                  String fromDept, String toDept,
                                                  String fromLoc, String toLoc, String status) {
        AssetHistoryEventDto e = new AssetHistoryEventDto();
        e.id = id;
        e.eventType = EventType.TRANSFER;
        e.occurredAt = at;
        e.actor = actor;
        e.fromDepartment = fromDept;
        e.toDepartment = toDept;
        e.fromLocation = fromLoc;
        e.toLocation = toLoc;
        e.transferStatus = status;
        e.summary = "Transferred from " + fromDept + " → " + toDept + " (" + status + ")";
        return e;
    }

    public static AssetHistoryEventDto ofMaintenance(UUID id, LocalDateTime at, String type,
                                                     String status, LocalDate scheduled, LocalDate performed) {
        AssetHistoryEventDto e = new AssetHistoryEventDto();
        e.id = id;
        e.eventType = EventType.MAINTENANCE;
        e.occurredAt = at;
        e.maintenanceType = type;
        e.maintenanceStatus = status;
        e.scheduledDate = scheduled;
        e.performedDate = performed;
        e.summary = type + " maintenance — " + status;
        return e;
    }

    public static AssetHistoryEventDto ofDisposal(UUID id, LocalDateTime at, String actor,
                                                  String method, LocalDate disposalDate) {
        AssetHistoryEventDto e = new AssetHistoryEventDto();
        e.id = id;
        e.eventType = EventType.DISPOSAL;
        e.occurredAt = at;
        e.actor = actor;
        e.disposalMethod = method;
        e.disposalDate = disposalDate;
        e.summary = "Asset disposed via " + method + " on " + disposalDate;
        return e;
    }

    // ---- getters ----

    public UUID getId() { return id; }
    public EventType getEventType() { return eventType; }
    public LocalDateTime getOccurredAt() { return occurredAt; }
    public String getSummary() { return summary; }
    public String getActor() { return actor; }
    public String getFromDepartment() { return fromDepartment; }
    public String getToDepartment() { return toDepartment; }
    public String getFromLocation() { return fromLocation; }
    public String getToLocation() { return toLocation; }
    public String getTransferStatus() { return transferStatus; }
    public String getMaintenanceType() { return maintenanceType; }
    public String getMaintenanceStatus() { return maintenanceStatus; }
    public LocalDate getScheduledDate() { return scheduledDate; }
    public LocalDate getPerformedDate() { return performedDate; }
    public String getDisposalMethod() { return disposalMethod; }
    public LocalDate getDisposalDate() { return disposalDate; }
    public String getHttpMethod() { return httpMethod; }
    public String getPath() { return path; }
    public Integer getResponseStatus() { return responseStatus; }
}
