package com.example.demo.controllers.v1;

import com.example.demo.dto.DepreciationPolicyDto;
import com.example.demo.services.DepreciationPolicyService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/depreciation-policies")
public class DepreciationPolicyController {

    private final DepreciationPolicyService policyService;

    public DepreciationPolicyController(DepreciationPolicyService policyService) {
        this.policyService = policyService;
    }

    @PostMapping
    public ResponseEntity<DepreciationPolicyDto> createPolicy(@Valid @RequestBody DepreciationPolicyDto policyDto,
                                                             @RequestParam UUID organisationId) {
        DepreciationPolicyDto createdPolicy = policyService.createPolicy(policyDto, organisationId);
        return ResponseEntity.status(HttpStatus.CREATED).body(createdPolicy);
    }

    @GetMapping("/{id}")
    public ResponseEntity<DepreciationPolicyDto> getPolicyById(@PathVariable UUID id) {
        DepreciationPolicyDto policy = policyService.getPolicyById(id);
        return ResponseEntity.ok(policy);
    }

    @GetMapping
    public ResponseEntity<Set<DepreciationPolicyDto>> getPoliciesByOrganisation(@RequestParam UUID organisationId) {
        Set<DepreciationPolicyDto> policies = policyService.getPoliciesByOrganisation(organisationId);
        return ResponseEntity.ok(policies);
    }

    @PutMapping("/{id}")
    public ResponseEntity<DepreciationPolicyDto> updatePolicy(@PathVariable UUID id,
                                                             @Valid @RequestBody DepreciationPolicyDto policyDto) {
        DepreciationPolicyDto updatedPolicy = policyService.updatePolicy(id, policyDto);
        return ResponseEntity.ok(updatedPolicy);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletePolicy(@PathVariable UUID id) {
        policyService.deletePolicy(id);
        return ResponseEntity.noContent().build();
    }
}

