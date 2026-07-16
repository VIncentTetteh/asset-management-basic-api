package com.assetiq.controllers.v1;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class SwaggerUiSecurityTest {

    @Autowired
    MockMvc mockMvc;

    @Test
    void anonymousCannotAccessSwaggerUiOrApiDocs() throws Exception {
        mockMvc.perform(get("/swagger-ui.html").accept(MediaType.TEXT_HTML))
                .andExpect(status().is4xxClientError());
        mockMvc.perform(get("/swagger-ui/index.html").accept(MediaType.TEXT_HTML))
                .andExpect(status().is4xxClientError());
        mockMvc.perform(get("/v3/api-docs").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().is4xxClientError());
    }
}

