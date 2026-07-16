package com.assetiq.controllers.v1;

import com.assetiq.enums.SsoProvider;
import com.assetiq.models.OrgSsoConfig;
import com.assetiq.repositories.OrgSsoConfigRepository;
import com.assetiq.repositories.RoleRepository;
import com.assetiq.repositories.UserRepository;
import com.assetiq.security.JwtUtil;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class SsoControllerSecurityTest {

    private static final String CLIENT_ID = "assetiq-client";
    private static final String NONCE = "nonce-123";

    @Test
    void samlAcs_rejectsUnauthenticatedFormPostWithoutProvisioningUser() {
        OrgSsoConfigRepository ssoConfigRepository = mock(OrgSsoConfigRepository.class);
        UserRepository userRepository = mock(UserRepository.class);

        SsoController controller = controller(ssoConfigRepository, userRepository);
        MockHttpServletResponse response = new MockHttpServletResponse();

        var result = controller.samlAcs(UUID.randomUUID(), null, response);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verifyNoInteractions(ssoConfigRepository);
        verifyNoInteractions(userRepository);
    }

    @Test
    void oidcValidation_acceptsValidSignedToken() throws Exception {
        try (OidcFixture oidc = new OidcFixture()) {
            Map<String, Object> claims = validate(oidc.config(), oidc.token(CLIENT_ID, oidc.issuer(), NONCE, 300));

            assertThat(claims.get("email")).isEqualTo("user@example.com");
            assertThat(claims.get("nonce")).isEqualTo(NONCE);
        }
    }

    @Test
    void oidcValidation_rejectsInvalidSignature() throws Exception {
        try (OidcFixture oidc = new OidcFixture()) {
            RSAKey otherKey = new RSAKeyGenerator(2048).keyID("other").generate();
            String token = oidc.token(otherKey, CLIENT_ID, oidc.issuer(), NONCE, 300);

            assertThatThrownBy(() -> validate(oidc.config(), token))
                    .hasMessageContaining("ID token validation failed");
        }
    }

    @Test
    void oidcValidation_rejectsWrongIssuer() throws Exception {
        try (OidcFixture oidc = new OidcFixture()) {
            String token = oidc.token(CLIENT_ID, "https://issuer.invalid", NONCE, 300);

            assertThatThrownBy(() -> validate(oidc.config(), token))
                    .hasMessageContaining("ID token validation failed");
        }
    }

    @Test
    void oidcValidation_rejectsWrongAudience() throws Exception {
        try (OidcFixture oidc = new OidcFixture()) {
            String token = oidc.token("other-client", oidc.issuer(), NONCE, 300);

            assertThatThrownBy(() -> validate(oidc.config(), token))
                    .hasMessageContaining("ID token validation failed");
        }
    }

    @Test
    void oidcValidation_rejectsExpiredToken() throws Exception {
        try (OidcFixture oidc = new OidcFixture()) {
            String token = oidc.token(CLIENT_ID, oidc.issuer(), NONCE, -60);

            assertThatThrownBy(() -> validate(oidc.config(), token))
                    .hasMessageContaining("ID token validation failed");
        }
    }

    private static Map<String, Object> validate(OrgSsoConfig config, String token) throws Exception {
        SsoController controller = controller(mock(OrgSsoConfigRepository.class), mock(UserRepository.class));
        Method method = SsoController.class.getDeclaredMethod(
                "validateIdTokenClaims", OrgSsoConfig.class, String.class, String.class);
        method.setAccessible(true);
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> claims = (Map<String, Object>) method.invoke(controller, config, token, NONCE);
            return claims;
        } catch (InvocationTargetException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof Exception exception) {
                throw exception;
            }
            throw ex;
        }
    }

    private static SsoController controller(OrgSsoConfigRepository ssoConfigRepository, UserRepository userRepository) {
        @SuppressWarnings("unchecked")
        ObjectProvider<StringRedisTemplate> redisProvider = mock(ObjectProvider.class);
        when(redisProvider.getIfAvailable()).thenReturn(null);
        return new SsoController(
                ssoConfigRepository,
                userRepository,
                mock(RoleRepository.class),
                mock(JwtUtil.class),
                mock(PasswordEncoder.class),
                redisProvider);
    }

    private static final class OidcFixture implements AutoCloseable {
        private final RSAKey key;
        private final HttpServer server;
        private final String issuer;

        private OidcFixture() throws Exception {
            this.key = new RSAKeyGenerator(2048).keyID("test-key").generate();
            this.server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
            this.issuer = "http://127.0.0.1:" + server.getAddress().getPort();

            server.createContext("/.well-known/openid-configuration", exchange -> {
                byte[] body = ("{\"issuer\":\"" + issuer + "\",\"jwks_uri\":\"" + issuer + "/jwks\"}")
                        .getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, body.length);
                exchange.getResponseBody().write(body);
                exchange.close();
            });
            server.createContext("/jwks", exchange -> {
                byte[] body = ("{\"keys\":[" + key.toPublicJWK().toJSONString() + "]}")
                        .getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, body.length);
                exchange.getResponseBody().write(body);
                exchange.close();
            });
            server.start();
        }

        private String issuer() {
            return issuer;
        }

        private OrgSsoConfig config() {
            OrgSsoConfig config = new OrgSsoConfig();
            config.setProvider(SsoProvider.GOOGLE);
            config.setEnabled(true);
            config.setClientId(CLIENT_ID);
            config.setIssuerUri(issuer);
            return config;
        }

        private String token(String audience, String issuer, String nonce, long expiresInSeconds) throws Exception {
            return token(key, audience, issuer, nonce, expiresInSeconds);
        }

        private String token(RSAKey signingKey, String audience, String issuer, String nonce, long expiresInSeconds)
                throws Exception {
            Instant now = Instant.now();
            JWTClaimsSet claims = new JWTClaimsSet.Builder()
                    .issuer(issuer)
                    .subject("user-123")
                    .audience(audience)
                    .issueTime(Date.from(now))
                    .expirationTime(Date.from(now.plusSeconds(expiresInSeconds)))
                    .claim("nonce", nonce)
                    .claim("email", "user@example.com")
                    .build();
            SignedJWT jwt = new SignedJWT(
                    new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(signingKey.getKeyID()).build(),
                    claims);
            jwt.sign(new RSASSASigner(signingKey.toPrivateKey()));
            return jwt.serialize();
        }

        @Override
        public void close() {
            server.stop(0);
        }
    }
}
