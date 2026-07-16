package com.assetiq;

import com.assetiq.config.NoOpRateLimiter;
import com.assetiq.config.RateLimiter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class AssetIQApplicationTests {

	@Autowired
	RateLimiter rateLimiter;

	@Test
	void contextLoads() {
		assertThat(rateLimiter).isInstanceOf(NoOpRateLimiter.class);
	}

}
