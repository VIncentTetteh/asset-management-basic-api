package com.assetiq.controllers.v1;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.hasKey;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class HealthMonitoringSecurityTest {

    @Autowired
    MockMvc mockMvc;

    @Test
    void anonymousCanCallBasicHealthOnly() throws Exception {
        mockMvc.perform(get("/api/v1/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.components.database", not(hasKey("url"))));

        mockMvc.perform(get("/api/v1/health/detailed"))
                .andExpect(status().is4xxClientError());

        mockMvc.perform(get("/api/v1/metrics"))
                .andExpect(status().is4xxClientError());
    }
}
