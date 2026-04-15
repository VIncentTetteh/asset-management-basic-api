package com.assetiq.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Map;

@Component
public class JwtUtil {

    private final SecretKey key;
    private final long expirationMillis;

    public JwtUtil(@Value("${app.jwt.secret}") String secret,
                   @Value("${app.jwt.expiration:86400000}") long expirationMillis) {
        // Validate secret entropy at startup
        JwtSecretValidator.validateSecretEntropy(secret);
        
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        this.key = Keys.hmacShaKeyFor(keyBytes);
        this.expirationMillis = expirationMillis;
    }

    public String generateToken(String subject, Map<String, Object> claims, long expirationMillis) {
        Date now = new Date();
        String jti = java.util.UUID.randomUUID().toString();  // JWT ID for tracking
        
        return Jwts.builder()
                .claims(claims)
                .subject(subject)
                .id(jti)  // Unique token identifier
                .issuedAt(now)
                .notBefore(now)  // Not valid before now
                .expiration(new Date(now.getTime() + expirationMillis))
                .signWith(key)
                .compact();
    }

    public Claims parseToken(String token) {
        return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
    }

    public String extractUsername(String token) {
        return parseToken(token).getSubject();
    }

    public String extractJti(String token) {
        return parseToken(token).getId();
    }

    public boolean isTokenValid(String token) {
        try {
            parseToken(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public java.util.Date getExpiration(String token) {
        return parseToken(token).getExpiration();
    }

    public long getExpirationMillis() {
        return expirationMillis;
    }
}

