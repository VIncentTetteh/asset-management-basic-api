package com.assetiq.controllers.v1;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class HealthMonitoringSecurityTest {

    @Autowired
    MockMvc mockMvc;

    @Test
    void anonymousCannotCallTenantHealthOrMetrics() throws Exception {
        mockMvc.perform(get("/api/v1/health"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/v1/health/detailed"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/v1/metrics"))
                .andExpect(status().isUnauthorized());
    }
}
