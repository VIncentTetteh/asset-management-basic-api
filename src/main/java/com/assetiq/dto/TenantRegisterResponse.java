package com.assetiq.dto;



import lombok.Data;

import java.util.UUID;

@Data
public class TenantRegisterResponse {
    private UUID organisationId;
    private String organisationName;

    private UUID userId;
    private String email;
    private String firstName;
    private String lastName;
    private String role;

    private String token; // JWT for immediate login
    private long expiresIn; // seconds
}
