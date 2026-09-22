package com.assetiq.services;

import com.assetiq.dto.PoLineItemDto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Purchase order line arithmetic")
class PurchaseOrderTotalsTest {

    @Test
    void netIsQuantityTimesUnitPriceAtLineScale() {
        assertThat(PurchaseOrderTotals.netOf(new BigDecimal("3"), new BigDecimal("12.5")))
                .isEqualByComparingTo("37.5000");
        assertThat(PurchaseOrderTotals.netOf(new BigDecimal("3"), new BigDecimal("12.5")).scale()).isEqualTo(4);
    }

    @Test
    void nullQuantityOrPriceIsZeroRatherThanAnException() {
        assertThat(PurchaseOrderTotals.netOf(null, new BigDecimal("10"))).isEqualByComparingTo("0");
        assertThat(PurchaseOrderTotals.netOf(new BigDecimal("10"), null)).isEqualByComparingTo("0");
    }

    @Test
    void aTaxRateWinsOverAClientSuppliedTaxAmount() {
        BigDecimal net = new BigDecimal("100.0000");
        assertThat(PurchaseOrderTotals.taxOf(net, new BigDecimal("12.5"), new BigDecimal("999")))
                .isEqualByComparingTo("12.5000");
    }

    @Test
    void withoutARateAnExplicitTaxAmountIsTakenAsGiven() {
        assertThat(PurchaseOrderTotals.taxOf(new BigDecimal("100.0000"), null, new BigDecimal("7.25")))
                .isEqualByComparingTo("7.2500");
    }

    @Test
    void withNeitherRateNorAmountTheLineIsUntaxed() {
        assertThat(PurchaseOrderTotals.taxOf(new BigDecimal("100.0000"), null, null))
                .isEqualByComparingTo("0");
    }

    @Test
    void lineTotalIsNetPlusTax() {
        assertThat(PurchaseOrderTotals.lineTotalOf(new BigDecimal("2"), new BigDecimal("50"),
                new BigDecimal("10"), null)).isEqualByComparingTo("110.0000");
    }

    @Test
    void taxRoundsHalfUpAtTheFourthDecimalPlace() {
        // 1 x 0.0001 at 12.5% = 0.0000125 -> 0.0000 at line scale
        assertThat(PurchaseOrderTotals.taxOf(new BigDecimal("0.0001"), new BigDecimal("12.5"), null))
                .isEqualByComparingTo("0.0000");
        // 1 x 1.00005 rounds the net itself
        assertThat(PurchaseOrderTotals.netOf(new BigDecimal("1"), new BigDecimal("1.00005")))
                .isEqualByComparingTo("1.0001");
    }

    @Test
    void orderTotalSumsTheLinesAndRoundsOnceToTwoDecimals() {
        List<PoLineItemDto> lines = List.of(
                line("1", "0.3333", null),
                line("1", "0.3333", null),
                line("1", "0.3333", null));
        // 0.9999 -> 1.00 at order scale
        BigDecimal total = PurchaseOrderTotals.orderTotalOf(lines);
        assertThat(total).isEqualByComparingTo("1.00");
        assertThat(total.scale()).isEqualTo(2);
    }

    @Test
    void orderTotalOfNoLinesIsZero() {
        assertThat(PurchaseOrderTotals.orderTotalOf(List.of())).isEqualByComparingTo("0.00");
    }

    @Test
    void orderTotalIncludesPerLineTax() {
        assertThat(PurchaseOrderTotals.orderTotalOf(List.of(
                line("2", "100", "12.5"),
                line("1", "50", null))))
                .isEqualByComparingTo("275.00");
    }

    private static PoLineItemDto line(String quantity, String unitPrice, String taxRate) {
        PoLineItemDto dto = new PoLineItemDto();
        dto.setDescription("Item");
        dto.setQuantity(new BigDecimal(quantity));
        dto.setUnitPrice(new BigDecimal(unitPrice));
        dto.setTaxRate(taxRate == null ? null : new BigDecimal(taxRate));
        return dto;
    }
}
