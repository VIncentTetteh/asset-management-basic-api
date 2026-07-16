package com.assetiq.repositories;

import com.assetiq.models.FeatureFlag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false",
        "spring.datasource.url=jdbc:h2:mem:featureflags;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE"
})
class FeatureFlagRepositorySchemaTest {

    @Autowired
    FeatureFlagRepository repository;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Test
    void featureFlagUsesFlagKeyColumnAndFindByKeyStillWorks() {
        FeatureFlag flag = new FeatureFlag();
        flag.setKey("billing.ghs-default-currency");
        flag.setDescription("Route new organisations to GHS pricing by default.");
        flag.setEnabledGlobally(false);
        flag.setRolloutPercentage((short) 0);

        repository.saveAndFlush(flag);

        assertThat(repository.findByKey("billing.ghs-default-currency")).isPresent();
        assertThat(jdbcTemplate.queryForObject(
                "select flag_key from feature_flag where flag_key = ?",
                String.class,
                "billing.ghs-default-currency")).isEqualTo("billing.ghs-default-currency");
    }
}
