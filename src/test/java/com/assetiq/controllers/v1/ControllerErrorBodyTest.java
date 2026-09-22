package com.assetiq.controllers.v1;

import com.assetiq.exceptions.ResourceNotFoundException;
import com.assetiq.services.DepartmentService;
import com.assetiq.services.LeaseRecordService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Controllers used to answer some failures with an empty body, so the web app
 * could only show a generic message. They now throw, and GlobalExceptionHandler
 * supplies the reason, with the same status codes as before.
 */
@ExtendWith(MockitoExtension.class)
class ControllerErrorBodyTest {

    @Mock
    private LeaseRecordService leaseRecordService;

    @Mock
    private DepartmentService departmentService;

    private MockMvc mvc(Object controller) {
        return MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void leaseTerminate_alreadyTerminated_is400WithReason() throws Exception {
        UUID id = UUID.randomUUID();
        when(leaseRecordService.terminate(eq(id), isNull()))
                .thenThrow(new IllegalStateException("Lease is already terminated."));

        mvc(new LeaseRecordController(leaseRecordService))
                .perform(post("/api/v1/leases/{id}/terminate", id))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Lease is already terminated."));
    }

    @Test
    void leaseUpdate_unknownLease_is404WithReason() throws Exception {
        UUID id = UUID.randomUUID();
        when(leaseRecordService.update(eq(id), any()))
                .thenThrow(new ResourceNotFoundException("Lease record not found: " + id));

        mvc(new LeaseRecordController(leaseRecordService))
                .perform(put("/api/v1/leases/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Lease record not found: " + id));
    }

    @Test
    void leaseUpdate_badInput_is400NotA404() throws Exception {
        UUID id = UUID.randomUUID();
        when(leaseRecordService.update(eq(id), any()))
                .thenThrow(new IllegalArgumentException("Lease end date must not be before its start date"));

        mvc(new LeaseRecordController(leaseRecordService))
                .perform(put("/api/v1/leases/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Lease end date must not be before its start date"));
    }

    @Test
    void departmentPatch_duplicateName_is409WithReason() throws Exception {
        UUID id = UUID.randomUUID();
        when(departmentService.patch(eq(id), any()))
                .thenThrow(new IllegalStateException("Department with the same name already exists in this organisation"));

        mvc(new DepartmentController(departmentService))
                .perform(patch("/api/v1/departments/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Ops\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message")
                        .value("Department with the same name already exists in this organisation"));
    }
}
