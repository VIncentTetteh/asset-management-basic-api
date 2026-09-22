package com.assetiq.services.impl;

import com.assetiq.enums.AssetCondition;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;

class PredictiveMaintenanceConditionTest {

    @ParameterizedTest
    @EnumSource(AssetCondition.class)
    void poorDamagedAndScrapAreBadConditions(AssetCondition condition) {
        boolean expected = condition == AssetCondition.POOR || condition == AssetCondition.DAMAGED
                || condition == AssetCondition.SCRAP;
        assertThat(PredictiveMaintenanceServiceImpl.isBadCondition(condition)).isEqualTo(expected);
    }
}
