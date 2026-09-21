package com.assetiq.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.net.URI;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/** CSRF protection for the cookie-authenticated browser channel. */
@Component
public class BrowserMutationOriginFilter extends OncePerRequestFilter {

    private static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD", "OPTIONS", "TRACE");
    private final Set<String> allowedOrigins;

    public BrowserMutationOriginFilter(@Value("${app.cors.allowed-origins:}") String configuredOrigins) {
        this.allowedOrigins = Arrays.stream(configuredOrigins.split(","))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .map(BrowserMutationOriginFilter::normalizeOrigin)
                .collect(Collectors.toUnmodifiableSet());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (SAFE_METHODS.contains(request.getMethod()) || !hasSessionCookie(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        String suppliedOrigin = request.getHeader(HttpHeaders.ORIGIN);
        if (suppliedOrigin == null || suppliedOrigin.isBlank()) {
            suppliedOrigin = originFromReferer(request.getHeader(HttpHeaders.REFERER));
        }
        if (suppliedOrigin == null || !allowedOrigins.contains(normalizeOrigin(suppliedOrigin))) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN,
                    "Cookie-authenticated mutations require an approved Origin");
            return;
        }
        filterChain.doFilter(request, response);
    }

    private static boolean hasSessionCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) return false;
        return Arrays.stream(cookies).anyMatch(cookie ->
                ("access_token".equals(cookie.getName()) || "refresh_token".equals(cookie.getName()))
                        && cookie.getValue() != null && !cookie.getValue().isBlank());
    }

    private static String originFromReferer(String referer) {
        if (referer == null || referer.isBlank()) return null;
        try {
            URI uri = URI.create(referer);
            if (uri.getScheme() == null || uri.getHost() == null) return null;
            return uri.getScheme() + "://" + uri.getAuthority();
        } catch (IllegalArgumentException invalid) {
            return null;
        }
    }

    private static String normalizeOrigin(String value) {
        try {
            URI uri = URI.create(value.trim());
            if (uri.getScheme() == null || uri.getHost() == null || uri.getUserInfo() != null) return "";
            String scheme = uri.getScheme().toLowerCase(java.util.Locale.ROOT);
            String host = uri.getHost().toLowerCase(java.util.Locale.ROOT);
            int port = uri.getPort();
            boolean defaultPort = port == -1 || ("https".equals(scheme) && port == 443)
                    || ("http".equals(scheme) && port == 80);
            return scheme + "://" + host + (defaultPort ? "" : ":" + port);
        } catch (IllegalArgumentException invalid) {
            return "";
        }
    }
}
