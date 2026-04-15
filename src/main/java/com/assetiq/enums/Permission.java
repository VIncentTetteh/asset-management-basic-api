package com.assetiq.enums;

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
    SYSTEM_ADMIN,

    // Checkout / asset custody
    CHECKOUT_ASSET,

    // Finance
    MANAGE_EXPENSES,
    VIEW_TCO,
    MANAGE_EXCHANGE_RATES,

    // Lease & rental
    MANAGE_LEASES,

    // QR code management
    REGENERATE_QR,

    // IAM
    REVIEW_ACCESS,

    // Role management
    VIEW_ROLES,
    MANAGE_ROLES,

    // Location management
    VIEW_LOCATIONS,
    MANAGE_LOCATIONS,

    // Category management
    VIEW_CATEGORIES,
    MANAGE_CATEGORIES,

    // Supplier / vendor management
    VIEW_SUPPLIERS,
    MANAGE_SUPPLIERS,

    // Procurement (purchase orders)
    VIEW_PROCUREMENT,
    MANAGE_PROCUREMENT,
    APPROVE_PROCUREMENT,

    // Software & license management
    VIEW_SOFTWARE_LICENSES,
    MANAGE_SOFTWARE_LICENSES,

    // Contract management
    VIEW_CONTRACTS,
    MANAGE_CONTRACTS,

    // Compliance
    VIEW_COMPLIANCE,
    MANAGE_COMPLIANCE,

    // Network / infrastructure discovery
    VIEW_NETWORK_DISCOVERY,
    MANAGE_NETWORK_DISCOVERY,

    // Cloud assets
    VIEW_CLOUD_ASSETS,
    MANAGE_CLOUD_ASSETS,

    // Depreciation policies
    VIEW_DEPRECIATION,
    MANAGE_DEPRECIATION,

    // Vendor performance reviews
    VIEW_VENDOR_REVIEWS,
    MANAGE_VENDOR_REVIEWS
}

