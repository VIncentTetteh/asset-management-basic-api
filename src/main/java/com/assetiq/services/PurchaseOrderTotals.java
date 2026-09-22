package com.assetiq.services;

import com.assetiq.dto.PoLineItemDto;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * The arithmetic behind an itemised purchase order.
 *
 * <p>Kept out of the service so the rounding can be tested on its own: a line total
 * that disagrees with its own quantity and price by a hundredth is a supplier
 * dispute, and it is exactly the kind of thing that only shows up at the fourth
 * decimal place.
 *
 * <p>Money is carried at the column's scale (4dp for a line, 2dp for the order
 * total) and rounded HALF_UP, the convention used everywhere else in the ledger.
 */
public final class PurchaseOrderTotals {

    /** po_line_item.unit_price / line_total / tax_amount are NUMERIC(19,4). */
    public static final int LINE_SCALE = 4;
    /** purchase_order.total_amount is NUMERIC(15,2). */
    public static final int ORDER_SCALE = 2;

    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private PurchaseOrderTotals() {
    }

    /** quantity x unit price, before tax. */
    public static BigDecimal netOf(BigDecimal quantity, BigDecimal unitPrice) {
        return nullToZero(quantity).multiply(nullToZero(unitPrice)).setScale(LINE_SCALE, RoundingMode.HALF_UP);
    }

    /**
     * The tax a line carries. A supplied {@code taxRate} wins: the rate is what the
     * buyer agreed, and a client-sent amount that disagrees with it would make the
     * stored order self-contradictory. With no rate, an explicit amount is taken as
     * given; with neither, the line is untaxed.
     */
    public static BigDecimal taxOf(BigDecimal net, BigDecimal taxRate, BigDecimal taxAmount) {
        if (taxRate != null) {
            return net.multiply(taxRate).divide(HUNDRED, LINE_SCALE, RoundingMode.HALF_UP);
        }
        if (taxAmount != null) {
            return taxAmount.setScale(LINE_SCALE, RoundingMode.HALF_UP);
        }
        return BigDecimal.ZERO.setScale(LINE_SCALE);
    }

    /** net + tax, at the line's scale. */
    public static BigDecimal lineTotalOf(BigDecimal quantity, BigDecimal unitPrice,
                                         BigDecimal taxRate, BigDecimal taxAmount) {
        BigDecimal net = netOf(quantity, unitPrice);
        return net.add(taxOf(net, taxRate, taxAmount)).setScale(LINE_SCALE, RoundingMode.HALF_UP);
    }

    /**
     * The order total the lines add up to, at the order column's scale. Summing at
     * line scale and rounding once at the end keeps the total equal to the sum the
     * supplier sees on the printed order.
     */
    public static BigDecimal orderTotalOf(List<PoLineItemDto> lines) {
        BigDecimal sum = BigDecimal.ZERO;
        for (PoLineItemDto line : lines) {
            sum = sum.add(lineTotalOf(line.getQuantity(), line.getUnitPrice(),
                    line.getTaxRate(), line.getTaxAmount()));
        }
        return sum.setScale(ORDER_SCALE, RoundingMode.HALF_UP);
    }

    private static BigDecimal nullToZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
