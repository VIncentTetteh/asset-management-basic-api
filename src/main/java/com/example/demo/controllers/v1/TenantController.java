package com.example.demo.controllers.v1;

import com.example.demo.dto.TenantRegisterRequest;
import com.example.demo.dto.TenantRegisterResponse;
import com.example.demo.services.TenantRegistrationService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/tenant")
public class TenantController {
    private static final Logger log = LoggerFactory.getLogger(TenantController.class);

    private final TenantRegistrationService tenantRegistrationService;

    public TenantController(TenantRegistrationService tenantRegistrationService) {
        log.info("[TENANT_CTRL] TenantController initialized");
        this.tenantRegistrationService = tenantRegistrationService;
    }

    @PostMapping("/register")
    public ResponseEntity<TenantRegisterResponse> register(@Valid @RequestBody TenantRegisterRequest request) {
        log.info("[TENANT_CTRL] Received registration request for: {}", request.getOrganisationName());
        TenantRegisterResponse response = tenantRegistrationService.registerTenant(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
