package com.example.demo.enums;

/**
 * Classifies an asset acquisition as capital expenditure (CAPEX) or operating
 * expenditure (OPEX) — critical for financial reporting in banks and fintechs.
 */
public enum ProcurementType {
    /** Capital expenditure — asset owned outright, depreciated over time. */
    CAPEX,
    /** Operating expenditure — leased, rented, or subscription-based asset. */
    OPEX
}
