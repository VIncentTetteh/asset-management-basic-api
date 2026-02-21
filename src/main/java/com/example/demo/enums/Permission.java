package com.example.demo.enums;

public enum Permission {
    // Asset permissions
    VIEW_ASSETS,
    CREATE_ASSET,
    EDIT_ASSET,
    DELETE_ASSET,
    DISPOSE_ASSET,
    TRANSFER_ASSET,

    // Approval permissions
    APPROVE_REQUESTS,
    REJECT_REQUESTS,
    ESCALATE_REQUESTS,

    // Budget permissions
    MANAGE_BUDGETS,
    VIEW_BUDGETS,
    APPROVE_BUDGET,

    // User permissions
    MANAGE_USERS,
    VIEW_USERS,
    EDIT_USER,
    DELETE_USER,

    // Department permissions
    MANAGE_DEPARTMENTS,
    VIEW_DEPARTMENTS,

    // Maintenance permissions
    SCHEDULE_MAINTENANCE,
    VIEW_MAINTENANCE,
    MARK_MAINTENANCE_COMPLETE,

    // Audit permissions
    CONDUCT_AUDIT,
    VIEW_AUDIT_LOGS,
    EXPORT_AUDIT_LOGS,

    // Report permissions
    VIEW_REPORTS,
    GENERATE_REPORTS,
    EXPORT_REPORTS,

    // Settings permissions
    MANAGE_ORGANIZATION_SETTINGS,
    MANAGE_SECURITY_SETTINGS,

    // Admin permissions
    SYSTEM_ADMIN
}

