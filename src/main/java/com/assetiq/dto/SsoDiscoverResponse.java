package com.assetiq.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SsoDiscoverResponse {
    private boolean ssoEnabled;
    private String organisationId;
    private String provider;
}
