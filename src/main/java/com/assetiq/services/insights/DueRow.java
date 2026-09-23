package com.assetiq.services.insights;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * One dated commitment the tenant is carrying: a warranty, an insurance policy,
 * a maintenance visit, a contract, a licence or a lease.
 *
 * <p>Every expiry stream projects into this shape so the radar can bucket them
 * the same way and the UI can render one list component. The fields are
 * deliberately generic; what {@code amount} means differs per stream, and each
 * stream states that meaning in its {@code valueMeaning} field rather than
 * leaving the reader to guess.
 *
 * @param id             the record's own id, for the drill-through link
 * @param name           what to show as the headline
 * @param reference      a secondary identifier (asset tag, contract number, vendor)
 * @param dueDate        the date this expires or falls due; never null in a stream
 * @param amount         the primary money figure, in {@code currency}
 * @param secondaryAmount a fallback figure used when {@code amount} is absent
 * @param relatedId      the related record (asset, supplier) the UI can also link
 * @param relatedName    that record's name
 */
public record DueRow(UUID id,
                     String name,
                     String reference,
                     LocalDate dueDate,
                     BigDecimal amount,
                     BigDecimal secondaryAmount,
                     String currency,
                     UUID relatedId,
                     String relatedName) {

    /** The figure to use: the primary one, falling back to the secondary. */
    public BigDecimal effectiveAmount() {
        return amount != null ? amount : secondaryAmount;
    }
}
