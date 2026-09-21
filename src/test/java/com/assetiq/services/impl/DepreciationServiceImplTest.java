package com.assetiq.services.impl;

import com.assetiq.enums.AssetStatus;
import com.assetiq.enums.DepreciationMethod;
import com.assetiq.models.Asset;
import com.assetiq.repositories.AssetRepository;
import com.assetiq.repositories.OrganisationRepository;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("DepreciationServiceImpl monthly batch")
class DepreciationServiceImplTest {

    @Mock AssetRepository assetRepository;
    @Mock OrganisationRepository organisationRepository;

    @Test
    @DisplayName("refreshes every non-disposed asset, whatever its status, across pages")
    void refreshesAllNonDisposedAssets() {
        Asset inStock = asset(AssetStatus.IN_STOCK);
        Asset maintenance = asset(AssetStatus.MAINTENANCE);
        Asset retired = asset(AssetStatus.RETIRED);
        when(assetRepository.findUndisposedForDepreciation(any(Pageable.class))).thenAnswer(inv -> {
            Pageable p = inv.getArgument(0);
            return p.getPageNumber() == 0
                    ? new PageImpl<>(List.of(inStock, maintenance), PageRequest.of(0, 2), 3)
                    : new PageImpl<>(List.of(retired), PageRequest.of(1, 2), 3);
        });

        new DepreciationServiceImpl(assetRepository, organisationRepository).runMonthlyDepreciationBatch();

        for (Asset a : List.of(inStock, maintenance, retired)) {
            assertThat(a.getCurrentBookValue()).isEqualByComparingTo("900.00");
        }
    }

    @Test
    @DisplayName("keeps its ShedLock so only one instance runs it")
    void isLocked() throws NoSuchMethodException {
        SchedulerLock lock = DepreciationServiceImpl.class.getMethod("runMonthlyDepreciationBatch")
                .getAnnotation(SchedulerLock.class);
        assertThat(lock).isNotNull();
        assertThat(lock.name()).isEqualTo("monthlyDepreciation");
    }

    private static Asset asset(AssetStatus status) {
        Asset a = new Asset();
        a.setStatus(status);
        a.setPurchaseCost(new BigDecimal("1200"));
        a.setPurchaseDate(LocalDate.now().minusMonths(3));
        a.setUsefulLifeMonths(12);
        a.setDepreciationMethod(DepreciationMethod.STRAIGHT_LINE);
        a.setCurrentBookValue(new BigDecimal("1200"));
        return a;
    }
}
