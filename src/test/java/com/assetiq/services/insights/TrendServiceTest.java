package com.assetiq.services.insights;

import com.assetiq.models.AnalyticsSnapshot;
import com.assetiq.models.Organisation;
import com.assetiq.repositories.AnalyticsSnapshotRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("TrendService - honest about how little history there is")
class TrendServiceTest {

    @Mock AnalyticsSnapshotRepository snapshotRepository;

    private TrendService service;
    private Organisation org;
    private final Set<InsightSection> everything = EnumSet.allOf(InsightSection.class);

    @BeforeEach
    void setUp() {
        service = new TrendService(snapshotRepository);
        org = InsightTestFixtures.org("GHS");
    }

    private AnalyticsSnapshot snapshot(LocalDate date, long assets, String cost, String currency) {
        AnalyticsSnapshot s = new AnalyticsSnapshot();
        s.setOrganisation(org);
        s.setSnapshotDate(date);
        s.setCurrency(currency);
        s.setComplete(true);
        s.setAssetCount(assets);
        s.setTotalCost(new BigDecimal(cost));
        s.setNetBookValue(new BigDecimal(cost));
        return s;
    }

    @Test
    @DisplayName("a brand new tenant is told there is no history, not shown a flat line at zero")
    void noHistoryIsSaidOutLoud() {
        when(snapshotRepository.findSince(eq(org), any())).thenReturn(List.of());
        when(snapshotRepository.findEarliestSnapshotDate(org)).thenReturn(Optional.empty());

        Map<String, Object> r = service.trends(org, 90, everything);

        assertThat((List<?>) r.get("points")).isEmpty();
        assertThat(r.get("pointCount")).isEqualTo(0);
        assertThat(r.get("historyDays")).isEqualTo(0L);
        assertThat(r.get("firstSnapshot")).isNull();
        assertThat(r.get("sufficientHistory")).isEqualTo(false);
        assertThat(r.get("note")).asString().contains("No history");
        assertThat(asMap(r.get("change")).get("comparable")).isEqualTo(false);
    }

    @Test
    @DisplayName("three days of history is three points and a warning, not a trend")
    void tooLittleHistoryIsLabelled() {
        LocalDate today = LocalDate.now();
        when(snapshotRepository.findSince(eq(org), any())).thenReturn(List.of(
                snapshot(today.minusDays(2), 10, "1000", "GHS"),
                snapshot(today.minusDays(1), 11, "1100", "GHS"),
                snapshot(today, 12, "1200", "GHS")));
        when(snapshotRepository.findEarliestSnapshotDate(org))
                .thenReturn(Optional.of(today.minusDays(2)));

        Map<String, Object> r = service.trends(org, 90, everything);

        assertThat(r.get("pointCount")).isEqualTo(3);
        assertThat(r.get("sufficientHistory")).isEqualTo(false);
        assertThat(r.get("note")).asString().contains("3 day(s) of history");
        assertThat(r.get("historyDays")).isEqualTo(3L);
        // Nothing is filled in: exactly the days that were recorded appear.
        assertThat((List<?>) r.get("points")).hasSize(3);
    }

    @Test
    @DisplayName("with enough points the change is first against last, in one currency")
    void changeIsFirstAgainstLast() {
        LocalDate today = LocalDate.now();
        List<AnalyticsSnapshot> points = new ArrayList<>();
        for (int i = 9; i >= 0; i--) {
            points.add(snapshot(today.minusDays(i), 100 + (9 - i), String.valueOf(1000 + (9 - i) * 10), "GHS"));
        }
        when(snapshotRepository.findSince(eq(org), any())).thenReturn(points);
        when(snapshotRepository.findEarliestSnapshotDate(org)).thenReturn(Optional.of(today.minusDays(9)));

        Map<String, Object> r = service.trends(org, 90, everything);

        assertThat(r.get("sufficientHistory")).isEqualTo(true);
        assertThat(r.get("note")).isNull();
        assertThat(r.get("currency")).isEqualTo("GHS");
        Map<String, Object> change = asMap(r.get("change"));
        assertThat(change.get("comparable")).isEqualTo(true);
        assertThat(change.get("assetCount")).isEqualTo(9L);
        assertThat(((BigDecimal) change.get("totalCost")).toPlainString()).isEqualTo("90");
    }

    @Test
    @DisplayName("a base-currency change breaks the money comparison rather than subtracting units")
    void currencyChangeSuppressesMoneyDeltas() {
        LocalDate today = LocalDate.now();
        when(snapshotRepository.findSince(eq(org), any())).thenReturn(List.of(
                snapshot(today.minusDays(5), 10, "1000", "GHS"),
                snapshot(today, 10, "80", "USD")));
        when(snapshotRepository.findEarliestSnapshotDate(org)).thenReturn(Optional.of(today.minusDays(5)));

        Map<String, Object> r = service.trends(org, 90, everything);

        assertThat(r.get("currencyChanged")).isEqualTo(true);
        assertThat(r.get("currency")).isNull();
        assertThat(r.get("currencies")).isEqualTo(List.of("GHS", "USD"));
        Map<String, Object> change = asMap(r.get("change"));
        assertThat(change.get("moneyComparable")).isEqualTo(false);
        assertThat(change).doesNotContainKey("totalCost");
        // Counts are still comparable: an asset is an asset.
        assertThat(change.get("assetCount")).isEqualTo(0L);
    }

    @Test
    @DisplayName("a caller who may not see money gets counts only")
    void moneyIsWithheld() {
        LocalDate today = LocalDate.now();
        when(snapshotRepository.findSince(eq(org), any())).thenReturn(List.of(
                snapshot(today, 10, "1000", "GHS")));
        when(snapshotRepository.findEarliestSnapshotDate(org)).thenReturn(Optional.of(today));

        Map<String, Object> r = service.trends(org, 90, EnumSet.of(InsightSection.ASSETS));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> points = (List<Map<String, Object>>) r.get("points");
        assertThat(points.get(0)).containsKey("assetCount").doesNotContainKey("totalCost");
        assertThat(r.get("withheldSections")).isEqualTo(List.of("asset valuations"));
    }

    @Test
    @DisplayName("a caller who may not read assets gets no trend at all")
    void noAssetAccessNoTrend() {
        Map<String, Object> r = service.trends(org, 90, EnumSet.noneOf(InsightSection.class));

        assertThat((List<?>) r.get("points")).isEmpty();
        assertThat(r.get("sufficientHistory")).isEqualTo(false);
    }

    @Test
    @DisplayName("an out-of-range window is rejected")
    void windowIsValidated() {
        assertThatThrownBy(() -> service.trends(org, 0, everything))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.trends(org, 5000, everything))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object value) {
        return (Map<String, Object>) value;
    }
}
