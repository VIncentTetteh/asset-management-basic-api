package com.example.demo.dto;

import lombok.Data;

@Data
public class BillingCheckoutResponse {
    private String authorizationUrl;
    private String accessCode;
    private String reference;
}

