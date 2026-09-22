package com.assetiq.controllers.v1;

import com.assetiq.services.EmployeeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The matrix was unsure whether List<@Valid item> is validated without @Validated on the controller. */
class EmployeeChecklistValidationTest {

    private final EmployeeService employeeService = mock(EmployeeService.class);
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mockMvc = MockMvcBuilders.standaloneSetup(new EmployeeController(employeeService, validator))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();
    }

    @Test
    void onboardItemsAreValidated() throws Exception {
        mockMvc.perform(post("/api/v1/employees/{id}/onboard", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[{\"title\":\"  \",\"itemType\":\"GENERAL\"}]"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors['items[0].title']").exists());
        verify(employeeService, never()).onboard(any(), any());
    }
}
