package com.example.demo.services.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

@Service
public class PaystackGatewayService {

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    @Value("${paystack.secret.key:}")
    private String paystackSecretKey;

    @Value("${paystack.base-url:https://api.paystack.co}")
    private String paystackBaseUrl;

    @Value("${paystack.timeout-seconds:30}")
    private int timeoutSeconds;

    public PaystackGatewayService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    public JsonNode initializeTransaction(Map<String, Object> payload) {
        return post("/transaction/initialize", payload);
    }

    public JsonNode verifyTransaction(String reference) {
        return get("/transaction/verify/" + reference);
    }

    public JsonNode disableSubscription(String subscriptionCode, String emailToken) {
        return post("/subscription/disable", Map.of(
                "code", subscriptionCode,
                "token", emailToken));
    }

    public JsonNode enableSubscription(String subscriptionCode, String emailToken) {
        return post("/subscription/enable", Map.of(
                "code", subscriptionCode,
                "token", emailToken));
    }

    private JsonNode post(String path, Map<String, Object> payload) {
        ensureConfigured();
        try {
            String body = objectMapper.writeValueAsString(payload);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(paystackBaseUrl + path))
                    .header("Authorization", "Bearer " + paystackSecretKey)
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(timeoutSeconds))
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return parseJson(response.body());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Unable to call Paystack: " + e.getMessage(), e);
        } catch (IOException e) {
            throw new IllegalStateException("Unable to call Paystack: " + e.getMessage(), e);
        }
    }

    private JsonNode get(String path) {
        ensureConfigured();
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(paystackBaseUrl + path))
                    .header("Authorization", "Bearer " + paystackSecretKey)
                    .timeout(Duration.ofSeconds(timeoutSeconds))
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return parseJson(response.body());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Unable to call Paystack: " + e.getMessage(), e);
        } catch (IOException e) {
            throw new IllegalStateException("Unable to call Paystack: " + e.getMessage(), e);
        }
    }

    private JsonNode parseJson(String body) {
        try {
            return objectMapper.readTree(body);
        } catch (IOException e) {
            throw new IllegalStateException("Invalid Paystack response", e);
        }
    }

    private void ensureConfigured() {
        if (paystackSecretKey == null || paystackSecretKey.isBlank()) {
            throw new IllegalStateException("Paystack secret key is not configured");
        }
    }
}
